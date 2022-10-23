package com.sun.tools.xjc.addon.krasa;

public class UnionTest extends RunXJC2MojoTestHelper {

    @Override
    public String getFolderName() {
        return "uniontype";
    }

    public void testId() {

        AnnotationTester annotation = element("TIDValidator").classAnnotations().annotation("Pattern");

        annotation.assertParam("regexp", "((urn:uuid:)?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|\\{[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\})");
    }
}
