package com.jejo.satchel.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = LtvValidator.class)
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidLtv {
    String message() default "LTV must be one of: 50, 60, 70";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
