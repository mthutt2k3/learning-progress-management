package com.learning.progress.util;

import com.learning.progress.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.regex.Pattern;

public class ValidateUtil {
    private ValidateUtil() {
    }

    public static boolean regexValidation(String input, String stringPattern) {
        Pattern pattern = Pattern.compile(stringPattern);
        return pattern.matcher(input).matches();
    }
    public static void validateSortParams(List<String> validSortFields, String sortBy, String sortDir) {
        if (!validSortFields.contains(sortBy)) {
            throw new ApiException("Invalid sortBy: " + sortBy, HttpStatus.BAD_REQUEST.value());
        }
        if (!List.of("asc", "desc").contains(sortDir.toLowerCase())) {
            throw new ApiException("Invalid sortDir: must be asc or desc", HttpStatus.BAD_REQUEST.value());
        }
    }

    public static void validatePaginationParams(int page, int size) {
        if (page < 0) {
            throw new ApiException("Page must be >= 0", HttpStatus.BAD_REQUEST.value());
        }
        if (size <= 0) {
            throw new ApiException("Size must be > 0", HttpStatus.BAD_REQUEST.value());
        }
        if (size > 100) {
            throw new ApiException("Size must be <= 100", HttpStatus.BAD_REQUEST.value());
        }
    }
}
