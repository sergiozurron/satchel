package com.jejo.satchel.validator;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AssetIdValidator implements ConstraintValidator<ValidAssetId, String> {

	@Value("${satchel.financial.assets.supported}")
	private Set<String> supportedAssets;

	@Override
	public boolean isValid(String assetId, ConstraintValidatorContext context) {
		return supportedAssets.contains(assetId);
	}

}
