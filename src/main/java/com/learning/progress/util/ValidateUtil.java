package com.learning.progress.util;

import java.util.regex.Pattern;

public class ValidateUtil {
    private ValidateUtil() {
    }

    public static boolean regexValidation(String input, String stringPattern) {
        Pattern pattern = Pattern.compile(stringPattern);
        return pattern.matcher(input).matches();
    }
}
