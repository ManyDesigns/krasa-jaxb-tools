package com.sun.tools.xjc.addon.krasa;

import com.sun.codemodel.*;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.*;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.xml.xsom.*;
import com.sun.xml.xsom.impl.*;
import com.sun.xml.xsom.impl.parser.DelayedRef;
import cz.jirutka.validator.collection.constraints.EachDecimalMax;
import cz.jirutka.validator.collection.constraints.EachDecimalMin;
import cz.jirutka.validator.collection.constraints.EachDigits;
import cz.jirutka.validator.collection.constraints.EachSize;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.persistence.Column;
import javax.validation.Valid;
import javax.validation.constraints.*;

import org.apache.commons.lang.StringUtils;
import org.apache.cxf.tools.common.model.JAnnotation;
import org.xml.sax.ErrorHandler;

/**
 * NOTE: fractionDigits fixed attribute cannot be translated into a meaningful Validation.
 *
 * @author Giampiero Granatella
 * @author Francesco Illuminati
 * @author Vojtěch Krása
 * @author cocorossello
 */
public class JaxbValidationPlugin extends Plugin {

    private static final String PLUGIN_OPTION_NAME = "XJsr303Annotations";
    private static final String TARGET_NAMESPACE_PARAMETER =
            PLUGIN_OPTION_NAME + ":targetNamespace";
    private static final String JSR_349 = PLUGIN_OPTION_NAME + ":JSR_349";
    private static final String GENERATE_NOT_NULL_ANNOTATIONS =
            PLUGIN_OPTION_NAME + ":generateNotNullAnnotations";
    private static final String NOT_NULL_ANNOTATIONS_CUSTOM_MESSAGES =
            PLUGIN_OPTION_NAME + ":notNullAnnotationsCustomMessages";
    private static final String VERBOSE = PLUGIN_OPTION_NAME + ":verbose";
    private static final String GENERATE_JPA_ANNOTATIONS =
            PLUGIN_OPTION_NAME + ":jpa";
    static final String GENERATE_SERVICE_VALIDATION_ANNOTATIONS =
            PLUGIN_OPTION_NAME + ":generateServiceValidationAnnotations";
    static final String GENERATE_STRING_LIST_ANNOTATIONS =
            PLUGIN_OPTION_NAME + ":generateStringListAnnotations";
    private static final String NAMESPACE =
            "http://jaxb.dev.java.net/plugin/code-injector";
    public static final String VALIDATOR = "Validator";

    private String targetNamespace = null;
    private boolean jsr349 = false;
    private boolean verbose = true;
    private boolean notNullAnnotations = true;
    private boolean notNullCustomMessages;
    private boolean notNullPrefixFieldName;
    private boolean notNullPrefixClassName;
    private String notNullCustomMessage = null;
    private boolean jpaAnnotations = false;
    private String serviceValidationAnnotations;
    private boolean generateStringListAnnotations;

    @Override
    public String getOptionName() {
        return PLUGIN_OPTION_NAME;
    }

    @Override
    public int parseArgument(Options opt, String[] args, int i)
            throws BadCommandLineException, IOException {

        ArgumentParser argParser = new ArgumentParser(args[i]);

        argParser.extractString(TARGET_NAMESPACE_PARAMETER)
                .ifPresent(v -> targetNamespace = v);

        argParser.extractBoolean(JSR_349)
                .ifPresent(v -> jsr349 = v);

        argParser.extractBoolean(GENERATE_NOT_NULL_ANNOTATIONS)
                .ifPresent(v -> notNullAnnotations = v);

        argParser.extractBoolean(VERBOSE)
                .ifPresent(v -> verbose = v);

        argParser.extractBoolean(GENERATE_JPA_ANNOTATIONS)
                .ifPresent(v -> jpaAnnotations = v);

        argParser.extractString(GENERATE_SERVICE_VALIDATION_ANNOTATIONS)
                .ifPresent(v -> serviceValidationAnnotations = v);

        argParser.extractBoolean(GENERATE_STRING_LIST_ANNOTATIONS)
                .ifPresent(v -> generateStringListAnnotations = v);

        argParser.extractString(NOT_NULL_ANNOTATIONS_CUSTOM_MESSAGES)
                .ifPresent(value -> {
                    notNullCustomMessages = Boolean.parseBoolean(value);

                    if (!notNullCustomMessages) {
                        if (value.equalsIgnoreCase("classname")) {
                            notNullCustomMessages = true;
                            notNullPrefixFieldName = true;
                            notNullPrefixClassName = true;
                        } else if (value.equalsIgnoreCase("fieldname")) {
                            notNullCustomMessages = true;
                            notNullPrefixFieldName = true;
                        } else if (value.length() != 0 &&
                                !value.equalsIgnoreCase("false")) {
                            notNullCustomMessage = value;
                        }
                    }
                });

        return argParser.getCounter();
    }

