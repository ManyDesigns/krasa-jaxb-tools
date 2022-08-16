package com.sun.tools.xjc.addon.krasa;

import junit.framework.Assert;

import javax.validation.constraints.Pattern;
import java.util.List;
import java.util.stream.Collectors;

public class UnionTest extends RunXJC2MojoTestHelper {

    @Override
    public String getFolderName() {
        return "uniontype";
    }

    public void testId() {

        AnnotationTester annotation = element("TIDValidator").classAnnotations().annotation( "Pattern" );

        annotation.assertParam( "regexp", "((urn:uuid:)?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|\\\\\\\\{[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\\\\\\\})|([[_:A-Za-z]-[:]][[-._:A-Za-z0-9]-[:]]*)" );


    }
}
