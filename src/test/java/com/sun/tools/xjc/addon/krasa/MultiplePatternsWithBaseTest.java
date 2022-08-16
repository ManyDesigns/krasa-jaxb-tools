package com.sun.tools.xjc.addon.krasa;

import java.util.List;
import java.util.stream.Collectors;

public class MultiplePatternsWithBaseTest extends RunXJC2MojoTestHelper {

    @Override
    public String getFolderName() {
        return "multiplePatternsWithBase";
    }

    @Override
    public String getNamespace() {
        return "a";
    }

    //I don't support anymore multiplepatttern withBase it is incompatible wtih uniontype
    public void test() {
        element("MultiPatternWithBase")
                .attribute("multiplePatternsWithBase")
                .annotation("Pattern")
                .assertParam("regexp", "([0-9])|([A-B])");
    }

}
