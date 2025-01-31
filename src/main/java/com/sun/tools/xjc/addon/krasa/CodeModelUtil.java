package com.sun.tools.xjc.addon.krasa;


import com.sun.codemodel.*;
import org.hibernate.validator.constraints.CompositionType;
import org.hibernate.validator.constraints.ConstraintComposition;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

/**
 * Author: Giampiero Granatella
 * Date: 02/08/22
 * Time: 11:20
 */
public class CodeModelUtil {
    public static JDefinedClass createComposedConstraintAnnotation(JCodeModel model, JPackage jPackage, String name) throws JClassAlreadyExistsException {
        JDefinedClass clazz = jPackage._annotationTypeDeclaration(name);


        //clazz.annotate(model.ref( Target.class)).param("value", model.ref( "{ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER} "));
        clazz.annotate(model.ref(Target.class)).paramArray("value")
                .param(model.ref(ElementType.class).staticRef("METHOD"))
                .param(model.ref(ElementType.class).staticRef("FIELD"))
                .param(model.ref(ElementType.class).staticRef("ANNOTATION_TYPE"))
                .param(model.ref(ElementType.class).staticRef("CONSTRUCTOR"))
                .param(model.ref(ElementType.class).staticRef("PARAMETER"));

        clazz.annotate(model.ref(Documented.class));
        clazz.annotate(model.ref(Retention.class)).param("value", model.ref(RetentionPolicy.class).staticRef("RUNTIME"));
        clazz.annotate(model.ref(Constraint.class)).paramArray("validatedBy");
        clazz.annotate(model.ref(ConstraintComposition.class)).param("value", model.ref(CompositionType.class).staticRef("OR"));
        JMethod groups = clazz.method(JMod.PUBLIC, model
                .ref(Class.class).narrow(
                        model.ref(Object.class).wildcard()).array(), "groups");
        JMethod payload = clazz.method(JMod.PUBLIC, model
                .ref(Class.class).narrow(
                        model.ref(Payload.class).wildcard()).array(), "payload");

        groups.declareDefaultValue(new JExpressionImpl() {
            @Override
            public void generate(JFormatter f) {
                f.p("{}");
            }
        });

        payload.declareDefaultValue(new JExpressionImpl() {
            @Override
            public void generate(JFormatter f) {
                f.p("{}");
            }
        });
        
        JMethod message = clazz.method(JMod.PUBLIC,
                String.class, "message");
        message.declareDefaultValue(new JExpressionImpl() {
            @Override
            public void generate(JFormatter f) {
                f.p("\"tid pattern violated\"");
            }
        });


        return clazz;

        /*
        @Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER })
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = {})
public @interface ValidAlphanumeric {

    String message() default "field should have a valid length and contain numeric character(s).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
         */
    }
}