    @Override
    public List<String> getCustomizationURIs() {
        return Collections.singletonList(NAMESPACE);
    }

    @Override
    public boolean isCustomizationTagName(String nsUri, String localName) {
        return nsUri.equals(NAMESPACE) &&
                localName.equals("code");
    }

    @Override
    public void onActivated(Options opts) throws BadCommandLineException {
        super.onActivated(opts);
    }

    @Override
    public String getUsage() {
        return "  -" + PLUGIN_OPTION_NAME + "      :  " +
                "inject Bean validation annotations (JSR 303); " +
                "-" + PLUGIN_OPTION_NAME +
                ":targetNamespace=http://www.foo.com/bar  :      " +
                "additional settings for @Valid annotation";
    }

    @Override
    public boolean run(Outline model, Options opt, ErrorHandler errorHandler) {
        for (ClassOutline co : model.getClasses()) {
            List<CPropertyInfo> properties = co.target.getProperties();

            for (CPropertyInfo property : properties) {
                if (property instanceof CElementPropertyInfo) {
                    processElement((CElementPropertyInfo) property, co, model);

                } else if (property instanceof CAttributePropertyInfo) {
                    processAttribute((CAttributePropertyInfo) property, co, model);

                } else if (property instanceof CValuePropertyInfo) {
                    processAttribute((CValuePropertyInfo) property, co, model);

                } else if (property instanceof CReferencePropertyInfo) {
                    //nothing to do at the moment
                }
            }
        }
        return true;
    }

    /**
     * XS:Element
     *
     * @param property
     */
    public void processElement(CElementPropertyInfo property,
                               ClassOutline classOutline, Outline model) {

        XSComponent schemaComponent = property.getSchemaComponent();
        ParticleImpl particle = (ParticleImpl) schemaComponent;

        int minOccurs = particle.getMinOccurs().intValue();
        int maxOccurs = particle.getMaxOccurs().intValue();
        boolean nillable = ((ElementDecl) particle.getTerm()).isNillable();
        boolean required = property.isRequired();
        String propertyName = propertyName(property);

        JFieldVar field = classOutline.implClass.fields().get(propertyName);

        if (notNullAnnotations &&
                !(minOccurs == 0 || !required || nillable) &&
                !hasAnnotation(field, NotNull.class)) {

            addNotNullAnnotation(classOutline, field);
        }

        // https://www.ibm.com/developerworks/webservices/library/ws-tip-null/index.html
        // http://www.dimuthu.org/blog/2008/08/18/xml-schema-nillabletrue-vs-minoccurs0/comment-page-1/
        if (property.isCollection() &&
                !hasAnnotation(field, Size.class) &&
                (maxOccurs != 0 || minOccurs != 0)) {

            if (property.isCollectionRequired()) {
                addNotNullAnnotation(classOutline, field);
            }

            addSizeAnnotation(minOccurs, maxOccurs, null,
                    propertyName, classOutline.implClass.name(), field);
        }

        XSTerm term = particle.getTerm();
        if (term instanceof ElementDecl) {
            processElement(property, classOutline, field, (ElementDecl) term);

        } else if (term instanceof DelayedRef.Element) {

            XSElementDecl xsElementDecl = ((DelayedRef.Element) term).get();
            processElement(property, classOutline, field, (ElementDecl) xsElementDecl);
        }

    }

