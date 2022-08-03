package com.sun.tools.xjc.addon.krasa;

import javax.validation.constraints.Pattern;

public class UnionTest extends RunXJC2MojoTestHelper {

    @Override
    public String getFolderName() {
        return "uniontype";
    }

    public void testId() {
        element( "AComplexType" )
                .attribute( "id" ).annotation("Pattern")
                .assertParam("regexp", "(urn:uuid:)?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|\\\\{[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\\\}")
                .end();
    }


}
