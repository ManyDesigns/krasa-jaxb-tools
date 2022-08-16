package com.sun.tools.xjc.addon.krasa;

public class MultiplePatternTest extends RunXJC2MojoTestHelper {

    @Override
    public String getFolderName() {
        return "multiplePatterns";
    }

    @Override
    public String getNamespace() {
        return "a";
    }
    
    public void test() {
        element("Multipattern")
                .attribute("multiplePatterns")
                        .annotation("Pattern")
                                .assertParam("regexp", "([0-9])|([A-B])");
    }

}