    private void processElement(CElementPropertyInfo property,
                                ClassOutline clase, JFieldVar field, ElementDecl element) {
        String propertyName = propertyName(property);
        String className = clase.implClass.name();
        XSType elementType = element.getType();

        addValidAnnotation(elementType, field, propertyName, className);

        // using https://github.com/jirutka/validator-collection to annotate Lists of primitives
        final XSSimpleType simpleType = elementType.asSimpleType();
        if (generateStringListAnnotations && property.isCollection() && simpleType != null) {
            addEachSizeAnnotation(simpleType, field);
            addEachDigitsAnnotation(simpleType, field);
            addEachDecimalMinAnnotation(simpleType, field);
            addEachDecimalMaxAnnotation(simpleType, field);
        }

        if (elementType instanceof XSSimpleType) {
            processType((XSSimpleType) elementType, field, propertyName, className);

        } else if (elementType.getBaseType() instanceof XSSimpleType) {
            final XSSimpleType baseType = (XSSimpleType) elementType.getBaseType();
            processType(baseType, field, propertyName, className);
        }
    }

    public void processType(XSSimpleType simpleType, JFieldVar field,
                            String propertyName, String className) {
        processType(simpleType, field,
                propertyName, className, field);
    }

    public void processType(XSSimpleType simpleType, JFieldVar field,
                            String propertyName, String className, JAnnotatable target) {

        if (!hasAnnotation(field, Size.class) &&
                isSizeAnnotationApplicable(field)) {
            addSizeAnnotation(simpleType, propertyName, className, field, target);
        }

        if (jpaAnnotations && isSizeAnnotationApplicable(field)) {
            addJpaColumnAnnotation(simpleType, propertyName, className, field, target);
        }

        if (Utils.isNumber(field)) {

            if (!hasAnnotation(field, DecimalMin.class)) {

                XSFacet minInclusive = simpleType.getFacet("minInclusive");
                if (isValidValue(minInclusive)) {
                    addDecimalMinAnnotation(field, minInclusive, propertyName, className,
                            false, target);
                }

                XSFacet minExclusive = simpleType.getFacet("minExclusive");
                if (isValidValue(minExclusive)) {
                    addDecimalMinAnnotation(field, minExclusive, propertyName, className,
                            true, target);
                }
            }

            if (!hasAnnotation(field, DecimalMax.class)) {

                XSFacet maxInclusive = simpleType.getFacet("maxInclusive");
                if (isValidValue(maxInclusive)) {
                    addDecimalMaxAnnotation(field, maxInclusive, propertyName, className,
                            false, target);
                }

                XSFacet maxExclusive = simpleType.getFacet("maxExclusive");
                if (isValidValue(maxExclusive)) {
                    addDecimalMaxAnnotation(field, maxExclusive, propertyName, className,
                            true, target);
                }
            }

            if (simpleType.getFacet("totalDigits") != null) {
                addDigitAndJpaColumnAnnotation(simpleType, field, propertyName, className, target);
            }
        }

        if ("String".equals(field.type().name())) {

            XSFacet patternFacet = simpleType.getFacet("pattern");
            final List<XSFacet> patternList = simpleType.getFacets("pattern");
            if (patternList.size() > 1) { // More than one pattern
                addPatternListAnnotation(simpleType, propertyName, className, field, patternList, target);
            } else if (patternFacet != null) {
                String pattern = patternFacet.getValue().value;
                addSinglePatternAnnotation(simpleType, propertyName, className, field, pattern, target);
            } else {
                addPatternEmptyAnnotation(simpleType, propertyName, className, field, target);
            }
        }
    }

    private void addEachSizeAnnotation(final XSSimpleType simpleType, JFieldVar field) throws
            NumberFormatException {
        addEachSizeAnnotation(simpleType, field, field);
    }

    private void addEachSizeAnnotation(final XSSimpleType simpleType, JFieldVar field, JAnnotatable annotatable) throws
            NumberFormatException {
        String minLength = getStringFacet(simpleType, "minLength");
        String maxLength = getStringFacet(simpleType, "maxLength");
        if (minLength != null || maxLength != null) {
            JAnnotationUse annotation = annotatable.annotate(EachSize.class);
            if (minLength != null) {
                annotation.param("min", Integer.parseInt(minLength));
            }
            if (maxLength != null) {
                annotation.param("max", Integer.parseInt(maxLength));
            }
        }
    }

