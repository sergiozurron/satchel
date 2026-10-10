package com.jejo.satchel.validator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Constraint(validatedBy = AssetIdValidator.class)
@Target({ ElementType.FIELD })
public @interface ValidAssetId {
	String message() default "Asset not supported";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
