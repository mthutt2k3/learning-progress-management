package com.learning.progress.util;


import com.learning.progress.common.Const;
import com.learning.progress.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
public class DataUtil {

    /**
     * Normalize string:
     * - Trim leading/trailing spaces
     * - Replace multiple spaces between words with a single space
     * - Return null if input is null or blank
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }

        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.replaceAll("\\s+", " ");
    }

    public static String normalizeText(String text) {
        return Optional.ofNullable(text)
                .map(t -> t.replaceAll("[^a-zA-Z0-9\\s]", "")  // Xóa ký tự đặc biệt
                        .replaceAll("\\.+$", "")            // XÓA DẤU CHẤM CUỐI CÂU
                        .toLowerCase()
                        .trim())
                .orElse("");
    }

    public static String bold(String text) {
        if (text == null) return "";
        return "<b>" + text + "</b>";
    }

    public static OffsetDateTime parseAndValidateOffsetDateTime(String dateStr, String pattern, String fieldName) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return OffsetDateTime.parse(dateStr.trim(), formatter);
        } catch (DateTimeParseException e) {
            throw new ApiException(
                    String.format(Const.CLASS.INVALID_DATE_FORMAT, fieldName, pattern),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }


    public static void validateStartAndEndDate(OffsetDateTime startDate, OffsetDateTime endDate) {
        if (startDate == null || endDate == null) {
            log.error("Start date or end date is null");
            throw new ApiException(
                    Const.CLASS.INVALID_DATE_RANGE,
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        if (endDate.isBefore(startDate)) {
            log.error("Invalid date range: endDate {} is before startDate {}", endDate, startDate);
            throw new ApiException(
                    Const.CLASS.END_DATE_INVALID,
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }



    public static void validateDateOfBirth(Date dateOfBirth) {
        if (dateOfBirth == null) return;

        LocalDate dob = dateOfBirth.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();

        // ❌ Không được chọn ngày tương lai
        if (dob.isAfter(today)) {
            throw new ApiException(Const.DOB.DOB_IN_FUTURE, HttpStatus.BAD_REQUEST.value());
        }

        // ✅ Check tuổi tối thiểu
        int minimumAge = 3;
        if (dob.isAfter(today.minusYears(minimumAge))) {
            throw new ApiException(
                    String.format(Const.DOB.DOB_UNDER_AGE, minimumAge),
                    HttpStatus.BAD_REQUEST.value()
            );
        }

        // ✅ Check tuổi tối đa
        int maximumAge = 100;
        if (dob.isBefore(today.minusYears(maximumAge))) {
            throw new ApiException(
                    String.format(Const.DOB.DOB_OVER_AGE, maximumAge),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }

    /**
     * Lấy toàn bộ phần trước dấu @ của email
     * Ví dụ: user@example.com -> user
     */
    public static String getEmailPrefix(String email) {
        if (email == null || !email.contains("@")) {
            return null; // hoặc "" tùy nhu cầu
        }
        return email.substring(0, email.indexOf('@'));
    }
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email; // hoặc throw exception tuỳ bạn xử lý
        }

        int atIndex = email.indexOf('@');

        // Trường hợp email quá ngắn thì xử lý an toàn hơn
        if (atIndex < 4) {
            return "****" + email.substring(atIndex);
        }

        return email.substring(0, 2) + "****" + email.substring(atIndex - 2);
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }


    public static boolean isValidEmail(String email) {
        String emailRegex = Const.VALIDATE_INPUT.regexEmail;
        return email.matches(emailRegex);
    }

    public static boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber.matches(Const.VALIDATE_INPUT.regexPhone);
    }

    /**
     * check null or empty
     * Su dung ma nguon cua thu vien StringUtils trong apache common lang
     *
     * @param cs String
     * @return boolean
     */
    public static boolean isNullOrEmpty(CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ham nay mac du nhan tham so truyen vao la object nhung gan nhu chi hoat dong cho doi tuong la string
     * Chuyen sang dung isNullOrEmpty thay the
     *
     * @param obj1
     * @return
     */
    @Deprecated
    public static boolean isStringNullOrEmpty(Object obj1) {
        return obj1 == null || "".equals(obj1.toString().trim());
    }


    /**
     * Khong dung ham nay nua ma chuyen sang check thang == null
     *
     * @param obj1
     * @return
     */
    @Deprecated
    public static boolean isNullObject(Object obj1) {
        if (obj1 == null) {
            return true;
        }
        if (obj1 instanceof String) {
            return isNullOrEmpty(obj1.toString());
        }
        return false;
    }

    /**
     * Chuyen sang dung {@link BeanUtils# copyProperties}
     *
     * @param source
     * @param destClass
     * @param <T>
     * @return
     */
    @Deprecated
    public static <T> T mapToNewClass(Object source, Class<T> destClass) {
        try {
            if (source == null) {
                return null;
            }
            T dto;
            dto = destClass.getConstructor().newInstance();
            BeanUtils.copyProperties(source, dto);
            return dto;
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }


    /**
     * Map role name to prefix (2 letters)
     */
    public static String mapRoleToPrefix(String roleName) {
        if (roleName == null) {
            throw new IllegalArgumentException("roleName must not be null");
        }
        switch (roleName) {
            case "TEACHER":
            case "TEACHING_ASSISTANT":
                return "TC";
            case "STUDENT":
            case "TEST_TAKER":
                return "ST";
            case "ADMIN": return "AD";
            case "MANAGER": return "MG";
            default: throw new IllegalArgumentException("Unknown role: " + roleName);
        }
    }

    /**
     * Generate username based on role and ID.
     * Example: TEACHER + 12 -> "TC000012"
     */
    public static String generateUsername(String roleName, Long id) {
        String prefix = mapRoleToPrefix(roleName);
        return prefix + String.format("%06d", id);
    }

    public static String generateRandomPassword(int length) {
        if (length < 6) {
            throw new RuntimeException("Password length must be at least 6 characters");
        }

        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }

        return sb.toString();
    }

    public static String generateClassCode(Long id) {
        String prefix = "CL";
        return prefix + String.format("%06d", id);
    }
    public static String generateLevelCode(Long id) {
        String prefix = "LV";
        return prefix + String.format("%06d", id);
    }
    public static String generateSyllabusCode(Long id) {
        String prefix = "SY";
        return prefix + String.format("%06d", id);
    }
    public static String generateChapterCode(Long id) {
        String prefix = "CH";
        return prefix + String.format("%06d", id);
    }
    public static String generateClassChapterCode(Long chapterId, Long classId) {
        String prefixChapter = "-CH";
        String prefixClass = "CL";
        return prefixClass + String.format("%06d", classId) + prefixChapter + String.format("%03d", chapterId);
    }


    /**
     * Compute final score on a 10-point scale based on achieved total weight and max possible total weight.
     * Returns 0.0 when maxPossibleWeight is null/zero or when achievedWeight is null.
     * Result rounded to 2 decimal places.
     */
    public static Double getRawScore(Double achievedWeight, Double maxPossibleWeight) {
        if (achievedWeight == null || maxPossibleWeight == null || maxPossibleWeight == 0.0) {
            return 0.0;
        }
        double raw = (achievedWeight / maxPossibleWeight) * 10.0;
        return round(raw, 2);
    }

    /**
     * Tính điểm cuối cùng sau khi áp dụng phạt muộn.
     *
     * @param rawScore        Điểm gốc (trước phạt)
     * @param penaltyApplied  Phần trăm bị trừ (0.0 → 1.0)
     * @return finalScore = rawScore × (1 - penaltyApplied)
     */
    public static Double getFinalScore(Double rawScore, Double penaltyApplied) {
        // Kiểm tra null
        if (rawScore == null || penaltyApplied == null) {
            return rawScore; // Không phạt nếu thiếu dữ liệu
        }

        // Đảm bảo penaltyApplied trong [0.0, 1.0]
        double penalty = Math.max(0.0, Math.min(1.0, penaltyApplied));

        // Tính điểm cuối
        return rawScore * (1.0 - penalty);
    }
}
