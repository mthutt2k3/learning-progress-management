package com.learning.progress.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public final class JsonUtil {

    private JsonUtil() {}

    // Tái sử dụng ObjectMapper (thread-safe)
    private static final ObjectMapper MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // ĐĂNG KÝ MODULE CHO JAVA 8 TIME (OffsetDateTime, LocalDateTime,...)
        mapper.registerModule(new JavaTimeModule());

        // Không serialize date thành timestamp
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Không fail khi object rỗng
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        // Chỉ serialize field không null (tùy chọn)
        // mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        return mapper;
    }

    public static String objectToJson(Object obj) {
        if (obj == null) return null;
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Error when writing object to json string [{}]", e.getMessage(), e);
            return null;
        }
    }

    public static <T> T jsonToObject(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            log.error("Error when reading json string to object [{}]", e.getMessage(), e);
            throw new IllegalArgumentException("Không đọc được dữ liệu", e);
        }
    }

    public static <T> T responseToObject(Object obj, Class<T> clazz) {
        if (obj == null) return null;
        String json = objectToJson(obj);
        return jsonToObject(json, clazz);
    }

    public static <T> List<T> responseToListObject(Object obj, Class<T> clazz) {
        if (obj == null) return Collections.emptyList();
        String json = objectToJson(obj);
        List<?> listObj = jsonToObject(json, List.class);
        if (listObj == null) return Collections.emptyList();

        List<T> list = new ArrayList<>();
        for (Object itemObj : listObj) {
            T item = responseToObject(itemObj, clazz);
            list.add(item);
        }
        return list;
    }

    public static Map<String, Object> objectToMap(Object obj) {
        if (obj == null) return Collections.emptyMap();
        try {
            String json = MAPPER.writeValueAsString(obj);
            return MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Error converting object to map [{}]", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    public static <T> List<T> jsonToList(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            CollectionType type = MAPPER.getTypeFactory().constructCollectionType(List.class, clazz);
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            log.error("Error deserializing JSON to List<{}>", clazz.getSimpleName(), e);
            return Collections.emptyList();
        }
    }

    // BONUS: Pretty print
    public static String objectToJsonPretty(Object obj) {
        if (obj == null) return null;
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Error pretty printing JSON [{}]", e.getMessage(), e);
            return null;
        }
    }
}