    private void addEachDigitsAnnotation(final XSSimpleType simpleType, JAnnotatable annotatable) throws
            NumberFormatException {
        String totalDigits = getStringFacet(simpleType, "totalDigits");
        String fractionDigits = getStringFacet(simpleType, "fractionDigits");
        if (totalDigits != null || fractionDigits != null) {
            JAnnotationUse annotation = annotatable.annotate(EachDigits.class);
            if (totalDigits != null || fractionDigits != null) {
                if (totalDigits != null) {
                    annotation.param("integer", Integer.parseInt(totalDigits));
                } else {
                    annotation.param("integer", 0);
                }
                if (fractionDigits != null) {
                    annotation.param("fraction", Integer.parseInt(fractionDigits));
                } else {
                    annotation.param("fraction", 0);
                }
            }
        }
    }

    private void addEachDecimalMaxAnnotation(final XSSimpleType simpleType, JAnnotatable annotatable) throws
            NumberFormatException {
        String maxInclusive = getStringFacet(simpleType, "maxInclusive");
        String maxExclusive = getStringFacet(simpleType, "maxExclusive");
        if (maxExclusive != null || maxInclusive != null) {
            JAnnotationUse annotation = annotatable.annotate(EachDecimalMax.class);
            if (maxInclusive != null) {
                annotation.param("value", maxInclusive)
                        .param("inclusive", true);
            }
            if (maxExclusive != null) {
                annotation.param("value", maxExclusive)
                        .param("inclusive", false);
            }
        }
    }

    private void addEachDecimalMinAnnotation(final XSSimpleType simpleType, JAnnotatable annotatable) throws
            NumberFormatException {
        String minInclusive = getStringFacet(simpleType, "minInclusive");
        String minExclusive = getStringFacet(simpleType, "minExclusive");
        if (minExclusive != null || minInclusive != null) {
            JAnnotationUse annotation = annotatable.annotate(EachDecimalMin.class);
            if (minInclusive != null) {
                annotation.param("value", minInclusive)
                        .param("inclusive", true);
            }
            if (minExclusive != null) {
                annotation.param("value", minExclusive)
                        .param("inclusive", false);
            }
        }
    }

    private void addNotNullAnnotation(ClassOutline co, JFieldVar field) {
        addNotNullAnnotation(co, field, field);
    }

    private void addNotNullAnnotation(ClassOutline co, JFieldVar field, JAnnotatable annotatable) {
        final String className = co.implClass.name();

        String message = null;
        if (notNullPrefixClassName) {
            message = String.format("%s.%s {%s.message}",
                    className, field.name(),
                    NotNull.class.getName());

        } else if (notNullPrefixFieldName) {
            message = String.format("%s {%s.message}",
                    field.name(), NotNull.class.getName());

        } else if (notNullCustomMessages) {
            message = String.format("{%s.message}",
                    NotNull.class.getName());

        } else if (notNullCustomMessage != null) {
            message = notNullCustomMessage
                    .replace("{ClassName}", className)
                    .replace("{FieldName}", field.name());

        }

        log("@NotNull: " + field.name() + " added to class " + className);

        final JAnnotationUse annotation = annotatable.annotate(NotNull.class);
        if (message != null) {
            annotation.param("message", message);
        }

    }


    private void addCustomValidatorAnnotation(JDefinedClass customValidator, JAnnotatable annotatable) {
        annotatable.annotate(customValidator);
    }

    private void addValidAnnotation(XSType elementType, JFieldVar field, String propertyName,
                                    String className) {
        addValidAnnotation(elementType, field, propertyName,
                className, field);
    }

    private void addValidAnnotation(XSType elementType, JFieldVar field, String propertyName,
                                    String className, JAnnotatable annotatable) {

        String elemNs = elementType.getTargetNamespace();

        if ((targetNamespace == null || elemNs.startsWith(targetNamespace)) &&
                (elementType.isComplexType() || Utils.isCustomType(field)) &&
                !hasAnnotation(field, Valid.class)) {

            log("@Valid: " + propertyName + " added to class " + className);
            annotatable.annotate(Valid.class);
        }
    }

    private void addSizeAnnotation(XSSimpleType simpleType, String propertyName, String className,
                                   JFieldVar field) {
        addSizeAnnotation(simpleType, propertyName, className,
                field, field);

    }

