package com.learning.progress.util;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.learning.progress.common.Const;
import com.learning.progress.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class DataUtil {

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

    /**
     * check null or empty
     * Su dung ma nguon cua thu vien StringUtils trong apache common lang
     *
     * @return boolean
     */
//    public static boolean isNullOrEmpty(CharSequence cs) {
//        int strLen;
//        if (cs == null || (strLen = cs.length()) == 0) {
//            return true;
//        }
//        for (int i = 0; i < strLen; i++) {
//            if (!Character.isWhitespace(cs.charAt(i))) {
//                return false;
//            }
//        }
//        return true;
//    }
    public static String convertUnicodeToEnglishText(String str) {
        str = str.replaceAll("à|á|ạ|ả|ã|â|ầ|ấ|ậ|ẩ|ẫ|ă|ằ|ắ|ặ|ẳ|ẵ", "a");
        str = str.replaceAll("è|é|ẹ|ẻ|ẽ|ê|ề|ế|ệ|ể|ễ", "e");
        str = str.replaceAll("ì|í|ị|ỉ|ĩ", "i");
        str = str.replaceAll("ò|ó|ọ|ỏ|õ|ô|ồ|ố|ộ|ổ|ỗ|ơ|ờ|ớ|ợ|ở|ỡ", "o");
        str = str.replaceAll("ù|ú|ụ|ủ|ũ|ư|ừ|ứ|ự|ử|ữ", "u");
        str = str.replaceAll("ỳ|ý|ỵ|ỷ|ỹ", "y");
        str = str.replaceAll("đ", "d");

        str = str.replaceAll("À|Á|Ạ|Ả|Ã|Â|Ầ|Ấ|Ậ|Ẩ|Ẫ|Ă|Ằ|Ắ|Ặ|Ẳ|Ẵ", "A");
        str = str.replaceAll("È|É|Ẹ|Ẻ|Ẽ|Ê|Ề|Ế|Ệ|Ể|Ễ", "E");
        str = str.replaceAll("Ì|Í|Ị|Ỉ|Ĩ", "I");
        str = str.replaceAll("Ò|Ó|Ọ|Ỏ|Õ|Ô|Ồ|Ố|Ộ|Ổ|Ỗ|Ơ|Ờ|Ớ|Ợ|Ở|Ỡ", "O");
        str = str.replaceAll("Ù|Ú|Ụ|Ủ|Ũ|Ư|Ừ|Ứ|Ự|Ử|Ữ", "U");
        str = str.replaceAll("Ỳ|Ý|Ỵ|Ỷ|Ỹ", "Y");
        str = str.replaceAll("Đ", "D");
        return str;
    }

    public static Timestamp getDateTimeNowUTC() {
        ZoneId utcZoneId = ZoneId.of("UTC");
        // Lấy ngày giờ hiện tại theo múi giờ UTC +0
        ZonedDateTime utcDateTime = ZonedDateTime.now().withZoneSameInstant(utcZoneId);

        // Chuyển đổi sang LocalDateTime để lưu trữ trong MySQL
        LocalDateTime localDateTime = utcDateTime.toLocalDateTime();

        return Timestamp.valueOf(localDateTime);
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static int getZeroInteger(Integer number) {
        return number == null ? 0 : number;
    }

    public static Long getZeroLong(Long number) {
        return number == null ? 0 : number;
    }

    public static double setZeroDouble(Double number) {
        return number == null ? 0 : number;
    }

    public static boolean checkDate1IsAfterDate2(Date date1, Date date2) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Calendar calendar1 = Calendar.getInstance();
        calendar1.setTime(date2);
        calendar1.set(Calendar.HOUR_OF_DAY, 0);
        calendar1.set(Calendar.MINUTE, 0);
        calendar1.set(Calendar.SECOND, 0);
        calendar1.set(Calendar.MILLISECOND, 0);
        return calendar.after(calendar1);
    }

    public static long subtractionDate(Date date1, Date date2) {
        LocalDate d1 = LocalDate.of(date1.getYear(), date1.getMonth(), date1.getDay());
        LocalDate d2 = LocalDate.of(date2.getYear(), date2.getMonth(), date2.getDay());
        return ChronoUnit.DAYS.between(d1, d2);
    }

    public static boolean checkDate1IsBeforeDate2(Date date1, Date date2) {
        LocalDate d1 = LocalDate.of(date1.getYear(), date1.getMonth(), date1.getDay());
        LocalDate d2 = LocalDate.of(date2.getYear(), date2.getMonth(), date2.getDay());
        if (d1.isBefore(d2)) {
            return true;
        } else {
            return false;
        }
    }

    public static String convertDoubleString(String doubleString) {
        if (!DataUtil.isNullOrEmpty(doubleString)) {
            double value = (double) DataUtil.round(Double.parseDouble(doubleString.replace(",", ".")), 2);
            return String.valueOf(value);
        }
        return null;
    }

    public static String convertIntegerStringToString(String integerString) {
        if (!DataUtil.isNullOrEmpty(integerString)) {
//      integerString = integerString.replace(".", "");
            if (integerString.contains(".")) {
                StringBuilder result = new StringBuilder();
                if (integerString.split("\\.").length > 0) {
                    result.append(integerString.split("\\.")[0]);
                    result = new StringBuilder(result.toString().replace(",", "."));
                    return result.toString();
                }
            }
            return integerString;
        }
        return null;
    }

    /**
     * Copy 1 list bean sang list moi
     *
     * @param sourceList
     * @param <T>
     * @return
     */
//  public static <T> List<T> cloneBean(Iterable<T> sourceList) {
//    final List<T> destList = Lists.newArrayList();
//
//    if (sourceList == null) {
//      return destList;
//    }
//
//    sourceList.forEach(t -> {
//      T destObject = cloneBean(t);
//      if (destObject != null) {
//        destList.add(cloneBean(t));
//      }
//    });
//
//    return destList;
//  }


    /**
     * Copy du lieu tu bean sang bean moi
     * Luu y chi copy duoc cac doi tuong o ngoai cung, list se duoc copy theo tham chieu
     * <p>
     * Chi dung duoc cho cac bean java, khong dung duoc voi cac doi tuong dang nhu String, Integer, Long...
     *
     * @param source
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> T cloneBean(T source) {
        try {
            if (source == null) {
                return null;
            }
            T dto = (T) source.getClass().getConstructor().newInstance();
            BeanUtils.copyProperties(source, dto);
            return dto;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }


    public static Date getDate() {
        LocalDateTime now = LocalDateTime.now();
        ZonedDateTime zonedDateTime = now.atZone(ZoneId.systemDefault());
        return Date.from(zonedDateTime.toInstant());
    }

    public static boolean isValidEmail(String email) {
        String emailRegex = Const.VALIDATE_INPUT.regexEmail;
        return email.matches(emailRegex);
    }

    public static LocalDate convertToLocalDateViaMilisecond(Date dateToConvert) {
        return Instant.ofEpochMilli(dateToConvert.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    public static boolean isValidMutilEmail(String email) {
        if (isNullObject(email)) {
            return false;
        }
        String[] input = email.split(";");
        if (!isNullOrEmpty(input) && input.length > 0) {
            for (int i = 0; i < input.length; i++) {
                if (!isValidEmail(input[i])) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isValidGender(String gender) {
        return gender.matches(Const.VALIDATE_INPUT.regexGender);
    }

    public static boolean isValidPass(String pass) {
        return pass.matches(Const.VALIDATE_INPUT.regexPass);
    }

    public static boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber.matches(Const.VALIDATE_INPUT.regexPhone);
    }

    // check nhieu so dien thoai
    public static boolean isValidMultiPhoneNumber(String phoneNumber) {
        if (isNullOrEmpty(phoneNumber)) {
            return false;
        }
        String[] input = phoneNumber.split(";");
        if (!isNullOrEmpty(input) && input.length > 0) {
            for (int i = 0; i < input.length; i++) {
                if (!isValidPhoneNumber(input[i])) {
                    return false;
                }
            }
        }
        return true;
    }


    public static String addZeroToString(String input, int strLength) {
        String result = input;
        for (int i = 1; i <= strLength - input.length(); i++) {
            result = "0" + result;
        }
        return result;
    }

    /**
     * Copy du lieu tu bean sang bean moi
     * Luu y chi copy duoc cac doi tuong o ngoai cung, list se duoc copy theo tham chieu
     * <p>
     * Chi dung duoc cho cac bean java, khong dung duoc voi cac doi tuong dang nhu String, Integer, Long...
     *
     * @param source
     * @param <T>
     * @return
     */
//  @SuppressWarnings("unchecked")
//  public static <T> T cloneBean(T source) {
//    try {
//      if (source == null) {
//        return null;
//      }
//      T dto = (T) source.getClass().getConstructor().newInstance();
//      BeanUtils.copyProperties(source, dto);
//      return dto;
//    } catch (Exception e) {
//      log.error(e.getMessage(), e);
//      return null;
//    }
//  }

    /**
     * Copy 1 list bean sang list moi
     *
     * @param sourceList
     * @param <T>
     * @return
     */
    public static <T> List<T> cloneBean(Iterable<T> sourceList) {
        final List<T> destList = Lists.newArrayList();

        if (sourceList == null) {
            return destList;
        }

        sourceList.forEach(t -> {
            T destObject = cloneBean(t);
            if (destObject != null) {
                destList.add(cloneBean(t));
            }
        });

        return destList;
    }

    /**
     * Clone an object completely
     *
     * @param source
     * @param <T>
     * @return
     * @author KhuongDV
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCloneObject(T source) {
        try {
            if (source == null) {
                return null;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(source);
            out.flush();
            out.close();

            ObjectInputStream in = new ObjectInputStream(
                    new ByteArrayInputStream(bos.toByteArray()));
            T dto = (T) in.readObject();
            in.close();
            return dto;
        } catch (IOException | ClassNotFoundException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * forward page
     *
     * @return
     * @author ThanhNT
     */
    public static String forwardPage(String pageName) {
        if (!isNullOrEmpty(pageName)) {
            return "pretty:" + pageName.trim();
        }
        return "";
    }

    /*
     * Kiem tra Long bi null hoac zero
     *
     * @param value
     * @return
     */
    public static boolean isNullOrZero(Long value) {
        return (value == null || value.equals(0L));
    }

    /*
     * Kiem tra Long bi null hoac zero
     *
     * @param value
     * @return
     */
    public static boolean isNullOrZero(Double value) {
        return (value == null || value == 0);
    }

    /*
     * Kiem tra Long bi null hoac zero
     *
     * @param value
     * @return
     */
    public static boolean isNullOrZero(String value) {
        return (value == null || value.trim().equals(""));
    }

    /*
     * Kiem tra Long bi null hoac zero
     *
     * @param value
     * @return
     */
    public static boolean isNullOrZero(Integer value) {
        return (value == null || value.equals(0));
    }

    /*
     * Kiem tra Long bi null hoac zero
     *
     * @param value
     * @return
     */
    public static boolean isNullOrOneNavigate(Long value) {
        return (value == null || value.equals(-1L));
    }

    public static boolean isNullOrOneNavigate(Integer value) {
        return (value == null || value.equals(-1));
    }

    // hungnn, check BigInteger
    public static boolean isNullOrOneBigInteger(BigInteger value) {
        return (value == null || value.equals(-1));
    }

    public static boolean isNullOrNotGreaterZero(Long value) {
        return (value == null || value.compareTo(0L) <= 0);
    }

    /**
     * Kiem tra Bigdecimal bi null hoac zero
     *
     * @param value
     * @return
     */
    public static boolean isNullOrZero(BigDecimal value) {
        return (value == null || value.compareTo(BigDecimal.ZERO) == 0);
    }

    /**
     * Upper first character
     *
     * @param input
     * @return
     */
    public static String upperFirstChar(String input) {
        if (isNullOrEmpty(input)) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    /**
     * Lower first characater
     *
     * @param input
     * @return
     */
    public static String lowerFirstChar(String input) {
        if (isNullOrEmpty(input)) {
            return input;
        }
        return input.substring(0, 1).toLowerCase() + input.substring(1);
    }

    public static String stripAccents(String input) {
        if (isNullOrEmpty(input)) {
            return input;
        }
        input = StringUtils.stripAccents(input);
        input = input.replace("Đ", "D").replace("Ð", "D").replace("ð", "d");
        return input.replace("đ", "d");
    }

    public static String safeTrim(String obj) {
        if (obj == null) return null;
        return obj.trim();
    }

    public static String safeToUpperCase(String obj) {
        if (obj == null) return null;
        return obj.toUpperCase();
    }

    public static Long safeToLong(Object obj1, Long defaultValue) {
        if (obj1 == null) {
            return defaultValue;
        }
        if (obj1 instanceof BigDecimal) {
            return ((BigDecimal) obj1).longValue();
        }
        if (obj1 instanceof BigInteger) {
            return ((BigInteger) obj1).longValue();
        }

        try {
            return Long.parseLong(obj1.toString());
        } catch (final NumberFormatException nfe) {
            return defaultValue;
        }
    }

    /**
     * @param obj1 Object
     * @return Long
     */
    public static Long safeToLong(Object obj1) {
        return safeToLong(obj1, 0L);
    }

    public static Double safeToDouble(Object obj1, Double defaultValue) {
        if (obj1 == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(obj1.toString());
        } catch (final NumberFormatException nfe) {
            return defaultValue;
        }
    }

    public static Double safeToDouble(Object obj1) {
        return safeToDouble(obj1, 0.0);
    }

    public static Short safeToShort(Object obj1, Short defaultValue) {
        if (obj1 == null) {
            return defaultValue;
        }
        try {
            return Short.parseShort(obj1.toString());
        } catch (final NumberFormatException nfe) {
            return defaultValue;
        }
    }

    public static Short safeToShort(Object obj1) {
        return safeToShort(obj1, (short) 0);
    }

    /**
     * @param obj1
     * @param defaultValue
     * @return
     * @author phuvk
     */
    public static int safeToInt(Object obj1, Integer defaultValue) {
        if (obj1 == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(obj1.toString());
        } catch (final NumberFormatException nfe) {
            return defaultValue;
        }
    }

    /**
     * @param obj1 Object
     * @return int
     */
    public static int safeToInt(Object obj1) {
        return safeToInt(obj1, 0);
    }

    /**
     * @param obj1 Object
     * @return String
     */
    public static String safeToString(Object obj1, String defaultValue) {
        if (obj1 == null) {
            return defaultValue;
        }

        return obj1.toString();
    }


    public static String safeToLower(String obj1) {
        if (obj1 == null) {
            return null;
        }

        return obj1.toLowerCase();
    }

    /**
     * @param obj1 Object
     * @return String
     */

    public static String safeToStringNull(Object obj1) {
        if (null == safeToString(obj1) || "".equals(safeToString(obj1))) {
            return null;
        } else {
            return safeToString(obj1);
        }

    }


    public static String safeToString(Object obj1) {
        return safeToString(obj1, "");
    }

    public static Date safeToDate(Object obj1) {
        if (obj1 == null) {
            return null;
        }
        return (Date) obj1;
    }

    public static java.sql.Date safeToSqlDate(Object obj1) {
        if (obj1 == null) {
            return null;
        }
        return (java.sql.Date) obj1;
    }

    public static Timestamp safeToSqlTimestamp(Object obj1) {
        if (obj1 == null) {
            return null;
        }
        return (Timestamp) obj1;
    }

    public static LocalDate safeToLocalDate(Object obj1) {
        if (obj1 == null) {
            return null;
        }
        return convertToLocalDate(safeToDate(obj1));
    }

    /**
     * safe equal
     *
     * @param obj1 Long
     * @param obj2 Long
     * @return boolean
     */
    public static boolean safeEqual(Long obj1, Long obj2) {
        if (obj1 == obj2) return true;
        return ((obj1 != null) && (obj2 != null) && (obj1.compareTo(obj2) == 0));
    }

    /**
     * safe equal
     *
     * @param obj1 Long
     * @param obj2 Long
     * @return boolean
     */
    public static boolean safeEqual(BigInteger obj1, BigInteger obj2) {
        if (obj1 == obj2) return true;
        return (obj1 != null) && (obj2 != null) && obj1.equals(obj2);
    }

    /**
     * @param obj1
     * @param obj2
     * @return
     * @date 09-12-2015 17:43:20
     * @author TuyenNT17
     * @description
     */
    public static boolean safeEqual(Short obj1, Short obj2) {
        if (obj1 == obj2) return true;
        return ((obj1 != null) && (obj2 != null) && (obj1.compareTo(obj2) == 0));
    }

    /**
     * safe equal
     *
     * @param obj1 String
     * @param obj2 String
     * @return boolean
     */
    public static boolean safeEqual(String obj1, String obj2) {
        if (obj1 == obj2) return true;
        return ((obj1 != null) && (obj2 != null) && obj1.equals(obj2));
    }

    /**
     * safe equal
     *
     * @param obj1 String
     * @param obj2 String
     * @return boolean
     */
    public static boolean safeEqualIgnoreCase(String obj1, String obj2) {
        if (obj1 == obj2) return true;
        return ((obj1 != null) && (obj2 != null) && obj1.equalsIgnoreCase(obj2));
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

    public static boolean isNullOrEmpty(final Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isNullOrEmpty(final Object[] collection) {
        return collection == null || collection.length == 0;
    }

    public static boolean isNullOrEmpty(final Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * Tra ve doi tuong default neu object la null, neu khong thi tra object
     *
     * @param object
     * @param defaultValue
     * @param <T>
     * @return
     */
    public static <T> T defaultIfNull(final T object, final T defaultValue) {
        return object != null ? object : defaultValue;
    }

    /**
     * Tra ve doi tuong default neu object la null, neu khong thi tra object
     *
     * @param object
     * @param defaultValueSupplier
     * @param <T>
     * @return
     */
    public static <T> T defaultIfNull(final T object, final Supplier<T> defaultValueSupplier) {
        return object != null ? object : defaultValueSupplier.get();
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
     * @param obj1 Object
     * @return BigDecimal
     */
    public static BigDecimal safeToBigDecimal(Object obj1) {
        if (obj1 == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(obj1.toString());
        } catch (final NumberFormatException nfe) {
            return BigDecimal.ZERO;
        }
    }

    public static BigInteger safeToBigInteger(Object obj1, BigInteger defaultValue) {
        if (obj1 == null) {
            return defaultValue;
        }
        try {
            return new BigInteger(obj1.toString());
        } catch (final NumberFormatException nfe) {
            return defaultValue;
        }
    }

    public static BigInteger safeToBigInteger(Object obj1) {
        return safeToBigInteger(obj1, BigInteger.ZERO);
    }


    public static Long add(Long number1, Long number2, Long... numbers) {
        List<Long> realNumbers = Lists.newArrayList(number1, number2);
        if (!isNullOrEmpty(numbers)) {
            Collections.addAll(realNumbers, numbers);
        }
        return realNumbers.stream()
                .mapToLong(DataUtil::safeToLong)
                .sum();
    }

    /**
     * add
     *
     * @param obj1 BigDecimal
     * @param obj2 BigDecimal
     * @return BigDecimal
     */
    public static BigInteger add(BigInteger obj1, BigInteger obj2) {
        if (obj1 == null) {
            return obj2;
        } else if (obj2 == null) {
            return obj1;
        }

        return obj1.add(obj2);
    }


    public static Character safeToCharacter(Object value) {
        return safeToCharacter(value, '0');
    }

    public static Character safeToCharacter(Object value, Character defaulValue) {
        if (value == null) return defaulValue;
        return String.valueOf(value).charAt(0);
    }

    public static Collection<Long> objLstToLongLst(List<Object> list) {
        Collection<Long> result = new ArrayList<>();
        if (!list.isEmpty()) {
            result.addAll(list.stream().map(DataUtil::safeToLong).collect(Collectors.toList()));
        }
        return result;
    }

    public static Collection<Short> objLstToShortLst(List<Object> list) {
        Collection<Short> result = new ArrayList<>();
        if (!list.isEmpty()) {
            result.addAll(list.stream().map(DataUtil::safeToShort).collect(Collectors.toList()));
        }
        return result;
    }

    public static Collection<BigDecimal> objLstToBigDecimalLst(List<Object> list) {
        Collection<BigDecimal> result = new ArrayList<>();
        if (!list.isEmpty()) {
            result.addAll(list.stream().map(DataUtil::safeToBigDecimal).collect(Collectors.toList()));
        }
        return result;
    }

    public static Collection<Double> objLstToDoubleLst(List<Object> list) {
        Collection<Double> result = new ArrayList<>();
        if (!list.isEmpty()) {
            result.addAll(list.stream().map(DataUtil::safeToDouble).collect(Collectors.toList()));
        }
        return result;
    }

    public static Collection<Character> objLstToCharLst(List<Object> list) {
        Collection<Character> result = new ArrayList<>();
        if (!list.isEmpty()) {
            result.addAll(list.stream().map(item -> item.toString().charAt(0)).collect(Collectors.toList()));
        }

        return result;
    }

    public static Collection<String> objLstToStringLst(List<Object> list) {
        Collection<String> result = new ArrayList<>();
        if (!list.isEmpty()) {
            result.addAll(list.stream().map(DataUtil::safeToString).collect(Collectors.toList()));
        }

        return result;
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

    public static String toUpper(String input) {
        if (isNullOrEmpty(input)) {
            return input;
        }
        return input.toUpperCase();
    }

    /**
     * Validate Data theo Pattern
     *
     * @author khuongdv
     */
    public static boolean validateStringByPattern(String value, String regex) {
        if (isNullOrEmpty(regex) || isNullOrEmpty(value)) {
            return false;
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(value);
        return matcher.matches();
    }


    private static final String[] CHARLIST_UNICODE = {"à", "á", "ả", "ã", "ạ", "â", "ầ", "ấ", "ẩ", "ẫ", "ậ", "ă", "ằ", "ắ", "ẳ", "ẵ", "ặ",
            "è", "é", "ẻ", "ẽ", "ẹ", "ê", "ề", "ế", "ể", "ễ", "ệ",
            "ì", "í", "ỉ", "ĩ", "ị",
            "ò", "ó", "ỏ", "õ", "ọ", "ô", "ồ", "ố", "ổ", "ỗ", "ộ", "ơ", "ờ", "ớ", "ở", "ỡ", "ợ",
            "ù", "ú", "ủ", "ũ", "ụ", "ư", "ừ", "ứ", "ử", "ữ", "ự",
            "ỳ", "ý", "ỷ", "ỹ", "ỵ",
            "À", "Á", "Ả", "Ã", "Ạ", "Â", "Ầ", "Ấ", "Ẩ", "Ẫ", "Ậ", "Ă", "Ằ", "Ắ", "Ẳ", "Ẵ", "Ặ",
            "È", "É", "Ẻ", "Ẽ", "Ẹ", "Ê", "Ề", "Ế", "Ể", "Ễ", "Ệ",
            "Ì", "Í", "Ỉ", "Ĩ", "Ị",
            "Ò", "Ó", "Ỏ", "Õ", "Ọ", "Ô", "Ồ", "Ố", "Ổ", "Ỗ", "Ộ", "Ơ", "Ờ", "Ớ", "Ở", "Ỡ", "Ợ",
            "Ù", "Ú", "Ủ", "Ũ", "Ụ", "Ư", "Ừ", "Ứ", "Ử", "Ữ", "Ự", "Ỳ", "Ý", "Ỷ", "Ỹ", "Ỵ"};

    private static final String[] CHARLIST_UF8 = {"à", "á", "ả", "ã", "ạ", "â", "ầ", "ấ", "ẩ", "ẫ", "ậ", "ă", "ằ", "ắ", "ẳ", "ẵ", "ặ",
            "è", "é", "ẻ", "ẽ", "ẹ", "ê", "ề", "ế", "ể", "ễ", "ệ",
            "ì", "í", "ỉ", "ĩ", "ị",
            "ò", "ó", "ỏ", "õ", "ọ", "ô", "ồ", "ố", "ổ", "ỗ", "ộ", "ơ", "ờ", "ớ", "ở", "ỡ", "ợ",
            "ù", "ú", "ủ", "ũ", "ụ", "ư", "ừ", "ứ", "ử", "ữ", "ự",
            "ý", "ỳ", "ỷ", "ỹ", "ỵ",
            "À", "Á", "Ả", "Ã", "Ạ", "Â", "Ầ", "Ấ", "Ẩ", "Ẫ", "Ậ", "Ă", "Ằ", "Ắ", "Ẳ", "Ẵ", "Ặ",
            "È", "É", "Ẻ", "Ẽ", "Ẹ", "Ê", "Ề", "Ế", "Ể", "Ễ", "Ệ",
            "Ì", "Í", "Ỉ", "Ĩ", "Ị",
            "Ò", "Ó", "Ỏ", "Õ", "Ọ", "Ô", "Ồ", "Ố", "Ổ", "Ỗ", "Ộ", "Ơ", "Ờ", "Ớ", "Ở", "Ỡ", "Ợ",
            "Ù", "Ú", "Ủ", "Ũ", "Ụ", "Ư", "Ừ", "Ứ", "Ử", "Ữ", "Ự", "Ý", "Ỳ", "Ỷ", "Ỹ", "Ỵ"};


    public static String removeSpecialCharVn(String input) {
        if (input == null) {
            return "";
        }
        for (int i = 0; i < CHARLIST_UNICODE.length; i++) {
            input = input.replace(CHARLIST_UNICODE[i], CHARLIST_UF8[i]);
        }
        return input;
    }

    /**
     * Bien cac ki tu dac biet ve dang ascii
     *
     * @param input
     * @return
     */
    public static String convertCharacter(String input) {
        if (input == null) {
            return "";
        }
        String[] aList = {"à", "á", "ả", "ã", "ạ", "â", "ầ", "ấ", "ẩ", "ẫ", "ậ", "ă", "ằ", "ắ", "ẳ", "ẵ", "ặ"};
        String[] eList = {"è", "é", "ẻ", "ẽ", "ẹ", "ê", "ề", "ế", "ể", "ễ", "ệ"};
        String[] iList = {"ì", "í", "ỉ", "ĩ", "ị"};
        String[] oList = {"ò", "ó", "ỏ", "õ", "ọ", "ô", "ồ", "ố", "ổ", "ỗ", "ộ", "ơ", "ờ", "ớ", "ở", "ỡ", "ợ"};
        String[] uList = {"ù", "ú", "ủ", "ũ", "ụ", "ư", "ừ", "ứ", "ử", "ữ", "ự"};
        String[] yList = {"ý", "ỳ", "ỷ", "ỹ", "ỵ"};
        String[] AList = {"À", "Á", "Ả", "Ã", "Ạ", "Â", "Ầ", "Ấ", "Ẩ", "Ẫ", "Ậ", "Ă", "Ằ", "Ắ", "Ẳ", "Ẵ", "Ặ"};
        String[] EList = {"È", "É", "Ẻ", "Ẽ", "Ẹ", "Ê", "Ề", "Ế", "Ể", "Ễ", "Ệ"};
        String[] IList = {"Ì", "Í", "Ỉ", "Ĩ", "Ị"};
        String[] OList = {"Ò", "Ó", "Ỏ", "Õ", "Ọ", "Ô", "Ồ", "Ố", "Ổ", "Ỗ", "Ộ", "Ơ", "Ờ", "Ớ", "Ở", "Ỡ", "Ợ"};
        String[] UList = {"Ù", "Ú", "Ủ", "Ũ", "Ụ", "Ư", "Ừ", "Ứ", "Ử", "Ữ", "Ự"};
        String[] YList = {"Ỳ", "Ý", "Ỷ", "Ỹ", "Ỵ"};
        for (String s : aList) {
            input = input.replace(s, "a");
        }
        for (String s : AList) {
            input = input.replace(s, "A");
        }
        for (String s : eList) {
            input = input.replace(s, "e");
        }
        for (String s : EList) {
            input = input.replace(s, "E");
        }
        for (String s : iList) {
            input = input.replace(s, "i");
        }
        for (String s : IList) {
            input = input.replace(s, "I");
        }
        for (String s : oList) {
            input = input.replace(s, "o");
        }
        for (String s : OList) {
            input = input.replace(s, "O");
        }
        for (String s : uList) {
            input = input.replace(s, "u");
        }
        for (String s : UList) {
            input = input.replace(s, "U");
        }
        for (String s : yList) {
            input = input.replace(s, "y");
        }
        for (String s : YList) {
            input = input.replace(s, "Y");
        }
        input = input.replace("đ", "d");
        input = input.replace("Đ", "D");
        return input;
    }

    public static Map<String, String> convertStringToMap(String temp, String regex, String regexToMap) {
        if (isNullOrEmpty(temp)) {
            return null;
        }
        String[] q = temp.split(regex);
        Map<String, String> lstParam = new HashMap<>();
        for (String a : q) {
            String key = a.substring(0, !a.contains(regexToMap) ? 1 : a.indexOf(regexToMap));
            String value = a.substring(a.indexOf(regexToMap) + 1);
            lstParam.put(key.trim(), value.trim());
        }
        return lstParam;
    }

    /*
     * toanld2 ham xu li khoang trang giua xau
     * **/
    public static String replaceSpaceSolr(String inputLocation) {
        if (inputLocation == null || inputLocation.trim().isEmpty()) {
            return "";
        }
        String[] arr = inputLocation.split(" ");
        String result = "";
        for (String s : arr) {
            if (!"".equals(s.trim())) {
                if (!"".equals(result)) {
                    result += " ";
                }
                result += s.trim();
            }
        }
        return result;
    }

    public static boolean isNumber(String string) {
        return !isNullOrEmpty(string) && string.trim().matches("^\\d+$");
    }

    /**
     * Ham format isdn bo +,0,84,084 o dau trong chuoi ISDN nhap vao
     *
     * @param rawIsdn
     * @return
     */
    public static String formatIsdn(String rawIsdn) {
        if (isNullOrEmpty(rawIsdn)) return "";
        String isdn = rawIsdn.trim();
        while (isdn.startsWith("0") || isdn.startsWith("+")) {
            isdn = isdn.substring(1);
        }
        while (isdn.startsWith("84") && isdn.length() > 10) {
            isdn = isdn.substring(2);
        }
        return isdn;
    }

    public static String formatAndAdd84(String isdn) {
        if (isNullOrEmpty(isdn)) return "";
        return "84" + formatIsdn(isdn);
    }


    public static boolean isValidFraction(String str) {
        if (str != null) {
            try {
                String tmp[] = str.split("/");
                if (tmp.length == 2) {
                    if (safeToLong(tmp[0]) < safeToLong(tmp[1])) {
                        return true;
                    }
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return false;

    }

    /**
     * Tim nhung phan tu co o collection a ma khong co o collection b
     *
     * @param a
     * @param b
     * @param <T>
     * @return
     */
    public static <T> List<T> subtract(Collection<T> a, Collection<T> b) {
        if (a == null) {
            return new ArrayList<>();
        }
        if (b == null) {
            return new ArrayList<>(a);
        }
        List<T> list = new ArrayList<>(a);
        list.removeAll(b);
        return list;
    }

    public static <T> List<T> intersection(Collection<T> a, Collection<T> b) {
        if (a == null || b == null) {
            return new ArrayList<>();
        }
        List<T> list = new ArrayList<>(a);
        list.retainAll(b);
        return list;
    }

    public static <T> List<T> add(Collection<T> a, Collection<T> b) {
        if (a == null && b == null) {
            return new ArrayList<>();
        }

        if (a == null) {
            return new ArrayList<>(b);
        }

        if (b == null) {
            return new ArrayList<>(a);
        }

        List<T> list = new ArrayList<>(a);
        list.addAll(b);
        return list;
    }

    public static String removeStartingZeroes(String number) {
        if (isNullOrEmpty(number)) {
            return "";
        }
        return CharMatcher.anyOf("0").trimLeadingFrom(number);
    }

    /**
     * Trim string
     *
     * @param str
     * @param alt: sau thay the khi str null
     * @return
     */
    public static String trim(String str, String alt) {
        if (str == null) {
            return alt;
        }
        return str.trim();
    }


    public static String formatStringDateSubAge(String date) {
        if (date == null) {
            return "";
        }
        StringBuilder str = new StringBuilder();
        str.append(date, 4, 6);
        str.append("/");
        str.append(date, 0, 4);
        return str.toString();
    }


    public static boolean checkNotSpecialCharacter(String str) {
        return str.matches("^[A-Za-z0-9_]+");
    }

    public static Long safeAbs(Long number) {
        return safeAbs(number, 0L);

    }

    public static Long safeAbs(Long number, Long defaultValue) {
        if (number == null) {
            if (defaultValue == null) {
                return 0L;
            }
            return defaultValue < 0 ? -defaultValue : defaultValue;
        }

        return number < 0 ? -number : number;
    }


    public static String firstNonEmpty(String... strings) {
        for (String string : strings) {
            if (!isNullOrEmpty(string)) {
                return string;
            }
        }
        return "";
    }

    /**
     * ham làm tron so voi so thap phan sau dau phay
     *
     * @param value
     * @param numberAfterDot
     * @return
     */
    public static BigDecimal roundNumber(BigDecimal value, int numberAfterDot) {
        return value.setScale(numberAfterDot, BigDecimal.ROUND_HALF_UP);
    }


    public static Long convertDateToLong(String d) {
        Date date = new Date(d);
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        Calendar cal = Calendar.getInstance();
        if (d == null) {
            return 0L;
        }
        cal.setTime(date);
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMinimum(Calendar.DAY_OF_MONTH));
        String sdate = dateFormat.format(cal.getTime());
        return Long.valueOf(sdate);
    }

    public static String convertDateToString(Date date, String format) {
        if (null == date || null == format) {
            return "";
        }
        DateFormat dateFormat = new SimpleDateFormat(format);
        String strDate = dateFormat.format(date);
        return strDate;
    }


    public static String convertDateToStringPatter(Date date, String patternDate) {
        if (null == date) {
            return "";
        }
        DateFormat dateFormat = new SimpleDateFormat(patternDate);
        String strDate = dateFormat.format(date);
        return strDate;
    }

    public static Long convertStringToLong(String month) {
        String month2 = month.replace("/", "");
        String year = month2.substring(2, 6);
        String mon = month2.substring(0, 2);
        String date = year + mon + "01";
        return Long.valueOf(date);

    }

    public static Long convertStringToFirstQuarter(String month) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/yyyy");
        Date d = sdf.parse(month);
        Calendar cal = Calendar.getInstance();
        cal.setTime(d);
        cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) / 3 * 3);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        LocalDate date = cal.getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        String firstDate = String.valueOf(date);
        String customDate = firstDate.replace("-", "");
        Long firstDateOfQuarter = Long.valueOf(customDate);
        return firstDateOfQuarter;
    }

    public static Long convertStringToFirstYear(String month) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/yyyy");
        Date d = sdf.parse(month);
        Calendar cal = Calendar.getInstance();
        cal.setTime(d);
        cal.set(Calendar.DAY_OF_YEAR, 1);
        LocalDate date = cal.getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        String firstDate = String.valueOf(date);
        String customDate = firstDate.replace("-", "");
        Long firstDateOfQuarter = Long.valueOf(customDate);
        return firstDateOfQuarter;
    }

    public static String convertDateForFile(String d) {
        if (isNullOrEmpty(d))
            return "";
        Date date = new Date(d);
        DateFormat dateFormat = new SimpleDateFormat("MM/yyyy");
        String sdate = dateFormat.format(date);
        return sdate;
    }

    public static boolean compareQuarterInFileAndSystem(String d1) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/yyyy");
        boolean check = false;
        try {
            Date date1 = sdf.parse(d1);
            Date d2 = getDate();
            Date date2 = sdf.parse(sdf.format(d2));

            if ((date1.getMonth() / 3) + 1 < (date2.getMonth() / 3) + 1 && date1.getYear() <= date2.getYear()) {
                check = false;
            } else {
                check = true;
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return check;

    }

    public static boolean compareFromDateAndToDate(String pstrFromDate, String pstrToDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        boolean check = false;
        try {
            Date date1 = sdf.parse(pstrFromDate);
            Date date2 = sdf.parse(pstrToDate);
            if (pstrToDate != null) {
                if (date1.getMonth() > date2.getMonth() && date1.getYear() >= date2.getYear()) {
                    check = false;
                } else {
                    check = true;
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return check;

    }

    public static boolean compareYearInFileAndSystem(String d1) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/yyyy");
        boolean check = false;
        try {
            Date date1 = sdf.parse(d1);
            Date d2 = getDate();
            Date date2 = sdf.parse(sdf.format(d2));

            if (date1.getYear() < date2.getYear()) {
                check = false;
            } else {
                check = true;
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return check;

    }

    public static boolean compareDateAndLastSystemDate(String d1) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/yyyy");
        boolean check = false;
        try {
            Date date1 = sdf.parse(d1);
            Instant instant = Instant.ofEpochMilli(date1.getTime());
            LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            LocalDate date = localDateTime.toLocalDate();
            LocalDate date2 = LocalDate.now().minusMonths(1);

            if (date.getMonth().compareTo(date2.getMonth()) >= 0) {
                check = true;
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return check;

    }

    public static Long convertToPrdIdFirstDay(Long d) {
        String sdate;
        if (isNullOrZero(d)) {
            return 0L;
        } else {
            Date date = new Date(d);
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            Calendar cal = Calendar.getInstance();
            if (isNullOrZero(d)) {
                return 0L;
            }
            cal.setTime(date);
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMinimum(Calendar.DAY_OF_MONTH));
            sdate = dateFormat.format(cal.getTime());
        }
        return Long.valueOf(sdate);
    }

    public static Date convertStringToDate(String s, String patternDate) {
        try {
            if (s != null) {
                Date date = new SimpleDateFormat(patternDate).parse(s);
                return date;
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Date getDateTimeNow() throws Exception {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = getDate();
        String local = df.format(date);
        Date datenow = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(local);
        return datenow;
    }

    public static String convertFileSize(Double size_bytes, String type) {
        double size_kb = 0;
        double size_mb = 0;
        double size_gb = 0;
        if (size_bytes > 0) {
            size_kb = size_bytes / 1024;
            size_mb = size_kb / 1024;
            size_gb = size_mb / 1024;
        }

        if ("KB".equals(type)) {
            return (Math.floor(size_kb * 100) / 100) + " KB";
        }
        if ("MB".equals(type)) {
            return (Math.floor(size_mb * 100) / 100) + " GB";
        }
        if ("GB".equals(type)) {
            return (Math.floor(size_gb * 100) / 100) + " GB";
        }
        return "0 " + type;
    }

    public static LocalDate convertToLocalDate(Date dateToConvert) {
        return dateToConvert.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    /**
     * convert blob to string
     *
     * @param blob
     * @return
     */
    public static String blobToBase64(Blob blob) {
        try {
            if (null == blob) {
                return "";
            }
            int blobLength = (int) blob.length();
            byte[] blobAsBytes = blob.getBytes(1, blobLength);

            blob.free();
            return Base64.getEncoder().encodeToString(blobAsBytes);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return "";
        }
    }

    public static byte[] blobToBytes(Object object) {
        try {
            if (object == null) {
                return null;
            }
            Blob blob = (Blob) object;
            int blobLength = (int) blob.length();
            byte[] blobAsBytes = blob.getBytes(1, blobLength);

            blob.free();
            return blobAsBytes;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return null;
        }
    }

    public static BufferedImage convertByteArrayToBufferedImage(byte[] imageInByte) throws IOException {
        if (imageInByte == null)
            return null;
        InputStream in = new ByteArrayInputStream(imageInByte);
        return ImageIO.read(in);

    }

    public static String convertLocalDateToString(LocalDate localDate) {
        String formattedDate = localDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        return formattedDate;
    }

    public static String convertLocalDateToString(LocalDate localDate, String pattern) {
        String formattedDate = localDate.format(DateTimeFormatter.ofPattern(pattern));
        return formattedDate;
    }

    public static String removeAccent(String string) {
        if (StringUtils.isEmpty(string)) {
            return null;
        }
        String temp = Normalizer.normalize(string, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        temp = pattern.matcher(temp).replaceAll("");
        return temp.replaceAll("đ", "d").replaceAll("Đ", "D");
    }

    public static <Long> List<Long> removeDuplicates(List<Long> array) {
        // convert input array to populated list

        // convert list to populated set
        HashSet<Long> set = new HashSet<Long>();
        set.addAll(array);

        // convert set to array & return,
        // use cast because you can't create generic arrays
        return new ArrayList<Long>(set);
    }

    public static <E> E getData(ObjectMapper objectMapper, Object object, Class<E> clazz) throws IOException {
        if (object instanceof String) {
            return objectMapper.readValue(object.toString(), clazz);
        }
        return objectMapper.readValue(objectMapper.writeValueAsBytes(object), clazz);
    }

    public static String convertBienXoSe(String bienSoXe) {
        if (isNullOrEmpty(bienSoXe)) return null;
        bienSoXe = stripAccents(bienSoXe);
        bienSoXe = bienSoXe.replaceAll("[^\\w\\s]", "");
        bienSoXe = bienSoXe.replace(" ", "");
        return bienSoXe.toUpperCase();
    }

    public static String getUlrImage(String folder, String fileName) {
        return String.format("/image/%s/%s", folder, fileName);
    }

    public static int getPage(Pageable pageable) {
        if (pageable.getPageNumber() <= 0)
            return 0;
        else return (pageable.getPageNumber() - 1) * pageable.getPageSize();
    }

    public static String objectToJsonNotNull(Object object) {
        Gson gson = new Gson();
        return gson.toJson(object);
    }

    public static Date addHours(Date date, int hours) {
        if (date == null) {
            return null;
        }
        Calendar cal = Calendar.getInstance(); // creates calendar
        cal.setTime(date);               // sets calendar time/date
        cal.add(Calendar.HOUR_OF_DAY, hours);      // adds one hour
        return cal.getTime();
    }

    public static List<String> lstLoaiYeuCau() {
        List<String> loaiYeuCauIgnoreMap = new ArrayList<>();
        loaiYeuCauIgnoreMap.add("2T");
        loaiYeuCauIgnoreMap.add("3T");
        loaiYeuCauIgnoreMap.add("16T");
        loaiYeuCauIgnoreMap.add("17T");
        loaiYeuCauIgnoreMap.add("0T"); // các mã có loại yc 0T sẽ có json là map với cá nhân
        return loaiYeuCauIgnoreMap;
    }


    public static Date parseDateTime(Long lDate) {
        if (lDate != null) {
            String sDate = "" + lDate;
            Integer year = Integer.valueOf(sDate.substring(0, 4));
            Integer month = Integer.valueOf(sDate.substring(4, 6));
            Integer day = Integer.valueOf(sDate.substring(6, 8));
            Integer hour = Integer.valueOf(sDate.substring(8, 10));
            Integer minutes = Integer.valueOf(sDate.substring(10, 12));
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, day, hour, minutes);
            return calendar.getTime();
        }
        return null;
    }

    public static Date parseDateTime(String lDate) {
        if (!Strings.isNullOrEmpty(lDate)) {
            Integer year = Integer.valueOf(lDate.substring(0, 4));
            Integer month = Integer.valueOf(lDate.substring(4, 6));
            Integer day = Integer.valueOf(lDate.substring(6, 8));
            Integer hour = Integer.valueOf(lDate.substring(8, 10));
            Integer minutes = Integer.valueOf(lDate.substring(10, 12));
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, day, hour, minutes);
            return calendar.getTime();
        }
        return null;
    }

    public static Date parseDateTime(String date, String pattern) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
        try {
            return dateFormat.parse(date);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Function genarate code for encrypt
     *
     * @param number
     * @param lenght
     * @return
     */
    public static String codeGenarate(Long number, Long lenght) {
        String code = number.toString();
        while (code.length() < lenght) {
            code = "0" + code;
        }
        return code;
    }

    public static Date convertDateToDateWithLastTimeOfDay(Date date) {
        ZonedDateTime d = date.toInstant().atZone(ZoneId.of("UTC"));
        SimpleDateFormat simpleDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        try {
            return simpleDate.parse((d.getDayOfMonth() + "/" + d.getMonthValue() + "/" + d.getYear() + " " + "23:59:59").toString());
        } catch (ParseException e) {
            return null;
        }
    }

    public static Date convertDateToDateWithFirstTimeOfDay(Date date) {
        ZonedDateTime d = date.toInstant().atZone(ZoneId.of("UTC"));
        SimpleDateFormat simpleDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        try {
            return simpleDate.parse((d.getDayOfMonth() + "/" + d.getMonthValue() + "/" + d.getYear() + " " + "12:00:00").toString());
        } catch (ParseException e) {
            return null;
        }
    }

    public static Date getUTCDateTime() {
        try {
            ZonedDateTime utcTime = new Date().toInstant().atZone(ZoneId.of("UTC"));
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").parse(utcTime.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public static Date getUTCDateTime(Date date) {
        if (date == null) {
            return null;
        }
        try {
            ZonedDateTime utcTime = date.toInstant().atZone(ZoneId.of("UTC"));
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").parse(utcTime.toString());
        } catch (Exception e) {
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


    public static String generateImageUrl(String imageUrlCheck) {
        if (DataUtil.isNullOrEmpty(imageUrlCheck)) {
            return null;
        }
        String imageUrl = imageUrlCheck.trim();
        if (imageUrl.startsWith("http")) {
            int count = 0;
            int indexCheck = 0;
            for (int i = 0; i < imageUrlCheck.length(); i++) {
                if (imageUrlCheck.charAt(i) == '/') {
                    count++;
                }
                if (count == 3) {
                    indexCheck = i;
                    break;
                }
            }
            return imageUrlCheck.substring(indexCheck);
        } else {
            return imageUrlCheck;
        }
    }

    public static String getString(String content) {
        return isNullOrEmpty(content) ? null : content;
    }

    public static String getEmptyString(String content) {
        return isNullOrEmpty(content) ? "" : content;
    }

    public static String getStringLike(String content) {
        return isNullOrEmpty(content) ? "%%" : "%" + content.trim() + "%";
    }

    public static Integer getInteger(Integer content) {
        return content == null ? 0 : content;
    }

    public static Long getLong(Long content) {
        return content == null ? 0 : content;
    }

    public static Long getLongPositiveNumber(Long content) {
        return content < 0 ? 0 : content;
    }

    public static List<Long> convertStringToListLong(String content, String point) {
        if (isNullOrEmpty(content)) {
            return new ArrayList<>();
        }
        content = content.replace(" ", "");
        List<Long> list = new ArrayList<>();
        if (!content.contains(point)) {
            try {
                Long abc = Long.parseLong(content);
                list.add(abc);
            } catch (Exception e) {

            }
        } else {
            String[] strings = content.split(point);
            for (String string : strings) {
                try {
                    Long abc = Long.parseLong(string);
                    list.add(abc);
                } catch (Exception e) {

                }
            }
        }
        return list;
    }

    public static String genFunctionCode(String functionName) {
        if (functionName == null) {
            return null;
        }
        functionName = convertUnicodeToEnglishText(functionName.trim().toUpperCase()).trim().replace("(", "").replace(")", "");
        StringBuilder result = new StringBuilder();
        if (functionName.contains(" ")) {
            String[] strings = functionName.split(" ");
            for (String string : strings) {
                if (string.length() > 0) {
                    result.append(string.charAt(0)).append(string.charAt(string.length() - 1));
                }
            }
        } else {
            if (functionName.length() > 0) {
                result.append(functionName.charAt(0)).append(functionName.charAt(functionName.length() - 1));
            }
        }
        return result.toString() + functionName.length();
    }

    public static Date convertDateToZeroHours(Date sourceDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(sourceDate);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static String convertListStringToString(List<String> stringList, String point) {
        if (stringList == null || stringList.size() == 0) {
            return null;
        }
        if (stringList.size() == 1) {
            return stringList.get(0);
        }
        AtomicReference<String> result = new AtomicReference<>("");
        stringList.forEach(s -> {
            result.set(result + s + point);
        });
        return result.toString().substring(0, result.toString().length() - point.length());
    }

    public static List<String> convertStringToListString(String content, String point) {
        if (!DataUtil.isNullOrEmpty(content) && !DataUtil.isNullOrEmpty(point)) {
            return Arrays.asList(content.split(point));
        }
        return new ArrayList<>();
    }

    public static List<String> splitTextTolist(String text, String point) {
        if (isNullOrEmpty(text) || isNullOrEmpty(point)) {
            return new ArrayList<>();
        }
        String[] abc;
        text = text.replace(" ", "");
        abc = text.split(point);
        return Arrays.asList(abc);
    }

    public static Date convertUtc7ToUtc0Datetime(String time) {
        if (DataUtil.isNullOrEmpty(time)) {
            return DataUtil.getUTCDateTime();
        }
        // Tạo DateTimeFormatter với định dạng đầu vào "yyyy-MM-dd HH:mm:ss" và múi giờ UTC+7
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(ZoneId.of("UTC+7"));

        // Chuyển đổi chuỗi đầu vào thành đối tượng ZonedDateTime
        ZonedDateTime utc7DateTime = ZonedDateTime.parse(time, inputFormatter);

        // Chuyển đổi đối tượng ZonedDateTime sang múi giờ UTC+0
        ZonedDateTime utc0DateTime = utc7DateTime.withZoneSameInstant(ZoneOffset.UTC);
        // Tạo đối tượng DateTimeFormatter với định dạng "yyyy-MM-dd HH:mm:ss"
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Chuyển đổi đối tượng ZonedDateTime thành chuỗi đại diện cho ngày giờ ở múi giờ UTC+0 với định dạng "yyyy-MM-dd HH:mm:ss"
        String utc0DateString = utc0DateTime.format(formatter);

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date dateOutput = null;
        try {
            dateOutput = dateFormat.parse(utc0DateString);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        // Lưu trữ đối tượng ZonedDateTime vào CSDL với entity kiểu Date trong Spring
//    Timestamp utc0Date = new Timestamp();
//    Date output = Date.from(utc0DateTime.toInstant());
//    Date dateOutput = LocalDateTime.parse(utc0DateString);
        return dateOutput;
    }

    public static String getUtcDateTimeWithFormat(String dateString, String utcStringFormat, String formatter) {
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(utcStringFormat);
        LocalDateTime dateTime = LocalDateTime.parse(dateString, inputFormatter);

        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(formatter);
        String formattedDate = dateTime.format(outputFormatter);
        return formattedDate;
    }

    public static boolean hasSpecialCharacter(String content) {
//    // Biểu thức chính quy kiểm tra ký tự đặc biệt
//    String specialCharRegex = "[!@#$%^&*(),.?\":{}|<>]"; // Các ký tự đặc biệt cần kiểm tra
//
//    Pattern pattern = Pattern.compile(specialCharRegex);
//    Matcher matcher = pattern.matcher(str);
//
//    return matcher.find(); // Trả về true nếu chuỗi chứa ký tự đặc biệt, ngược lại trả về false

        Pattern p = Pattern.compile("[^a-z0-9 ]", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(content);
        return m.find();
    }

    public static boolean isInputStringOverLimitLength(String input, int lengthLimit) {
        if (input.trim().length() > lengthLimit) {
            return true;
        }
        return false;
    }

    //  public static boolean hasSpecialCharacterInFileName(String common){
//    List<String> str = new ArrayList();
//    str.add("/");
//    str.add("\\");
//    str.add(":");
//    str.add("*");
//    str.add("?");
//    str.add("<");
//    str.add(">");
//    str.add("|");
//    for (String s:str){
//      if(common.contains(s)){
//        return true;
//      }
//    };
//    return false;
//  }
    public static boolean isNot0or1(Integer content) {
        if (content == 1) {
            return false;
        }
        if (content == 0) {
            return false;
        }
        return true;
    }
    public static boolean dateValidate(Date fromDate, Date toDate) {
        return isAfterDateNow(fromDate, toDate) && !fromDate.after(toDate);
    }
    public static boolean isAfterDateNow(Date fromDate, Date toDate){
        return !fromDate.after(new Date())&&!toDate.after(new Date());
    }


    public static PageRequest getPageable(Integer pageNumber, Integer pageSize){
        if(pageNumber == null||pageNumber ==0){
            pageNumber =1;
        }
        if(pageSize == null||pageSize == 0){
            pageSize=20;
        }
        return PageRequest.of(pageNumber - 1, pageSize);
    }

    public static String convertDoubleToString(Double number){
        if (number == null){
            return "0";
        }
        return String.format("%.2f", number);
    }
}
