package com.learning.progress.util;

import com.learning.progress.common.Const;
import com.learning.progress.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class AppValidator {

    @Value("${app.pagination.max-size}")
    private int maxSize;

    public <E extends Enum<E>> void validateEnumValue(
            Class<E> enumClass,
            String value
    ) {
        if (!EnumUtil.isValidEnum(enumClass, value)) {
            String message = String.format(
                    Const.ENUM.INVALID_ENUM_VALUE,
                    enumClass.getSimpleName(),
                    value
            );
            throw new ApiException(message, HttpStatus.BAD_REQUEST.value());
        }
    }

    public <E extends Enum<E>> void validateAllowedEnumValue(
            Class<E> enumClass,
            String value,
            Set<E> allowedValues ){
        if (!EnumUtil.isAllowedEnumValue(enumClass, value, allowedValues)) {
            String enumName = enumClass.getSimpleName(); // Ví dụ: RoleName, UserStatus
            String message = String.format(Const.ENUM.INVALID_ENUM_VALUE, enumName, value);
            throw new ApiException(message, HttpStatus.BAD_REQUEST.value());
        }
    }

    public <E extends Enum<E>> List<E> validateAndConvertEnums(
            List<String> values,
            Class<E> enumClass ) {
        if (values == null) return null;

        return values.stream().map(v -> {
            if (!EnumUtil.isValidEnum(enumClass, v)) {
                String message = String.format(
                        Const.ENUM.INVALID_ENUM_VALUE,
                        enumClass.getSimpleName(),
                        v
                );
                throw new ApiException(message, HttpStatus.BAD_REQUEST.value());
            }
            return Enum.valueOf(enumClass, v);
        }).collect(Collectors.toList());
    }

    public void validateSortParams(List<String> validSortFields, String sortBy, String sortDir) {
        if (!validSortFields.contains(sortBy)) {
            throw new ApiException(
                    String.format(Const.SORT.INVALID_SORT_BY, sortBy, String.join(", ", validSortFields)),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
        if (!List.of("asc", "desc").contains(sortDir.toLowerCase())) {
            throw new ApiException(
                    String.format(Const.SORT.INVALID_SORT_DIR, sortDir),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }

    public void validatePaginationParams(int page, int size) {
        if (page < 0) {
            throw new ApiException(
                    Const.PAGINATION.PAGE_NEGATIVE,
                    HttpStatus.BAD_REQUEST.value()
            );
        }
        if (size <= 0) {
            throw new ApiException(
                    Const.PAGINATION.SIZE_NEGATIVE_OR_ZERO,
                    HttpStatus.BAD_REQUEST.value()
            );
        }
        if (size > maxSize) {
            throw new ApiException(
                    String.format(Const.PAGINATION.SIZE_EXCEEDS_MAX, maxSize),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }
}