    private void addSizeAnnotation(XSSimpleType simpleType, String propertyName, String className,
                                   JFieldVar field, JAnnotatable annotatable) {

        Integer maxLength = getIntegerFacet(simpleType, "maxLength");
        Integer minLength = getIntegerFacet(simpleType, "minLength");
        Integer length = getIntegerFacet(simpleType, "length");

        addSizeAnnotation(minLength, maxLength, length, propertyName, className, annotatable);
    }

    private void addSizeAnnotation(Integer minLength, Integer maxLength, Integer length,
                                   String propertyName, String className, JAnnotatable annotatable) {

        if (isValidLength(minLength) || isValidLength(maxLength)) {
            log("@Size(" + minLength + "," + maxLength + "): " +
                    propertyName + " added to class " + className);

            final JAnnotationUse annotate = annotatable.annotate(Size.class);
            if (isValidLength(minLength)) {
                annotate.param("min", minLength);
            }
            if (isValidLength(maxLength)) {
                annotate.param("max", maxLength);
            }

        } else if (isValidLength(length)) {
            log("@Size(" + length + "," + length + "): " + propertyName +
                    " added to class " + className);

            annotatable.annotate(Size.class)
                    .param("min", length)
                    .param("max", length);
        }
    }

    private static boolean isValidLength(Integer length) {
        return length != null && length != -1;
    }

    private void addJpaColumnAnnotation(XSSimpleType simpleType, String propertyName,
                                        String className, JFieldVar field) {
        addJpaColumnAnnotation(simpleType, propertyName,
                className, field, field);
    }

    private void addJpaColumnAnnotation(XSSimpleType simpleType, String propertyName,
                                        String className, JFieldVar field, JAnnotatable annotatable) {
        Integer maxLength = getIntegerFacet(simpleType, "maxLength");
        if (maxLength != null) {
            log("@Column(null, " + maxLength + "): " + propertyName +
                    " added to class " + className);
            annotatable.annotate(Column.class).param("length", maxLength);
        }
    }

    private void addDigitAndJpaColumnAnnotation(XSSimpleType simpleType, JFieldVar field,
                                                String propertyName, String className) {
        addDigitAndJpaColumnAnnotation(simpleType, field, propertyName, className, field);
    }

    private void addDigitAndJpaColumnAnnotation(XSSimpleType simpleType, JFieldVar field,
                                                String propertyName, String className, JAnnotatable annotatable) {

        Integer totalDigits = getIntegerFacet(simpleType, "totalDigits");
        Integer fractionDigits = getIntegerFacet(simpleType, "fractionDigits");
        if (totalDigits == null) {
            totalDigits = 0;
        }
        if (fractionDigits == null) {
            fractionDigits = 0;
        }

        if (!hasAnnotation(field, Digits.class)) {
            log("@Digits(" + totalDigits + "," + fractionDigits + "): " + propertyName +
                    " added to class " + className);
            annotatable.annotate(Digits.class)
                    .param("integer", totalDigits)
                    .param("fraction", fractionDigits);
        }
        if (jpaAnnotations) {
            annotatable.annotate(Column.class)
                    .param("precision", totalDigits)
                    .param("scale", fractionDigits);
        }
    }

    private void addDecimalMinAnnotation(JFieldVar field, XSFacet minFacet,
                                         String propertyName, String className, boolean exclusive) {
        addDecimalMinAnnotation(field, minFacet, propertyName, className, exclusive, field);
    }

    private void addDecimalMinAnnotation(JFieldVar field, XSFacet minFacet,
                                         String propertyName, String className, boolean exclusive, JAnnotatable annotatable) {

        BigDecimal min = parseIntegerXsFacet(minFacet);
        if (min == null) {
            return;
        }

        JAnnotationUse annotate = annotatable.annotate(DecimalMin.class);

        if (jsr349) {
            log("@DecimalMin(value = " + min + ", inclusive = " + (!exclusive) + "): " +
                    propertyName + " added to class " + className);

            annotate.param("value", min.toString())
                    .param("inclusive", !exclusive);

        } else {
            if (exclusive) {
                min = min.add(BigDecimal.ONE);
            }

            log("@DecimalMin(" + min + "): " + propertyName + " added to class " + className);

            annotate.param("value", min.toString());
        }
    }

