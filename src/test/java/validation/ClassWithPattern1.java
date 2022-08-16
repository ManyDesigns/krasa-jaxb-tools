package validation;


import javax.validation.constraints.Pattern;

public class ClassWithPattern1 {

    /*@Pattern.List({
        @Pattern(regexp = "[Y-Z]"),
        @Pattern(regexp = "([0-9])|([A-Z])")
    })*/
    @Pattern( regexp = "([Y-Z])|([0-9])")
    protected String multiplePatternsWithBase;

    /**
     * Gets the value of the multiplePatternsWithBase property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMultiplePatternsWithBase() {
        return multiplePatternsWithBase;
    }

    /**
     * Sets the value of the multiplePatternsWithBase property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMultiplePatternsWithBase(String value) {
        this.multiplePatternsWithBase = value;
    }

}
