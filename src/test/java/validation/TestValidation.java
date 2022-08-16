package validation;

import org.junit.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;

/**
 * Author: Giampiero Granatella
 * Date: 16/08/22
 * Time: 10:22
 */
public class TestValidation {
    @Test
    public void validatePattern(){
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        ClassWithPattern1 obj = new ClassWithPattern1();
        obj.setMultiplePatternsWithBase( "Y" );
        Set<ConstraintViolation<ClassWithPattern1>> violations = validator.validate(obj);
        for (ConstraintViolation<ClassWithPattern1> violation : violations) {
            System.out.println(violation.getMessage());
        }
    }
}