    //TODO minExclusive=0, fractionDigits=2 wrong annotation https://github.com/krasa/krasa-jaxb-tools/issues/38 
    private void addDecimalMaxAnnotation(JFieldVar field, XSFacet maxFacet,
                                         String propertyName, String className, boolean exclusive) {
        addDecimalMaxAnnotation(field, maxFacet, propertyName, className, exclusive, field);
    }

    private void addDecimalMaxAnnotation(JFieldVar field, XSFacet maxFacet,
                                         String propertyName, String className, boolean exclusive, JAnnotatable annotatable) {

        BigDecimal max = parseIntegerXsFacet(maxFacet);
        if (max == null) {
            return;
        }

        JAnnotationUse annotate = annotatable.annotate(DecimalMax.class);

        if (jsr349) {
            log("@DecimalMax(value = " + max + ", inclusive = " + (!exclusive) + "): " +
                    propertyName + " added to class " + className);

            annotate.param("value", max.toString())
                    .param("inclusive", (!exclusive));

        } else {
            if (exclusive) {
                max = max.subtract(BigDecimal.ONE);
            }

            log("@DecimalMax(" + max + "): " + propertyName + " added to class " + className);

            annotate.param("value", max.toString());
        }
    }

    private void addPatternEmptyAnnotation(XSSimpleType simpleType, String propertyName,
                                           String className, JFieldVar field) {
        addPatternEmptyAnnotation(simpleType, propertyName,
                className, field, field);
    }

    private void addPatternEmptyAnnotation(XSSimpleType simpleType, String propertyName,
                                           String className, JFieldVar field, JAnnotatable annotatable) {

        final List<XSFacet> enumerationList = simpleType.getFacets("enumeration");
        final XSFacet patternFacet = simpleType.getFacet("enumeration");

        if (enumerationList.size() > 1) { // More than one pattern

            log("@Pattern: " + propertyName + " added to class " + className);
            final JAnnotationUse annotation = annotatable.annotate(Pattern.class);
            annotateMultiplePattern(enumerationList, annotation, true);

        } else if (patternFacet != null) {
            final String pattern = patternFacet.getValue().value;
            annotateSinglePattern(pattern, propertyName, className, field, true);

        } else { // if pattern does not exist
            Collection<JAnnotationUse> annotations = annotatable.annotations();
            /*for (JAnnotationUse annotation : annotations) {
                JClass type = annotation.getAnnotationClass();
                if (type.name().equals("Pattern")) {*/
            String pattern = ConfigReader.getConfig().getString("pattern_match_all");
            if (hasAnnotation(annotatable, Pattern.class)) {

                JAnnotationUse annotationUse = getAnnotation(annotatable, Pattern.class);
                Writer str = new StringWriter();
                ((JAnnotationValue) annotationUse.getAnnotationMembers().get("regexp")).generate(new JFormatter(str));
                String prevValue = StringUtils.strip(str.toString(), "\"");
                String replace = prevValue;
                if (prevValue.contains("\\\\")) {
                    replace = prevValue.replace("\\\\", "\\");
                }
                JAnnotationUse regexp = annotationUse.param("regexp", "(" + replace.trim() + ")|(" + pattern + ")");
                log(regexp.toString());
            }
        }

            /*}
        }*/
    }

    private void addSinglePatternAnnotation(XSSimpleType simpleType, String propertyName,
                                            String className, JFieldVar field, String pattern) {
        addSinglePatternAnnotation(simpleType, propertyName,
                className, field, pattern, field);
    }

    private void addSinglePatternAnnotation(XSSimpleType simpleType, String propertyName,
                                            String className, JFieldVar field, String pattern, JAnnotatable annotatable) {

        annotateSinglePattern(pattern, propertyName, className, annotatable, false);
       /* if (simpleType.getBaseType() instanceof XSSimpleType &&
                ((XSSimpleType) simpleType.getBaseType()).getFacet( "pattern" ) != null) {

            final XSSimpleType baseType = (XSSimpleType) simpleType.getBaseType();

            log( "@Pattern.List: " + propertyName + " added to class " + className );

            JAnnotationUse patternListAnnotation = annotatable.annotate( Pattern.List.class );
            JAnnotationArrayMember listValue = patternListAnnotation.paramArray( "value" );
            final XSFacet facet = baseType.getFacet( "pattern" );
            String basePattern = facet.getValue().value;

            listValue.annotate( Pattern.class )
                    .param( "regexp", replaceRegexp( basePattern ) );

            annotateSinglePattern( basePattern, propertyName, className, listValue,
                    false );

        } else {

            annotateSinglePattern( pattern, propertyName, className, annotatable, false );
        } */
    }

