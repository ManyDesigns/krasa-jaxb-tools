package com.sun.tools.xjc.addon.krasa;

import com.sun.codemodel.*;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

public class PrimitiveFixerPlugin extends Plugin {

    private static final String OPTION_NAME = "XReplacePrimitives";
    private static final Map<String, Class<?>> PRIMITIVE_MAPPINGS = new HashMap<>();
    static {
        PRIMITIVE_MAPPINGS.put( "int", Integer.class );
        PRIMITIVE_MAPPINGS.put( "long", Long.class );
        PRIMITIVE_MAPPINGS.put( "boolean", Boolean.class );
        PRIMITIVE_MAPPINGS.put( "double", Double.class );
        PRIMITIVE_MAPPINGS.put( "float", Float.class );
        PRIMITIVE_MAPPINGS.put( "byte", Byte.class );
        PRIMITIVE_MAPPINGS.put( "short", Short.class );
        
    }

    @Override
    public String getOptionName() {
        return OPTION_NAME;
    }

    @Override
    public String getUsage() {
        return "-" + OPTION_NAME + "    :   " +
                "Replaces primitive types of fields and methods by proper " +
                "Class, WARNING: must be defined before XhashCode or Xequals.\n";
    }

    @Override
    public boolean run(Outline outline, Options opt, ErrorHandler errorHandler)
            throws SAXException {

        for (ClassOutline co : outline.getClasses()) {

            Map<String, JFieldVar> fields = co.implClass.fields();

            for (Map.Entry<String, JFieldVar> stringJFieldVarEntry : 
                    fields.entrySet()) {
                
                JFieldVar fieldVar = stringJFieldVarEntry.getValue();
                JType type = fieldVar.type();

                // Exclude "serialVersionUID" from processing XReplacePrimitives 
                // as this will have no getter or setter defined.
                if ("serialVersionUID".equals(fieldVar.name())) {
                    continue;
                }

                if (type.isPrimitive()) {
                    Class<?> o = PRIMITIVE_MAPPINGS.get(type.name());
                    if (o != null) {
                        JCodeModel jCodeModel = new JCodeModel();
                        JClass newType = jCodeModel.ref(o);
                        fieldVar.type(newType);
                        setReturnType(newType, 
                                getMethodsMap(MethodType.GETTER,
                                fieldVar, co));
                        setParameter(newType, 
                                getMethodsMap(MethodType.SETTER,
                                fieldVar, co));
                    }
                }
            }
        }
        return true;
    }

    enum MethodType {
        GETTER, SETTER
    }

    private void setParameter(JClass newType, JMethod jMethod) {
        if (jMethod != null) {
            JVar jVar = jMethod.listParams()[0];
            jVar.type(newType);
        }
    }

    private void setReturnType(JType type, JMethod jMethod) {
        if (jMethod != null) {
            jMethod.type(type);
        }
    }

    /**
     * I hate this shit
     */
    private JMethod getMethodsMap(MethodType type, JFieldVar field,
            ClassOutline co) {
        String getterBody = "return " + field.name() + ";";
        for (JMethod method : co.implClass.methods()) {
            String name = method.name();
            if (method.type().isPrimitive()) {
                if (MethodType.GETTER == type && (name.startsWith("is") || name.
                        startsWith("get"))) {
                    JStatement o = (JStatement) method.body().getContents().get(
                            0);
                    String s = getterBody(o);
                    if (s.trim().equals(getterBody)) {
                        return method;
                    }
                } else if (MethodType.SETTER == type && name.startsWith("set")) {
                    JStatement o = (JStatement) method.body().getContents().get(
                            0);
                    String s = setterBody(o);
                    if (s.startsWith("this." + field.name() + " =")) {
                        return method;
                    }
                }
            }

        }
        throw new RuntimeException("Failed to find " + type + " for " + field.
                name() + ", disable XReplacePrimitives and report a bug");
    }

    public static String getterBody(JStatement jStatement) {
        StringWriter w = new StringWriter();
        jStatement.state(new JFormatter(w));
        return w.toString();
    }

    public static String setterBody(JStatement jStatement) {
        StringWriter w = new StringWriter();
        jStatement.state(new JFormatter(w));
        return w.toString();
    }
}
