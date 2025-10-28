package com.learning.progress.util;

import com.learning.progress.common.ClassStudentStatus;
import com.learning.progress.common.ClassTeacherStatus;
import com.learning.progress.common.Const;
import com.learning.progress.common.RoleName;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.ClassStudentRepository;
import com.learning.progress.repository.ClassTeacherRepository;
import com.learning.progress.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppValidator {

    @Value("${app.pagination.max-size}")
    private int maxSize;

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassStudentRepository classStudentRepository;
    /**
     * Validates that the order numbers in the given list of DTOs are sequential from 1 to expectedCount.
     *
     * @param dtos              List of DTOs containing order numbers
     * @param orderNumberMapper Function to extract order number from DTO
     * @param expectedCount     Expected number of order numbers (must match size of unique order numbers)
     * @param entityName        Name of the entity for error messaging (e.g., "Section", "Question")
     * @param <T>               Type of DTO
     * @throws ApiException if order numbers are not sequential or do not match expected count
     */
    public static <T> void validateSequentialOrderNumbers(List<T> dtos, Function<T, Integer> orderNumberMapper,
                                                          int expectedCount, String entityName) {
        Set<Integer> orderNumbers = dtos.stream()
                .map(orderNumberMapper)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Integer> expectedOrders = IntStream.rangeClosed(1, expectedCount).boxed()
                .collect(Collectors.toSet());

        if (orderNumbers.size() != expectedCount || !orderNumbers.equals(expectedOrders)) {
            log.error("{} order numbers must be sequential from 1 to {}. Found: {}", entityName, expectedCount, orderNumbers);
            throw new ApiException(
                    String.format("%s order numbers must be sequential from 1 to %d. Found: %s", entityName, expectedCount, orderNumbers),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }
    public boolean hasRole(RoleName roleName) {
        String currentRole = jwtUtil.extractRoleFromCurrentRequest();
        RoleName currentRoleName = this.validateAndConvertEnum(currentRole, RoleName.class);
        return currentRoleName.equals(roleName);
    }
    /**
     * Validate that the current user is allowed to access a specific class.
     * MANAGERs are always allowed.
     * TEACHERs and STUDENTs must belong to that class.
     */
    public void validateUserAccessToClass(Long classId) {
        String username = jwtUtil.extractUsernameFromCurrentRequest();
        User user = userRepository.findByUserNameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.UNAUTHORIZED.value()));
        RoleName roleName = user.getRole().getName();
        // MANAGER has full access
        if (roleName == RoleName.MANAGER) {
            return;
        }

        boolean hasAccess = false;

        // Teacher in this class?
        if (roleName == RoleName.TEACHER || roleName == RoleName.TEACHING_ASSISTANT) {
            hasAccess = classTeacherRepository.existsByUser_IdAndClazz_IdAndStatus(user.getId(), classId, ClassTeacherStatus.ACTIVE);
        }

        // Student in this class?
        if (roleName == RoleName.STUDENT ||  roleName == RoleName.TEST_TAKER) {
            hasAccess = classStudentRepository.existsByUser_IdAndClazz_IdAndStatus(user.getId(), classId, ClassStudentStatus.ACTIVE);
        }

        if (!hasAccess) {
            throw new ApiException("You are not authorized to access this class", HttpStatus.FORBIDDEN.value());
        }
    }

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
    public <E extends Enum<E>> E validateAndConvertEnum(String value, Class<E> enumClass) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException ex) {
            String message = String.format(
                    Const.ENUM.INVALID_ENUM_VALUE,
                    enumClass.getSimpleName(),
                    value
            );
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
