package com.learning.progress.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
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
    public static final String SECTIONS_CHALLENGE_KEY_PREFIX = "sections:challenge:";
    public static final String SECTIONS_PUBLIC_CHALLENGE_KEY_PREFIX = "sections:public:challenge:";
    public static final String SUBMISSIONS_CHALLENGE_KEY_PREFIX = "submissions:challenge:";
    public static final String SUBMISSION_RESULT_KEY_PREFIX = "submission:result:user:";

    // LEVEL CACHE (MỚI)
    public static final long LEVEL_TTL_MINUTES = 15;
    public static final long LEVEL_LIST_TTL_MINUTES = 10;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    // =====================================================================
    // GET + SET (TỰ ĐỘNG LOG + TRACEID)
    // =====================================================================

    public <T> T getCachedObject(String key, TypeReference<T> typeReference) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                log.debug("Cache HIT for key: {}", key);
                return objectMapper.convertValue(value, typeReference);
            }
            log.debug("Cache MISS for key: {}", key);
            return null;
        } catch (Exception e) {
            log.warn("Failed to get cache key: {}", key, e);
            return null;
        }
    }

    public void cacheObject(String key, Object value, long ttlMinutes) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlMinutes, TimeUnit.MINUTES);
            log.debug("Cached object for key: {} (TTL: {}m)", key, ttlMinutes);
        } catch (Exception e) {
            log.warn("Failed to cache key: {}", key, e);
        }
    }

    // =====================================================================
    // DELETE + PATTERN (TỰ ĐỘNG LOG)
    // =====================================================================

    public void delete(String key) {
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.debug("Deleted cache key: {} → {}", key, deleted);
        } catch (Exception e) {
            log.warn("Failed to delete key: {}", key, e);
        }
    }

    private void deletePattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Deleted {} keys by pattern: {}", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.warn("Failed to delete pattern: {}", pattern, e);
        }
    }

    // =====================================================================
    // KEY BUILDERS (LEVEL + CÁC LOẠI KHÁC)
    // =====================================================================

    // LEVEL
    public String buildLevelDetailsCacheKey(Long levelId) {
        return "level:details:" + levelId;
    }

    public String buildAllLevelsCacheKey(int page, int size, String text) {
        String normalizedText = text != null ? text.trim() : "";
        return String.format("levels:all:page:%d:size:%d:text:%s", page, size, normalizedText);
    }

    public String buildPublishedLevelsCacheKey(int page, int size, String text) {
        String normalizedText = text != null ? text.trim() : "";
        return String.format("levels:published:page:%d:size:%d:text:%s", page, size, normalizedText);
    }

    // SECTION
    public String buildSectionsCacheKey(Long challengeId, int page, int size, String text) {
        String normalizedText = text != null ? text.trim() : "";
        return String.format("%s%d:page:%d:size:%d:text:%s", SECTIONS_CHALLENGE_KEY_PREFIX, challengeId, page, size, normalizedText);
    }

    public String buildPublicSectionsCacheKey(Long challengeId, int page, int size, String text) {
        String normalizedText = text != null ? text.trim() : "";
        return String.format("%s%d:page:%d:size:%d:text:%s", SECTIONS_PUBLIC_CHALLENGE_KEY_PREFIX, challengeId, page, size, normalizedText);
    }

    // SUBMISSION
    public String buildSubmissionsByChallengeCacheKey(Long challengeId, int page, int size, String text, String sortBy, String sortDir) {
        String normalizedText = text != null ? text.trim() : "";
        return String.format("%s%d:page:%d:size:%d:text:%s:sort:%s:%s",
                SUBMISSIONS_CHALLENGE_KEY_PREFIX, challengeId, page, size, normalizedText, sortBy, sortDir);
    }

    public String buildSubmissionResultCacheKey(Long userId, Long submissionId) {
        return String.format("%s%d:submission:%d", SUBMISSION_RESULT_KEY_PREFIX, userId, submissionId);
    }

    // =====================================================================
    // CLEAR CACHE HELPERS (KHÔNG CẦN traceId)
    // =====================================================================

    public void clearLevelCache(Long levelId) {
        String key = buildLevelDetailsCacheKey(levelId);
        delete(key);
    }

    public void clearLevelListCache() {
        deletePattern("levels:all:*");
        deletePattern("levels:published:*");
        log.debug("Cleared all level list caches");
    }

    public void clearCacheForChallenge(Long challengeId) {
        deletePattern(SECTIONS_CHALLENGE_KEY_PREFIX + challengeId + ":*");
        deletePattern(SECTIONS_PUBLIC_CHALLENGE_KEY_PREFIX + challengeId + ":*");
        deletePattern(SUBMISSIONS_CHALLENGE_KEY_PREFIX + challengeId + ":*");
        log.debug("Cleared cache for challengeId: {}", challengeId);
    }

    public void clearSubmissionsCacheForChallenge(Long challengeId) {
        deletePattern(SUBMISSIONS_CHALLENGE_KEY_PREFIX + challengeId + ":*");
        log.debug("Cleared submissions cache for challenge: {}", challengeId);
    }
    public void clearSubmissionCache(Long userId, Long submissionId) {
        try {
            String resultKey = buildSubmissionResultCacheKey(userId, submissionId);

            delete(resultKey);
            log.debug("Cleared submission caches for userId: {}, submissionId: {}", userId, submissionId);
        } catch (Exception e) {
            log.warn("Failed to clear submission cache: userId={}, submissionId={}", userId, submissionId, e);
        }
    }

}