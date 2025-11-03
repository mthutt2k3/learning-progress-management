package com.learning.progress.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
    public static final String SECTION_KEY_PREFIX = "section:";
    public static final String SECTIONS_CHALLENGE_KEY_PREFIX = "sections:challenge:";
    public static final String SECTIONS_PUBLIC_CHALLENGE_KEY_PREFIX = "sections:public:challenge:";
    public static final String QUESTION_KEY_PREFIX = "question:";
    public static final String QUESTIONS_SECTION_KEY_PREFIX = "questions:section:";
    public static final String SUBMISSIONS_CHALLENGE_KEY_PREFIX = "submissions:challenge:";
    public static final String SUBMISSION_RESULT_KEY_PREFIX = "submission:result:user:";
    public static final String SUBMISSION_DRAFT_KEY_PREFIX = "submission:draft:user:";

    // LEVEL CACHE (MỚI)
    public static final long LEVEL_TTL_MINUTES = 15;
    public static final long LEVEL_LIST_TTL_MINUTES = 10;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // === LẤY traceId TỪ MDC ===
    public String getCurrentTraceId() {
        return MDC.get(Const.LOGGING.TRACE_ID);
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

    public String buildSectionCacheKey(Long sectionId) {
        return SECTION_KEY_PREFIX + sectionId;
    }

    // QUESTION
    public String buildQuestionCacheKey(Long questionId) {
        return QUESTION_KEY_PREFIX + questionId;
    }

    public String buildQuestionsBySectionCacheKey(Long sectionId) {
        return QUESTIONS_SECTION_KEY_PREFIX + sectionId;
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

    public void clearCacheForSection(Long sectionId, Long challengeId) {
        if (sectionId != null) {
            delete(SECTION_KEY_PREFIX + sectionId);
            delete(QUESTIONS_SECTION_KEY_PREFIX + sectionId);
        }
        if (challengeId != null) {
            deletePattern(SECTIONS_CHALLENGE_KEY_PREFIX + challengeId + ":*");
            deletePattern(SECTIONS_PUBLIC_CHALLENGE_KEY_PREFIX + challengeId + ":*");
        }
        log.debug("Cleared cache for sectionId: {}, challengeId: {}", sectionId, challengeId);
    }

    public void clearCacheForQuestion(Long questionId, Long sectionId, Long challengeId) {
        if (questionId != null) {
            delete(QUESTION_KEY_PREFIX + questionId);
        }
        clearCacheForSection(sectionId, challengeId);
    }

    public void clearSubmissionsCacheForChallenge(Long challengeId) {
        deletePattern(SUBMISSIONS_CHALLENGE_KEY_PREFIX + challengeId + ":*");
        log.debug("Cleared submissions cache for challenge: {}", challengeId);
    }

    public String buildDraftSubmissionCacheKey(Long userId, Long submissionId) {
        return String.format("%s%d:submission:%d", SUBMISSION_DRAFT_KEY_PREFIX, userId, submissionId);
    }
}