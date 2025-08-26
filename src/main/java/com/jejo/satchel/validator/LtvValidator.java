package com.jejo.satchel.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;
import java.util.HashSet;

public class LtvValidator implements ConstraintValidator<ValidLtv, Integer> {
    private static final Set<Integer> ALLOWED_LTVS = new HashSet<>(Set.of(50, 60, 70));

    @Override
    public boolean isValid(Integer ltv, ConstraintValidatorContext context) {
        if (ltv == null) {
            return false;
        }
        return ALLOWED_LTVS.contains(ltv);
    }
}