    private void addPatternListAnnotation(XSSimpleType simpleType, String propertyName,
                                          String className, JFieldVar field, List<XSFacet> patternList, JAnnotatable annotatable) {

        log("@Pattern: " + propertyName + " added to class " + className);
        final JAnnotationUse patternAnnotation = annotatable.annotate(Pattern.class);
        annotateMultiplePattern(patternList, patternAnnotation, false);

    }

    private void annotateSinglePattern(String pattern, String propertyName, String className,
                                       JAnnotatable annotable, boolean literal) {
        // cxf-codegen fix
        if (!"\\c+".equals(pattern)) {
            log("@Pattern(" + pattern + "): " + propertyName + " added to class " + className);
            String replaceRegexp = replaceRegexp(pattern);

            if (literal) {
                replaceRegexp = escapeRegexp(replaceRegexp);
            }
            if (hasAnnotation(annotable, Pattern.class)) {
                JAnnotationUse annotationUse = getAnnotation(annotable, Pattern.class);
                JAnnotationValue regexp1 = annotationUse.getAnnotationMembers().get("regexp");
                final StringWriter str = new StringWriter();
                ((JAnnotationValue) annotationUse.getAnnotationMembers().get("regexp")).generate(new JFormatter(str));
                String prevValue = StringUtils.strip(str.toString(), "\"");
                String replace = prevValue;
                if (prevValue.contains("\\\\")) {
                    replace = prevValue.replace("\\\\", "\\");
                }
                JAnnotationUse regexp = annotationUse.param("regexp", "(" + replace.trim() + ")|(" + replaceRegexp + ")");
                log(regexp.toString());
            } else {
                JAnnotationUse regexp = annotable.annotate(Pattern.class).param("regexp", replaceRegexp);
                log(regexp.toString());
            }
        }
    }

    private void annotateMultiplePattern(
            final List<XSFacet> patternList,
            final JAnnotationUse patternAnnotation,
            final boolean literal) {

        StringBuilder sb = new StringBuilder();
        for (XSFacet xsFacet : patternList) {
            final String value = xsFacet.getValue().value;
            // cxf-codegen fix
            if (!"\\c+".equals(value)) {
                String regexp = replaceRegexp(value);
                if (literal) {
                    regexp = escapeRegexp(regexp);
                }
                sb.append("(").append(regexp).append(")|");
            }
        }
        patternAnnotation.param("regexp", sb.substring(0, sb.length() - 1));
    }

    private static String getStringFacet(final XSSimpleType simpleType, String param) {
        final XSFacet facet = simpleType.getFacet(param);
        return facet == null ? null : facet.getValue().value;
    }

