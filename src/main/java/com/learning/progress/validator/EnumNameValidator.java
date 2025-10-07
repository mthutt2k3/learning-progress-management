package com.learning.progress.validator;

import com.learning.progress.annotation.EnumName;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EnumNameValidator implements ConstraintValidator<EnumName, List<String>> {
    private List<String> acceptedValues;

    @Override
    public void initialize(EnumName constraintAnnotation) {
        acceptedValues = Arrays.stream(constraintAnnotation.enumClass().getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isValid(List<String> values, ConstraintValidatorContext context) {
        if (values == null) {
            return true; // Cho phép null nếu không yêu cầu @NotNull
        }
        return values.stream().allMatch(value -> acceptedValues.contains(value));
    }
}