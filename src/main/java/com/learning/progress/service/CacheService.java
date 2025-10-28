// src/main/java/com/learning/progress/cache/CacheService.java
package com.learning.progress.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.util.TraceUtil;
import io.lettuce.core.RedisConnectionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {

    // TTL Constants
    public static final long SECTION_TTL_MINUTES = 10;
    public static final long SECTIONS_LIST_TTL_MINUTES = 5;
    public static final long QUESTION_TTL_MINUTES = 10;
    public static final long SUBMISSION_LIST_TTL_MINUTES = 5;
    public static final long SUBMISSION_RESULT_TTL_MINUTES = 10;

    // Key Prefixes
    public static final String SECTION_KEY_PREFIX = "section:";
    public static final String SECTIONS_CHALLENGE_KEY_PREFIX = "sections:challenge:";
    public static final String SECTIONS_PUBLIC_CHALLENGE_KEY_PREFIX = "sections:public:challenge:";
    public static final String QUESTION_KEY_PREFIX = "question:";
    public static final String QUESTIONS_SECTION_KEY_PREFIX = "questions:section:";
    public static final String SUBMISSIONS_CHALLENGE_KEY_PREFIX = "submissions:challenge:";
    public static final String SUBMISSION_RESULT_KEY_PREFIX = "submission:result:user:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // === GET WITH GENERIC ===
    public <T> T getCachedObject(String key, TypeReference<T> typeReference, String traceId) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                log.debug("[{}] Cache HIT for key: {}", traceId, key);
                return objectMapper.convertValue(value, typeReference);
            }
            log.debug("[{}] Cache MISS for key: {}", traceId, key);
            return null;
        } catch (RedisConnectionException e) {
            log.error("[{}] Redis connection error while getting key: {}", traceId, key, e);
            return null;
        } catch (Exception e) {
            log.error("[{}] Unexpected error while deserializing key: {}", traceId, key, e);
            return null;
        }
    }

    // === SET WITH TTL ===
    public void cacheObject(String key, Object value, long ttlMinutes, String traceId) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlMinutes, TimeUnit.MINUTES);
            log.debug("[{}] Cached object for key: {} (TTL: {}m)", traceId, key, ttlMinutes);
        } catch (RedisConnectionException e) {
            log.error("[{}] Redis connection error while caching key: {}", traceId, key, e);
        } catch (Exception e) {
            log.error("[{}] Unexpected error while caching key: {}", traceId, key, e);
        }
    }

    // === DELETE SINGLE KEY ===
    public void delete(String key, String traceId) {
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.debug("[{}] Deleted cache key: {} → {}", traceId, key, deleted);
        } catch (RedisConnectionException e) {
            log.error("[{}] Redis error deleting key: {}", traceId, key, e);
        }
    }

    // === DELETE PATTERN ===
    public void deletePattern(String pattern, String traceId) {
        try {
            redisTemplate.keys(pattern).forEach(key -> {
                redisTemplate.delete(key);
                log.debug("[{}] Deleted by pattern: {}", traceId, key);
            });
        } catch (RedisConnectionException e) {
            log.error("[{}] Redis error deleting pattern: {}", traceId, pattern, e);
        }
    }

    // === KEY BUILDERS ===
    public String buildSectionsCacheKey(Long challengeId, int page, int size, String text) {
        String normalizedText = text != null ? text.trim() : "";
        return String.format("%s%d:page:%d:size:%d:text:%s",
                SECTIONS_CHALLENGE_KEY_PREFIX, challengeId, page, size, normalizedText);
    }

    public String buildPublicSectionsCacheKey(Long challengeId, int page, int size, String text) {
        String normalizedText = text != null ? text.trim() : "";
        return String.format("%s%d:page:%d:size:%d:text:%s",
                SECTIONS_PUBLIC_CHALLENGE_KEY_PREFIX, challengeId, page, size, normalizedText);
    }

    public String buildSectionCacheKey(Long sectionId) {
        return SECTION_KEY_PREFIX + sectionId;
    }

    public String buildQuestionCacheKey(Long questionId) {
        return QUESTION_KEY_PREFIX + questionId;
    }

    public String buildQuestionsBySectionCacheKey(Long sectionId) {
        return QUESTIONS_SECTION_KEY_PREFIX + sectionId;
    }

    // NEW: Submission List Cache Key
    public String buildSubmissionsByChallengeCacheKey(
            Long challengeId, int page, int size, String text, String sortBy, String sortDir) {
        String normalizedText = text != null ? text.trim() : "";
        return String.format("%s%d:page:%d:size:%d:text:%s:sort:%s:%s",
                SUBMISSIONS_CHALLENGE_KEY_PREFIX, challengeId, page, size, normalizedText, sortBy, sortDir);
    }

    // NEW: Submission Result Cache Key
    public String buildSubmissionResultCacheKey(Long userId, Long submissionId) {
        return String.format("%s%d:submission:%d", SUBMISSION_RESULT_KEY_PREFIX, userId, submissionId);
    }

    // === CLEAR CACHE HELPERS ===
    public void clearCacheForSection(Long sectionId, Long challengeId, String traceId) {
        try {
            if (sectionId != null) {
                delete(SECTION_KEY_PREFIX + sectionId, traceId);
                delete(QUESTIONS_SECTION_KEY_PREFIX + sectionId, traceId);
            }
            if (challengeId != null) {
                deletePattern(SECTIONS_CHALLENGE_KEY_PREFIX + challengeId + ":*", traceId);
                deletePattern(SECTIONS_PUBLIC_CHALLENGE_KEY_PREFIX + challengeId + ":*", traceId);
            }
            log.debug("[{}] Cleared cache for sectionId: {}, challengeId: {}", traceId, sectionId, challengeId);
        } catch (Exception e) {
            log.error("[{}] Error clearing section cache", traceId, e);
        }
    }

    public void clearCacheForQuestion(Long questionId, Long sectionId, Long challengeId, String traceId) {
        try {
            if (questionId != null) {
                delete(QUESTION_KEY_PREFIX + questionId, traceId);
            }
            clearCacheForSection(sectionId, challengeId, traceId);
        } catch (Exception e) {
            log.error("[{}] Error clearing question cache", traceId, e);
        }
    }

    // NEW: Clear submission list cache
    public void clearSubmissionsCacheForChallenge(Long challengeId, String traceId) {
        deletePattern(SUBMISSIONS_CHALLENGE_KEY_PREFIX + challengeId + ":*", traceId);
        log.debug("[{}] Cleared submissions cache for challenge: {}", traceId, challengeId);
    }

    // NEW: Clear submission result cache
    public void clearSubmissionResultCache(Long userId, Long submissionId, String traceId) {
        String key = buildSubmissionResultCacheKey(userId, submissionId);
        delete(key, traceId);
    }
}