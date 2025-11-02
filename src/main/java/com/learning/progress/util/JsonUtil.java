package com.learning.progress.util;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public final class JsonUtil {

    private JsonUtil() {
    }

    public static String objectToJson(Object obj) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Error when writing object to json string [{}]", e.getMessage());
        }
        return null;
    }

    public static <T> T jsonToObject(String json, Class<T> clazz) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(json, clazz);
        } catch (IOException e) {
            log.error("Error when reading json string to object [{}]", e.getMessage());
            throw new IllegalArgumentException("Không đọc được dữ liệu");
        }
    }

    public static <T> T responseToObject(Object obj, Class<T> clazz) {
        String json = objectToJson(obj);
        return jsonToObject(json, clazz);
    }

    public static <T> List<T> responseToListObject(Object obj, Class<T> clazz) {
        String json = objectToJson(obj);
        List<?> listObj = jsonToObject(json, List.class);
        if (listObj == null) {
            return Collections.emptyList();
        }

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
            ObjectMapper objectMapper = new ObjectMapper();
            // Convert object -> JSON string
            String json = objectMapper.writeValueAsString(obj);
            // Convert JSON string -> Map
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Error converting object to map [{}]", e.getMessage());
            return Collections.emptyMap();
        }
    }
    public static <T> List<T> jsonToList(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            log.error("Error deserializing JSON to List<{}>", clazz.getSimpleName(), e);
            return Collections.emptyList();
        }
    }

}