    private Integer getIntegerFacet(XSSimpleType simpleType, String name) {
        final XSFacet facet = simpleType.getFacet(name);
        if (facet == null) {
            return null;
        }
        final String value = facet.getValue().value;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String replaceRegexp(String pattern) {
        return pattern
                .replace("\\i", "[_:A-Za-z]")
                .replace("\\c", "[-._:A-Za-z0-9]");
    }

    /*
     * \Q indicates begin of quoted regex text, \E indicates end of quoted regex text
     */
    private static String escapeRegexp(String pattern) {
        return java.util.regex.Pattern.quote(pattern);
    }

    private boolean isSizeAnnotationApplicable(JFieldVar field) {
        return field.type().name().equals("String") || field.type().isArray();
    }

    /**
     * Attribute from parent declaration
     */
    private void processAttribute(CValuePropertyInfo property,
                                  ClassOutline clase, Outline model) {
        FieldOutline field = model.getField(property);
        String propertyName = property.getName(false);
        String className = clase.implClass.name();

        log("Attribute " + propertyName + " added to class " + className);
        XSComponent definition = property.getSchemaComponent();
        SimpleTypeImpl particle = (SimpleTypeImpl) definition;
        XSSimpleType type = particle.asSimpleType();
        JFieldVar var = clase.implClass.fields().get(propertyName);

        addValidAnnotation(type, var, propertyName, className);

        //If it is a Union Type I have to add all the constraint and make them with an Or clause
        if (type instanceof UnionSimpleTypeImpl) {
            processUnionType(clase, model, propertyName, className, type, var);
        } else {
            processType(type, var, propertyName, className);
        }
    }

    public void processAttribute(CAttributePropertyInfo property,
                                 ClassOutline clase, Outline model) {
        FieldOutline field = model.getField(property);
        String propertyName = property.getName(false);
        String className = clase.implClass.name();

        log("Attribute " + propertyName + " added to class " + className);
        XSComponent definition = property.getSchemaComponent();
        AttributeUseImpl particle = (AttributeUseImpl) definition;
        XSSimpleType type = particle.getDecl().getType();

        JFieldVar var = clase.implClass.fields().get(propertyName);
        if (particle.isRequired()) {
            if (!hasAnnotation(var, NotNull.class)) {
                addNotNullAnnotation(clase, var);
            }
        }

        addValidAnnotation(type, var, propertyName, className);
        if (type instanceof UnionSimpleTypeImpl) {
            processUnionType(clase, model, propertyName, className, type, var);
        } else {
            processType(type, var, propertyName, className);
        }


    }

    private void processUnionType(ClassOutline clase, Outline model, String propertyName, String className, XSSimpleType type, JFieldVar var) {
        UnionSimpleTypeImpl unionType = ((UnionSimpleTypeImpl) type);

        try {
            final String validatorName = type.getName() + VALIDATOR;
            JDefinedClass customValidator = CodeModelUtil.createComposedConstraintAnnotation(model.getCodeModel(), clase._package()._package(),
                    StringUtils.capitalize(validatorName));
            addCustomValidatorAnnotation(customValidator, var);
            for (int i = 0; i < unionType.getMemberSize(); i++) {
                XSSimpleType subType = unionType.getMember(i);
                processType(subType, var, propertyName, className, customValidator);
            }

        } catch (JClassAlreadyExistsException e) {
            /*throw new RuntimeException( e );*/
        }

    }


    private BigDecimal parseIntegerXsFacet(XSFacet facet) {
        final String str = facet.getValue().value;
        if (str == null || StringUtils.isBlank(str) || StringUtils.trimToNull(str) == null) {
            return null;
        }

        try {
            return new BigDecimal(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isValidValue(XSFacet facet) {
        if (facet == null) {
            return false;
        }
        String value = facet.getValue().value;
        // cxf-codegen puts max and min as value when there is not anything defined in wsdl.
        return value != null && !Utils.isMax(value) && !Utils.isMin(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean hasAnnotation(JAnnotatable var, Class annotationClass) {
        List<JAnnotationUse> list =
                (List<JAnnotationUse>) Utils.getField("annotations", var);
        if (list != null) {
            for (JAnnotationUse annotationUse : list) {

                if (((Class) Utils.getField("clazz._class", annotationUse)) == null)
                    continue;
                if (((Class) Utils.getField("clazz._class", annotationUse)).
                        getCanonicalName().equals(
                                annotationClass.getCanonicalName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public JAnnotationUse getAnnotation(JAnnotatable var, Class annotationClass) {

        List<JAnnotationUse> list =
                (List<JAnnotationUse>) Utils.getField("annotations", var);
        if (list != null) {
            for (JAnnotationUse annotationUse : list) {

                if (((Class) Utils.getField("clazz._class", annotationUse)) == null)
                    continue;
                if (((Class) Utils.getField("clazz._class", annotationUse)).
                        getCanonicalName().equals(
                                annotationClass.getCanonicalName())) {
                    return annotationUse;
                }
            }
        }
        return null;
    }

    private String propertyName(CElementPropertyInfo property) {
        return property.getName(false);
    }

    private void log(String log) {
        if (verbose) {
            System.out.println(log);
        }
    }
}
