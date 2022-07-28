package com.sun.tools.xjc.addon.krasa;

public class UnionTest extends RunXJC2MojoTestHelper {

    @Override
    public String getFolderName() {
        return "uniontype";
    }

    public void testCalendar() {
        element( "CalendarTimePeriodWithBitmask" )
                .attribute( "fromDate" ).assertNoAnnotationsPresent();
    }


}
