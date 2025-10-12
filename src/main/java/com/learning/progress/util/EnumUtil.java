package com.learning.progress.util;

import java.util.Set;

public class EnumUtil {
    public static <E extends Enum<E>> boolean isValidEnum(Class<E> enumClass, String value) {
        if (value == null) return false;
        try {
            Enum.valueOf(enumClass, value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    public static <E extends Enum<E>> boolean isAllowedEnumValue(Class<E> enumClass, String value, Set<E> allowedValues) {
        if (value == null) return false;
        try {
            E enumValue = Enum.valueOf(enumClass, value);
            return allowedValues.contains(enumValue);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
