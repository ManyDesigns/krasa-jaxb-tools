package com.sun.tools.xjc.addon.krasa;

import junit.framework.Assert;

import javax.validation.constraints.Pattern;

public class UnionTest extends RunXJC2MojoTestHelper {

    @Override
    public String getFolderName() {
        return "uniontype";
    }

    public void testId() {
        Assert.assertNotNull("I must have a list of Pattern inside an Annotation Interface TIDValidator",element( "TIDValidator" ).classAnnotations()
                .annotation("Pattern.List"));
    }

    public void testEnum() {
        Assert.assertNotNull("I must have an enum annotaion on the attribute aString in a AComplexUnionType",element( "AComplexUnionType" )
                .attribute("aString")
                .annotation("Pattern")
                );
    }


}
