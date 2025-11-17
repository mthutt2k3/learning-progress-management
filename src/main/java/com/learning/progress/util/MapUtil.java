package com.learning.progress.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Component
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MapUtil {

    public static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }

    public static OffsetDateTime getOffsetDateTime(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String) {
            try {
                return OffsetDateTime.parse((String) value);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<String> getListString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return null;
    }

    public static <T> T get(Map<String, Object> map, String key, Class<T> clazz) {
        Object value = map.get(key);
        if (value == null) return null;
        try {
            return clazz.cast(value);
        } catch (ClassCastException e) {
            return null;
        }
    }
}