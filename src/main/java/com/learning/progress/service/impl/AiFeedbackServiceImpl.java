package com.learning.progress.service.impl;

import com.learning.progress.dto.ai.GradingWritingRequest;
import com.learning.progress.dto.ai.GradingWritingResponse;
import com.learning.progress.entity.SubmissionQuestion;
import com.learning.progress.exception.ApiException;
import com.learning.progress.service.AiFeedbackService;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.dto.ai.*;
import com.learning.progress.entity.*;
import com.learning.progress.repository.SubmissionQuestionRepository;
import com.learning.progress.util.TraceUtil;
import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import java.util.List;

@Service
@Slf4j
public class AiFeedbackServiceImpl implements AiFeedbackService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SubmissionQuestionRepository submissionQuestionRepository;

    @Value("${azure.speech.key}")
    private String speechKey;

    @Value("${azure.speech.region}")
    private String speechRegion;

    private final OpenAiServiceImpl openAiServiceImpl;
    private static final String SYSTEM_ROLE_JSON_INSTRUCTION =
            "You are an expert English teacher. Return ONLY valid JSON (no markdown, no comments, no extra text). " +
                    "Do NOT include trailing commas or non-standard JSON syntax.";

    public AiFeedbackServiceImpl(OpenAiServiceImpl openAiServiceImpl, SubmissionQuestionRepository submissionQuestionRepository) {
        this.submissionQuestionRepository = submissionQuestionRepository;
        this.openAiServiceImpl = openAiServiceImpl;
    }

    @Override
    @Transactional(readOnly = true)
    public GradingWritingResponse gradeWriting(GradingWritingRequest request) {
        log.info("Starting AI grading for submissionQuestionId: {}", request.getSubmissionQuestionId());

        // 1. Load submission question
        SubmissionQuestion submissionQuestion = submissionQuestionRepository
                .findById(request.getSubmissionQuestionId())
                .orElseThrow(() -> new ApiException("Submission question not found", HttpStatus.NOT_FOUND.value()));

        // 2. Extract student's writing
        String studentWriting = extractWritingFromSubmission(submissionQuestion.getSubmissionContentJson());
        if (studentWriting == null || studentWriting.trim().isEmpty()) {
            throw new ApiException("No writing content found in submission", HttpStatus.BAD_REQUEST.value());
        }

        // 3. Load context
        Question question = submissionQuestion.getQuestion();
        ChallengeSection section = question.getSection();
        DailyChallenge challenge = section.getChallenge();
        OpenAiServiceImpl.ChallengeContext context = openAiServiceImpl.eagerLoadChallengeContext(challenge);

        // 4. Build prompt
        String prompt = buildWritingGradingPrompt(context, question.getQuestionText(), studentWriting);

        // 5. Call OpenAI with retry
        String aiResponse = null;
        int maxRetries = 3;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Calling OpenAI (attempt {}/{}) ...", attempt, maxRetries);
                aiResponse = openAiServiceImpl.callOpenAIForFeedback(prompt);
                break; // ✅ success → thoát vòng lặp
            } catch (Exception e) {
                lastException = e;
                log.warn("OpenAI call failed on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        long backoff = 1000L * attempt; // tăng delay dần: 1s, 2s, 3s
                        log.info("Retrying after {} ms...", backoff);
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    log.error("All {} retry attempts failed.", maxRetries);
                }
            }
        }

        if (aiResponse == null) {
            throw new ApiException("Failed to get AI response after " + maxRetries + " attempts: "
                    + (lastException != null ? lastException.getMessage() : "unknown error"),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        // 6. Parse response
        GradingWritingResponse result = parseGradingResponse(aiResponse, studentWriting);
        log.info("Successfully graded writing. Overall score: {}", result.getSuggestedScore());

        return result;
    }


    // Helper method: Extract writing text from JSON
    private String extractWritingFromSubmission(Map<String, Object> submissionContentJson) {
        try {
            Object dataObj = submissionContentJson.get("data");
            if (dataObj instanceof List<?> dataList && !dataList.isEmpty()) {
                Object firstItem = dataList.get(0);
                if (firstItem instanceof Map<?, ?> firstMap) {
                    Object value = firstMap.get("value");
                    return value != null ? value.toString() : null;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to extract writing: {}", e.getMessage());
            throw new RuntimeException("Invalid submission content format", e);
        }
    }

    // Build grading prompt
    private String buildWritingGradingPrompt(
            OpenAiServiceImpl.ChallengeContext context,
            String questionText,
            String studentWriting) {

        StringBuilder prompt = new StringBuilder();

        // System role ensures JSON-only responses and basic restrictions
        prompt.append(SYSTEM_ROLE_JSON_INSTRUCTION).append("\n\n");

        // Enforce Vietnamese for feedback content (values) while preserving JSON keys (English)
        prompt.append("IMPORTANT: All human-readable feedback content (the values of ")
                .append("`overallFeedback`, each comment's `commentText` and `correction`) ")
                .append("MUST be written in Vietnamese. Do NOT translate or change JSON field names (they must remain in English). ")
                .append("Return ONLY valid JSON, no markdown, no explanations, no extra text.\n\n");

        prompt.append("You are an experienced English writing teacher. Provide focused, high-value feedback only.\n\n");

        prompt.append("IMPORTANT: All feedback content MUST be in Vietnamese with HTML formatting in one line(do not use \\n to break the line).\n");
        prompt.append("Use these HTML tags for formatting:\n");
        prompt.append("- <h3><strong>Header</strong></h3> for section headers\n");
        prompt.append("- <p>paragraph text</p> for paragraphs\n");
        prompt.append("- <ul><li>item</li></ul> for bullet lists\n");
        prompt.append("- <ol><li>item</li></ol> for numbered lists\n");
        prompt.append("- <strong>text</strong> for bold emphasis\n");
        prompt.append("- <em>text</em> for italic emphasis\n");
        prompt.append("- Multiple spaces/newlines will be preserved as-is\n\n");

        prompt.append("Context: Chapter: ").append(context.classChapterName)
                .append(" | Level: ").append(context.studentLevel).append("\n\n");

        prompt.append("TASK: Read the writing below and produce a JSON object containing:\n");
        prompt.append(" - overallFeedback: 100-200 words in Vietnamese summarizing strengths, key weaknesses, and a 2-3 step study plan.\n");
        prompt.append(" - suggestedScore: numeric (0.0 - 10.0).\n");
        prompt.append(" - criteriaFeedback: object with 4 fields, each containing score (0-9) and feedback (in Vietnamese, HTML format, 100-200 words):\n");
        prompt.append("     * taskResponse\n");
        prompt.append("     * cohesionCoherence\n");
        prompt.append("     * lexicalResource\n");
        prompt.append("     * grammaticalRangeAccuracy\n");
        prompt.append(" - comments: 7-50 items, prioritized by impact on communication. Each comment must include:\n");
        prompt.append("     startIndex (0-based char index), endIndex (exclusive),\n");
        prompt.append("     commentText (15-80 characters, in Vietnamese),\n");
        prompt.append("     severity (one of: error|warning|suggestion),\n");
        prompt.append("     category (one of: grammar|vocabulary|cohesion|task|other),\n");
        prompt.append("     correction (concise suggested correction or rephrase, in Vietnamese).\n\n");

        prompt.append("GUIDELINES:\n");
        prompt.append("- Prioritize meaning-impacting issues (unclear sentences, wrong tense affecting meaning, wrong word choice, omitted information).\n");
        prompt.append("- Avoid trivial punctuation/capitalization comments unless frequent or harming readability.\n");
        prompt.append("- Provide a one-line correction or alternative phrasing for each comment (in Vietnamese).\n");
        prompt.append("- Indices must be 0-based character positions matching the STUDENT'S WRITING section below.\n");
        prompt.append("- Maintain neutral, constructive tone.\n\n");

        prompt.append("WRITING TASK:\n").append(questionText).append("\n\n");
        prompt.append("STUDENT'S WRITING:\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        prompt.append(studentWriting).append("\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

//        String ageInstructions = openAiServiceImpl.getAgeBasedLevelInstructions(age);
//        prompt.append(ageInstructions).append("\n\n");

        prompt.append("OUTPUT (exact JSON only, no extra text). Note: ALL textual values must be in Vietnamese:\n");
        prompt.append("{\n");
        prompt.append("  \"overallFeedback\": \"Tóm tắt ngắn gọn bằng tiếng Việt: ...\",\n");
        prompt.append("  \"suggestedScore\": 7.5,\n");
        prompt.append("  \"criteriaFeedback\": {\n");
        prompt.append("    \"taskResponse\": {\"score\": 7.0, \"feedback\": \"...\"},\n");
        prompt.append("    \"cohesionCoherence\": {\"score\": 7.5, \"feedback\": \"...\"},\n");
        prompt.append("    \"lexicalResource\": {\"score\": 7.0, \"feedback\": \"...\"},\n");
        prompt.append("    \"grammaticalRangeAccuracy\": {\"score\": 6.5, \"feedback\": \"...\"}\n");
        prompt.append("  },\n");
        prompt.append("  \"comments\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"startIndex\": 0,\n");
        prompt.append("      \"endIndex\": 5,\n");
        prompt.append("      \"commentText\": \"Nhận xét ngắn (tiếng Việt, 15-80 ký tự)\",\n");
        prompt.append("      \"severity\": \"error\",\n");
        prompt.append("      \"category\": \"grammar\",\n");
        prompt.append("      \"correction\": \"Sửa ngắn gọn bằng tiếng Việt\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");

        return prompt.toString();
    }


    private GradingWritingResponse parseGradingResponse(String jsonResponse, String studentWriting) {
        try {
            String cleaned = openAiServiceImpl.cleanJsonResponse(jsonResponse);
            JsonNode root = objectMapper.readTree(cleaned);

            String overallFeedback = root.hasNonNull("overallFeedback") ? root.get("overallFeedback").asText().trim() : "";
            double suggestedScore = 0.0;
            if (root.hasNonNull("suggestedScore")) {
                suggestedScore = root.get("suggestedScore").asDouble(0.0);
            }
            // clamp
            if (Double.isNaN(suggestedScore) || suggestedScore < 0) suggestedScore = 0.0;
            if (suggestedScore > 10) suggestedScore = 10.0;

            // Parse criteriaFeedback
            GradingWritingResponse.CriteriaFeedback criteriaFeedback = null;
            if (root.hasNonNull("criteriaFeedback")) {
                JsonNode cfNode = root.get("criteriaFeedback");
                criteriaFeedback = GradingWritingResponse.CriteriaFeedback.builder()
                        .taskResponse(parseCriteriaScore(cfNode.get("taskResponse")))
                        .cohesionCoherence(parseCriteriaScore(cfNode.get("cohesionCoherence")))
                        .lexicalResource(parseCriteriaScore(cfNode.get("lexicalResource")))
                        .grammaticalRangeAccuracy(parseCriteriaScore(cfNode.get("grammaticalRangeAccuracy")))
                        .build();
            }

            List<WritingComment> comments = new ArrayList<>();
            JsonNode commentsNode = root.get("comments");
            int textLength = studentWriting != null ? studentWriting.length() : 0;

            if (commentsNode != null && commentsNode.isArray()) {
                for (JsonNode commentNode : commentsNode) {
                    try {
                        if (!commentNode.hasNonNull("startIndex") || !commentNode.hasNonNull("endIndex")) continue;
                        int start = commentNode.get("startIndex").asInt(-1);
                        int end = commentNode.get("endIndex").asInt(-1);
                        if (start < 0 || end <= start || start >= textLength) continue;
                        if (end > textLength) end = textLength;

                        String commentText = commentNode.hasNonNull("commentText") ? commentNode.get("commentText").asText().trim() : "";
                        if (commentText.isEmpty()) continue;

                        // Avoid trivial short comments
                        String lower = commentText.toLowerCase();
                        if (commentText.length() < 12 && !lower.contains("error") && !lower.contains("use") && !lower.contains("replace")) {
                            continue;
                        }
                        // Build id + timestamp
                        String id = "fb-" + UUID.randomUUID();
                        String isoTs = Instant.now().toString();

                        WritingComment wc = WritingComment.builder()
                                .id(id)
                                .comment(commentText)
                                .startIndex(start)
                                .endIndex(end)
                                .timestamp(isoTs)
                                .build();

                        comments.add(wc);

                    } catch (Exception ex) {
                        log.debug("Skipping malformed comment node: {}", ex.getMessage());
                    }
                }
            }

            // Prioritize comments
            int minKeep = 7;
            int maxKeep = 20;
            if (comments.size() < minKeep) {
                // if AI returned fewer, keep all
            } else if (comments.size() > maxKeep) {
                comments = comments.subList(0, maxKeep);
            }

            return GradingWritingResponse.builder()
                    .overallFeedback(overallFeedback.replaceAll("\\r?\\n", " "))
                    .suggestedScore(suggestedScore)
                    .criteriaFeedback(criteriaFeedback)
                    .comments(comments)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse grading response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse AI grading response: " + e.getMessage(), e);
        }
    }

    // Helper method để parse từng criteria score
    private GradingWritingResponse.CriteriaScore parseCriteriaScore(JsonNode node) {
        if (node == null || node.isNull()) {
            return GradingWritingResponse.CriteriaScore.builder()
                    .score(0.0)
                    .feedback("")
                    .build();
        }

        double score = node.hasNonNull("score") ? node.get("score").asDouble(0.0) : 0.0;
        if (score < 0) score = 0.0;
        if (score > 10) score = 10.0;

        String feedback = node.hasNonNull("feedback") ? node.get("feedback").asText().trim() : "";

        return GradingWritingResponse.CriteriaScore.builder()
                .score(score)
                .feedback(feedback)
                .build();
    }

    // Thay thế method assessPronunciation() trong AiFeedbackServiceImpl.java

    @Override
    public PronunciationAssessmentResponse assessPronunciation(PronunciationAssessmentRequest request) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Starting pronunciation assessment", traceId);

        if (request.getAudioUrl() == null || request.getAudioUrl().trim().isEmpty()) {
            throw new ApiException("Audio URL is required", HttpStatus.BAD_REQUEST.value());
        }

        // Retry logic - max 3 attempts
        PronunciationAssessmentResponse result = null;
        Exception lastException = null;
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info("[{}] Attempt {}/{} for pronunciation assessment", traceId, attempt, maxAttempts);

                File tempAudioFile = downloadFromBlobUrl(request.getAudioUrl());

                try {
                    File wavFile = ensureWavFormat(tempAudioFile);
                    boolean needsCleanup = !wavFile.equals(tempAudioFile);

                    try {
                        boolean hasReferenceText = request.getReferenceText() != null
                                && !request.getReferenceText().trim().isEmpty();

                        PronunciationAssessmentResponse response;

                        if (request.getReferenceText() != null && !request.getReferenceText().trim().isEmpty()) {
                            validateEnglishOnly(request.getReferenceText());
                        }

                        if (hasReferenceText) {
                            // Use continuous recognition WITH pronunciation assessment
                            log.info("[{}] Scripted assessment with reference text", traceId);
                            response = assessWithReferenceTextContinuous(wavFile, request);
                        } else {
                            // Use continuous recognition WITHOUT pronunciation assessment
                            log.info("[{}] Free-form assessment", traceId);
                            response = assessFreeForm(wavFile);
                        }

                        log.info("[{}] Assessment completed on attempt {}. Score: {}",
                                traceId, attempt, response.getPronunciationScore());
                        response.setFeedback(response.getFeedback().replaceAll("\\r?\\n", " "));
                        response.setPronunciationScore(roundToOneDecimal(response.getPronunciationScore() / 10));
                        response.setAccuracyScore(roundToOneDecimal(response.getAccuracyScore() / 10));
                        response.setFluencyScore(roundToOneDecimal(response.getFluencyScore() / 10));
                        response.setCompletenessScore(roundToOneDecimal(response.getCompletenessScore() / 10));
                        response.setProsodyScore(roundToOneDecimal(response.getProsodyScore() / 10));
                        return response;

                    } finally {
                        if (needsCleanup && wavFile.exists()) {
                            wavFile.delete();
                        }
                    }
                } finally {
                    if (tempAudioFile.exists()) {
                        tempAudioFile.delete();
                    }
                }

            } catch (ApiException e) {
                // Business logic errors - don't retry
                log.error("[{}] Non-retryable ApiException: {}", traceId, e.getMessage());
                throw e;

            } catch (java.io.IOException e) {
                // Network/IO errors - retry
                lastException = e;
                log.warn("[{}] IO error on attempt {}/{}: {}", traceId, attempt, maxAttempts, e.getMessage());

                if (attempt < maxAttempts) {
                    log.info("[{}] Retrying pronunciation assessment after {}s delay...", traceId, attempt);
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ApiException("Assessment retry interrupted", HttpStatus.INTERNAL_SERVER_ERROR.value());
                    }
                }

            } catch (java.util.concurrent.TimeoutException e) {
                // Timeout errors - retry
                lastException = e;
                log.warn("[{}] Timeout on attempt {}/{}: {}", traceId, attempt, maxAttempts, e.getMessage());

                if (attempt < maxAttempts) {
                    log.info("[{}] Retrying pronunciation assessment after {}s delay...", traceId, attempt);
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ApiException("Assessment retry interrupted", HttpStatus.INTERNAL_SERVER_ERROR.value());
                    }
                }

            }catch (RuntimeException e) {
                // Check if it's a retryable runtime exception
                String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                boolean isRetryable = message.contains("timeout")
                        || message.contains("network")
                        || message.contains("connection")
                        || message.contains("parse")
                        || message.contains("json")
                        || message.contains("recognition failed")
                        || message.contains("speech service");

                if (isRetryable) {
                    lastException = e;
                    log.warn("[{}] Retryable runtime error on attempt {}/{}: {}",
                            traceId, attempt, maxAttempts, e.getMessage());

                    if (attempt < maxAttempts) {
                        log.info("[{}] Retrying pronunciation assessment after {}s delay...", traceId, attempt);
                        try {
                            Thread.sleep(1000L * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new ApiException("Assessment retry interrupted", HttpStatus.INTERNAL_SERVER_ERROR.value());
                        }
                        continue; // Retry
                    }
                } else {
                    // Non-retryable runtime exception
                    log.error("[{}] Non-retryable runtime error: {}", traceId, e.getMessage(), e);
                    throw new ApiException("Failed to assess pronunciation: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value());
                }

            } catch (Exception e) {
                // Other exceptions - check if retryable by message
                String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                boolean isRetryable = message.contains("timeout")
                        || message.contains("network")
                        || message.contains("connection");

                if (isRetryable) {
                    lastException = e;
                    log.warn("[{}] Retryable exception on attempt {}/{}: {}",
                            traceId, attempt, maxAttempts, e.getMessage());

                    if (attempt < maxAttempts) {
                        log.info("[{}] Retrying pronunciation assessment after {}s delay...", traceId, attempt);
                        try {
                            Thread.sleep(1000L * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new ApiException("Assessment retry interrupted", HttpStatus.INTERNAL_SERVER_ERROR.value());
                        }
                        continue; // Retry
                    }
                } else {
                    // Non-retryable exception
                    log.error("[{}] Non-retryable error: {}", traceId, e.getMessage(), e);
                    throw new ApiException("Failed to assess pronunciation: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value());
                }
            }
        }

        // All attempts failed
        log.error("[{}] All {} attempts for pronunciation assessment failed", traceId, maxAttempts);
        throw new ApiException(
                String.format("Failed to assess pronunciation after %d attempts: %s",
                        maxAttempts,
                        lastException != null ? lastException.getMessage() : "Unknown error"),
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * Assess with reference text using CONTINUOUS recognition
     */
    private PronunciationAssessmentResponse assessWithReferenceTextContinuous(
            File wavFile,
            PronunciationAssessmentRequest request) throws Exception {

        SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
        speechConfig.setSpeechRecognitionLanguage("en-US");

        AudioConfig audioConfig = AudioConfig.fromWavFileInput(wavFile.getAbsolutePath());

        // Create pronunciation assessment config
        PronunciationAssessmentConfig pronConfig = new PronunciationAssessmentConfig(
                request.getReferenceText(),
                mapGradingSystem(request.getGradingSystem()),
                mapGranularity(request.getGranularity()),
                request.getEnableMiscue() != null ? request.getEnableMiscue() : true
        );

        if (request.getEnableProsody() != null && request.getEnableProsody()) {
            pronConfig.enableProsodyAssessment();
        }

        // Create recognizer
        SpeechRecognizer recognizer = new SpeechRecognizer(speechConfig, audioConfig);

        // Apply pronunciation assessment config
        pronConfig.applyTo(recognizer);

        // Perform continuous recognition with pronunciation assessment
        ContinuousPronunciationResult result = performContinuousPronunciationRecognition(recognizer);

        recognizer.close();
        audioConfig.close();
        speechConfig.close();

        if (result.getFullText().isEmpty()) {
            throw new ApiException("No speech could be recognized from the audio file",
                    HttpStatus.BAD_REQUEST.value());
        }

        // Aggregate pronunciation assessment results
        return aggregatePronunciationResults(
                result,
                request.getReferenceText(),
                request.getEnableMiscue()
        );
    }

    /**
     * Perform continuous recognition WITH pronunciation assessment
     */
    private ContinuousPronunciationResult performContinuousPronunciationRecognition(
            SpeechRecognizer recognizer) throws Exception {

        StringBuilder fullText = new StringBuilder();
        List<String> jsonResults = new ArrayList<>();
        List<PronunciationScores> allScores = new ArrayList<>();

        CountDownLatch stopLatch = new CountDownLatch(1);
        AtomicBoolean hasError = new AtomicBoolean(false);
        AtomicReference<String> errorMessage = new AtomicReference<>("");

        // Handle recognized events
        recognizer.recognized.addEventListener((s, e) -> {
            if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                String text = e.getResult().getText();
                if (text != null && !text.trim().isEmpty()) {
                    fullText.append(text).append(" ");

                    // Get pronunciation assessment result for this segment
                    try {
                        PronunciationAssessmentResult pronResult =
                                PronunciationAssessmentResult.fromResult(e.getResult());

                        PronunciationScores scores = PronunciationScores.builder()
                                .pronunciationScore(pronResult.getPronunciationScore())
                                .accuracyScore(pronResult.getAccuracyScore())
                                .fluencyScore(pronResult.getFluencyScore())
                                .completenessScore(pronResult.getCompletenessScore())
                                .prosodyScore(getProsodyScore(pronResult))
                                .wordCount(countWords(text))
                                .build();

                        allScores.add(scores);

                    } catch (Exception ex) {
                        log.debug("Could not get pronunciation scores for segment: {}", ex.getMessage());
                    }

                    // Collect JSON for word-level details
                    String json = e.getResult().getProperties()
                            .getProperty(PropertyId.SpeechServiceResponse_JsonResult);
                    if (json != null && !json.isEmpty()) {
                        jsonResults.add(json);
                    }

                    log.debug("Recognized: {}", text);
                }
            }
        });

        recognizer.sessionStopped.addEventListener((s, e) -> {
            log.debug("Session stopped");
            stopLatch.countDown();
        });

        recognizer.canceled.addEventListener((s, e) -> {
            if (e.getReason() == CancellationReason.Error) {
                log.error("Recognition error: {}", e.getErrorDetails());
                hasError.set(true);
                errorMessage.set(e.getErrorDetails());
            }
            stopLatch.countDown();
        });

        // Start recognition
        recognizer.startContinuousRecognitionAsync().get();

        // Wait for completion (max 5 minutes)
        boolean completed = stopLatch.await(5, TimeUnit.MINUTES);

        recognizer.stopContinuousRecognitionAsync().get();

        if (!completed) {
            throw new ApiException("Recognition timeout after 5 minutes",
                    HttpStatus.REQUEST_TIMEOUT.value());
        }

        if (hasError.get()) {
            throw new ApiException("Recognition failed: " + errorMessage.get(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return ContinuousPronunciationResult.builder()
                .fullText(fullText.toString().trim())
                .allJsonResults(jsonResults)
                .allScores(allScores)
                .build();
    }

    /**
     * Aggregate pronunciation assessment results from multiple segments
     */
    private PronunciationAssessmentResponse aggregatePronunciationResults(
            ContinuousPronunciationResult result,
            String referenceText,
            Boolean enableMiscue) {

        // Calculate weighted average scores
        double totalPronunciation = 0;
        double totalAccuracy = 0;
        double totalFluency = 0;
        double totalCompleteness = 0;
        double totalProsody = 0;
        int prosodyCount = 0;
        int totalWords = 0;

        for (PronunciationScores scores : result.getAllScores()) {
            int wordCount = scores.getWordCount();
            totalPronunciation += scores.getPronunciationScore() * wordCount;
            totalAccuracy += scores.getAccuracyScore() * wordCount;
            totalFluency += scores.getFluencyScore() * wordCount;
            totalCompleteness += scores.getCompletenessScore() * wordCount;

            if (scores.getProsodyScore() != null) {
                totalProsody += scores.getProsodyScore() * wordCount;
                prosodyCount += wordCount;
            }

            totalWords += wordCount;
        }

        double avgPronunciation = totalWords > 0 ? totalPronunciation / totalWords : 0;
        double avgAccuracy = totalWords > 0 ? totalAccuracy / totalWords : 0;
        double avgFluency = totalWords > 0 ? totalFluency / totalWords : 0;
        double avgCompleteness = totalWords > 0 ? totalCompleteness / totalWords : 0;
        Double avgProsody = prosodyCount > 0 ? totalProsody / prosodyCount : null;

        // Parse all word assessments
        List<PronunciationAssessmentResponse.WordAssessment> allWords = new ArrayList<>();
        for (String jsonResult : result.getAllJsonResults()) {
            List<PronunciationAssessmentResponse.WordAssessment> words =
                    parseWordAssessments(jsonResult, false, null);
            allWords.addAll(words);
        }

        // Detect miscues if enabled
        if (enableMiscue != null && enableMiscue && !allWords.isEmpty()) {
            allWords = detectMiscues(allWords, referenceText);
        }

        // Generate AI feedback
        String feedback = generateFeedback(
                avgPronunciation,
                avgAccuracy,
                avgFluency,
                avgCompleteness,
                avgProsody,
                allWords
        );

        return PronunciationAssessmentResponse.builder()
                .pronunciationScore(avgPronunciation)
                .accuracyScore(avgAccuracy)
                .fluencyScore(avgFluency)
                .completenessScore(avgCompleteness)
                .prosodyScore(avgProsody)
                .recognizedText(result.getFullText())
                .referenceText(referenceText)
                .feedback(feedback)
                .build();
    }

    /**
     * Assess free-form (no reference text)
     */
    private PronunciationAssessmentResponse assessFreeForm(File wavFile) throws Exception {
        SpeechAnalysisResult analysis = performDetailedSpeechRecognition(wavFile);
        if (analysis.getRecognizedText() != null && !analysis.getRecognizedText().trim().isEmpty()) {
            validateEnglishOnly(analysis.getRecognizedText());
        }
        return generateAIAssessment(analysis);
    }

    /**
     * Get prosody score safely
     */
    private Double getProsodyScore(PronunciationAssessmentResult pronResult) {
        try {
            return pronResult.getProsodyScore();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Count words in text
     */
    private int countWords(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        return text.trim().split("\\s+").length;
    }

    // Supporting classes
    @Data
    @Builder
    private static class ContinuousPronunciationResult {
        private String fullText;
        private List<String> allJsonResults;
        private List<PronunciationScores> allScores;
    }

    @Data
    @Builder
    private static class PronunciationScores {
        private double pronunciationScore;
        private double accuracyScore;
        private double fluencyScore;
        private double completenessScore;
        private Double prosodyScore;
        private int wordCount;
    }

    /**
     * Download audio file from Azure Blob URL
     */
    private File downloadFromBlobUrl(String blobUrl) throws IOException {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            String filename = "downloaded_" + UUID.randomUUID() + ".tmp";
            File tempFile = new File(tempDir, filename);

            // Download from URL
            URL url = new URL(blobUrl);
            try (InputStream in = url.openStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            log.debug("Downloaded audio from blob: {}", tempFile.getAbsolutePath());
            return tempFile;

        } catch (Exception e) {
            log.error("Failed to download from blob URL: {}", e.getMessage());
            throw new IOException("Failed to download audio from blob URL", e);
        }
    }

    private File ensureWavFormat(File audioFile) throws IOException {
        // Check if already WAV
        if (isWavFile(audioFile)) {
            log.debug("File is already WAV format");
            return audioFile;
        }

        // Convert using JAVE2
        log.info("Converting MP3 to WAV using JAVE2...");

        String tempDir = System.getProperty("java.io.tmpdir");
        File wavFile = new File(tempDir, "converted_" + UUID.randomUUID() + ".wav");

        try {
            // Audio attributes
            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("pcm_s16le");
            audio.setBitRate(256000);
            audio.setChannels(1);
            audio.setSamplingRate(16000);

            // Encoding attributes
            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat("wav");
            attrs.setAudioAttributes(audio);

            // Encode
            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(audioFile), wavFile, attrs);

            log.info("Converted to WAV successfully");
            return wavFile;

        } catch (EncoderException e) {
            if (wavFile.exists()) wavFile.delete();
            throw new IOException("Failed to convert audio: " + e.getMessage(), e);
        }
    }

    /**
     * Check if file is WAV
     */
    private boolean isWavFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[12];
            if (fis.read(header) < 12) return false;

            return header[0] == 'R' && header[1] == 'I' &&
                    header[2] == 'F' && header[3] == 'F' &&
                    header[8] == 'W' && header[9] == 'A' &&
                    header[10] == 'V' && header[11] == 'E';
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Map grading system string to enum
     */
    private PronunciationAssessmentGradingSystem mapGradingSystem(String gradingSystem) {
        if ("FivePoint".equalsIgnoreCase(gradingSystem)) {
            return PronunciationAssessmentGradingSystem.FivePoint;
        }
        return PronunciationAssessmentGradingSystem.HundredMark;
    }

    /**
     * Map granularity string to enum
     */
    private PronunciationAssessmentGranularity mapGranularity(String granularity) {
        switch (granularity.toLowerCase()) {
            case "word":
                return PronunciationAssessmentGranularity.Word;
            case "fulltext":
                return PronunciationAssessmentGranularity.FullText;
            default:
                return PronunciationAssessmentGranularity.Phoneme;
        }
    }

    /**
     * Parse word-level assessment from JSON response
     */
    private List<PronunciationAssessmentResponse.WordAssessment> parseWordAssessments(
            String jsonResult,
            boolean enableMiscue,
            String referenceText) {

        List<PronunciationAssessmentResponse.WordAssessment> wordAssessments = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResult);
            JsonNode nBestArray = rootNode.get("NBest");

            if (nBestArray != null && nBestArray.isArray() && nBestArray.size() > 0) {
                JsonNode nBestItem = nBestArray.get(0);
                JsonNode wordsArray = nBestItem.get("Words");

                if (wordsArray != null && wordsArray.isArray()) {
                    int position = 0;
                    for (JsonNode wordNode : wordsArray) {
                        String word = wordNode.get("Word").asText();
                        Long duration = wordNode.has("Duration") ? wordNode.get("Duration").asLong() : null;

                        JsonNode pronAssessment = wordNode.get("PronunciationAssessment");
                        double wordAccuracy = pronAssessment.get("AccuracyScore").asDouble();
                        String errorType = pronAssessment.get("ErrorType").asText();

                        wordAssessments.add(
                                PronunciationAssessmentResponse.WordAssessment.builder()
                                        .word(word)
                                        .accuracyScore(wordAccuracy)
                                        .errorType(errorType)
                                        .position(position++)
                                        .duration(duration)
                                        .build()
                        );
                    }
                }
            }

            // If miscue detection enabled, compare with reference text
            if (enableMiscue && !wordAssessments.isEmpty()) {
                wordAssessments = detectMiscues(wordAssessments, referenceText);
            }

        } catch (Exception e) {
            log.error("Error parsing word assessments: {}", e.getMessage(), e);
        }

        return wordAssessments;
    }

    /**
     * Detect omissions and insertions by comparing with reference text
     */
    private List<PronunciationAssessmentResponse.WordAssessment> detectMiscues(
            List<PronunciationAssessmentResponse.WordAssessment> recognizedWords,
            String referenceText) {

        // Parse reference words
        String[] refWords = referenceText.toLowerCase().split("\\s+");
        List<String> refWordsList = new ArrayList<>();
        for (String word : refWords) {
            // Remove punctuation
            word = word.replaceAll("^\\p{Punct}+|\\p{Punct}+$", "");
            if (!word.isEmpty()) {
                refWordsList.add(word);
            }
        }

        // Extract recognized word strings
        List<String> recWordsList = recognizedWords.stream()
                .map(w -> w.getWord().toLowerCase().replaceAll("^\\p{Punct}+|\\p{Punct}+$", ""))
                .collect(Collectors.toList());

        // Compare and mark omissions/insertions (simplified diff algorithm)
        List<PronunciationAssessmentResponse.WordAssessment> finalWords = new ArrayList<>();

        int refIndex = 0;
        int recIndex = 0;

        while (refIndex < refWordsList.size() || recIndex < recWordsList.size()) {
            if (refIndex >= refWordsList.size()) {
                // Extra recognized words (insertions)
                PronunciationAssessmentResponse.WordAssessment word = recognizedWords.get(recIndex);
                word.setErrorType("Insertion");
                finalWords.add(word);
                recIndex++;
            } else if (recIndex >= recWordsList.size()) {
                // Missing words (omissions)
                finalWords.add(
                        PronunciationAssessmentResponse.WordAssessment.builder()
                                .word(refWordsList.get(refIndex))
                                .accuracyScore(0.0)
                                .errorType("Omission")
                                .position(finalWords.size())
                                .build()
                );
                refIndex++;
            } else if (refWordsList.get(refIndex).equals(recWordsList.get(recIndex))) {
                // Words match
                finalWords.add(recognizedWords.get(recIndex));
                refIndex++;
                recIndex++;
            } else {
                // Mismatch - check if it's omission or insertion
                // (simplified logic - could use more sophisticated diff algorithm)
                if (recIndex + 1 < recWordsList.size() &&
                        refWordsList.get(refIndex).equals(recWordsList.get(recIndex + 1))) {
                    // Likely insertion
                    PronunciationAssessmentResponse.WordAssessment word = recognizedWords.get(recIndex);
                    word.setErrorType("Insertion");
                    finalWords.add(word);
                    recIndex++;
                } else {
                    // Likely omission
                    finalWords.add(
                            PronunciationAssessmentResponse.WordAssessment.builder()
                                    .word(refWordsList.get(refIndex))
                                    .accuracyScore(0.0)
                                    .errorType("Omission")
                                    .position(finalWords.size())
                                    .build()
                    );
                    refIndex++;
                }
            }
        }

        return finalWords;
    }

    /**
     * Generate detailed Vietnamese feedback using AI
     */
    private String generateFeedback(
            double pronunciationScore,
            double accuracyScore,
            double fluencyScore,
            double completenessScore,
            Double prosodyScore,
            List<PronunciationAssessmentResponse.WordAssessment> words) {

        try {
            // Build prompt for AI
            String prompt = buildAIFeedbackPrompt(
                    pronunciationScore,
                    accuracyScore,
                    fluencyScore,
                    completenessScore,
                    prosodyScore,
                    words
            );

            // Call OpenAI to generate feedback
            String aiFeedback = openAiServiceImpl.callOpenAIForFeedback(prompt);

            // Clean and return
            return aiFeedback.trim();

        } catch (Exception e) {
            log.error("Failed to generate AI feedback, using fallback: {}", e.getMessage());
            // Fallback to basic feedback if AI fails
            return generateBasicFeedback(pronunciationScore, accuracyScore, fluencyScore, completenessScore, prosodyScore, words);
        }
    }

    /**
     * Build prompt for AI to generate detailed feedback
     */
    private String buildAIFeedbackPrompt(
            double pronunciationScore,
            double accuracyScore,
            double fluencyScore,
            double completenessScore,
            Double prosodyScore,
            List<PronunciationAssessmentResponse.WordAssessment> words) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an experienced English pronunciation coach who teaches Vietnamese learners. ");
        prompt.append("Based on the assessment results below, write a short, natural feedback **in Vietnamese only** as if you were a real teacher speaking directly to the student.\n\n");

        prompt.append("ASSESSMENT SCORES:\n");
        prompt.append(String.format("- Pronunciation Score: %.1f/100\n", pronunciationScore));
        prompt.append(String.format("- Accuracy Score: %.1f/100\n", accuracyScore));
        prompt.append(String.format("- Fluency Score: %.1f/100\n", fluencyScore));
        prompt.append(String.format("- Completeness Score: %.1f/100\n", completenessScore));
        if (prosodyScore != null) {
            prompt.append(String.format("- Prosody Score: %.1f/100\n", prosodyScore));
        }
        prompt.append("\n");

        // Count errors
        long mispronunciations = words.stream()
                .filter(w -> "Mispronunciation".equals(w.getErrorType()))
                .count();
        long omissions = words.stream()
                .filter(w -> "Omission".equals(w.getErrorType()))
                .count();
        long insertions = words.stream()
                .filter(w -> "Insertion".equals(w.getErrorType()))
                .count();

        prompt.append("WORD-LEVEL ERRORS:\n");
        prompt.append(String.format("- Mispronounced words: %d\n", mispronunciations));
        prompt.append(String.format("- Omitted words: %d\n", omissions));
        prompt.append(String.format("- Inserted words: %d\n", insertions));
        prompt.append("\n");

        // List problematic words (accuracy < 70)
        List<PronunciationAssessmentResponse.WordAssessment> problematicWords = words.stream()
                .filter(w -> !"None".equals(w.getErrorType()) || w.getAccuracyScore() < 70)
                .collect(Collectors.toList());

        if (!problematicWords.isEmpty()) {
            prompt.append("PROBLEMATIC WORDS:\n");
            for (PronunciationAssessmentResponse.WordAssessment word : problematicWords) {
                prompt.append(String.format("- '%s': accuracy %.1f%%, error type: %s\n",
                        word.getWord(), word.getAccuracyScore(), word.getErrorType()));
            }
            prompt.append("\n");
        }

        prompt.append("FEEDBACK INSTRUCTIONS:\n");
        prompt.append("- Give an overall impression first (confidence, clarity, tone, etc.) with a friendly and encouraging tone.\n");
        prompt.append("- Mention what the student did well (correct sounds, clear rhythm, natural speaking, etc.).\n");
        prompt.append("- Briefly point out the main pronunciation or fluency issues (e.g., unclear endings, missing sounds, wrong stress, hesitation).\n");
        prompt.append("- For each main issue, give simple, practical advice on how to fix it (don’t over-explain).\n");
        prompt.append("- End with 2–3 short, clear tips for improvement and a motivational closing.\n\n");

        prompt.append("STYLE REQUIREMENTS:\n");
        prompt.append("- Write in natural, conversational Vietnamese.\n");
        prompt.append("- Keep it concise and to the point (around 150–200 words).\n");
        prompt.append("- Sound like a supportive teacher, not an AI.\n");
        prompt.append("- Avoid repetition and overly detailed phonetic descriptions.\n");
        prompt.append("- Use short paragraphs or bullet points for readability.\n\n");

        prompt.append("IMPORTANT: All feedback content MUST be in Vietnamese with HTML formatting without \\n.\n");
        prompt.append("Use these HTML tags for formatting:\n");
        prompt.append("- <h3><strong>Header</strong></h3> for section headers\n");
        prompt.append("- <p>paragraph text</p> for paragraphs\n");
        prompt.append("- <ul><li>item</li></ul> for bullet lists\n");
        prompt.append("- <ol><li>item</li></ol> for numbered lists\n");
        prompt.append("- <strong>text</strong> for bold emphasis\n");
        prompt.append("- <em>text</em> for italic emphasis\n");
        prompt.append("- Multiple spaces/newlines will be preserved as-is\n\n");

//        String ageInstructions = openAiServiceImpl.getAgeBasedLevelInstructions(age);
//        prompt.append(ageInstructions).append("\n\n");

        prompt.append("Now, write the feedback in Vietnamese below:\n");

        return prompt.toString();
    }

    /**
     * Generate basic fallback feedback if AI fails
     */
    private String generateBasicFeedback(
            double pronunciationScore,
            double accuracyScore,
            double fluencyScore,
            double completenessScore,
            Double prosodyScore,
            List<PronunciationAssessmentResponse.WordAssessment> words) {

        StringBuilder feedback = new StringBuilder();

        // Overall assessment
        feedback.append("📊 **Đánh giá tổng quan:**\n");
        feedback.append(String.format("Điểm phát âm tổng thể của bạn là **%.1f/100**. ", pronunciationScore));

        if (pronunciationScore >= 80) {
            feedback.append("Xuất sắc! Phát âm của bạn rất tốt.\n\n");
        } else if (pronunciationScore >= 60) {
            feedback.append("Tốt! Phát âm của bạn ở mức khá, cần cải thiện thêm một số điểm.\n\n");
        } else if (pronunciationScore >= 40) {
            feedback.append("Trung bình. Bạn cần luyện tập thêm để cải thiện phát âm.\n\n");
        } else {
            feedback.append("Cần cố gắng hơn. Hãy luyện tập thường xuyên để cải thiện phát âm.\n\n");
        }

        // Detailed scores
        feedback.append("📈 **Chi tiết điểm số:**\n");
        feedback.append(String.format("- Độ chính xác: %.1f/100\n", accuracyScore));
        feedback.append(String.format("- Độ trôi chảy: %.1f/100\n", fluencyScore));
        feedback.append(String.format("- Độ hoàn chỉnh: %.1f/100\n", completenessScore));
        if (prosodyScore != null) {
            feedback.append(String.format("- Ngữ điệu: %.1f/100\n", prosodyScore));
        }
        feedback.append("\n");

        // Word errors
        List<PronunciationAssessmentResponse.WordAssessment> errorWords = words.stream()
                .filter(w -> !"None".equals(w.getErrorType()) || w.getAccuracyScore() < 70)
                .collect(Collectors.toList());

        if (!errorWords.isEmpty()) {
            feedback.append(String.format("⚠️ **Phát hiện %d từ cần cải thiện:**\n", errorWords.size()));

            // Group by error type
            Map<String, List<String>> errorsByType = new HashMap<>();
            for (PronunciationAssessmentResponse.WordAssessment word : errorWords) {
                errorsByType.computeIfAbsent(word.getErrorType(), k -> new ArrayList<>()).add(word.getWord());
            }

            if (errorsByType.containsKey("Mispronunciation")) {
                feedback.append("- Phát âm chưa chuẩn: ").append(String.join(", ", errorsByType.get("Mispronunciation"))).append("\n");
            }
            if (errorsByType.containsKey("Omission")) {
                feedback.append("- Thiếu các từ: ").append(String.join(", ", errorsByType.get("Omission"))).append("\n");
            }
            if (errorsByType.containsKey("Insertion")) {
                feedback.append("- Từ thừa: ").append(String.join(", ", errorsByType.get("Insertion"))).append("\n");
            }
            feedback.append("\n");
        }

        // Recommendations
        feedback.append("💡 **Gợi ý cải thiện:**\n");
        if (accuracyScore < 70) {
            feedback.append("- Tập trung luyện phát âm các âm chuẩn xác hơn\n");
        }
        if (fluencyScore < 70) {
            feedback.append("- Luyện nói trôi chảy hơn, giảm ngập ngừng\n");
        }
        if (completenessScore < 70) {
            feedback.append("- Đọc đầy đủ tất cả các từ trong câu\n");
        }
        if (prosodyScore != null && prosodyScore < 70) {
            feedback.append("- Chú ý đến ngữ điệu, trọng âm và nhịp điệu\n");
        }
        if (!errorWords.isEmpty()) {
            feedback.append(String.format("- Luyện tập lại các từ: %s\n",
                    errorWords.stream().limit(5).map(w -> w.getWord()).collect(Collectors.joining(", "))));
        }

        return feedback.toString();
    }

    /**
     * Perform detailed speech recognition for LONG audio (continuous recognition)
     */
    private SpeechAnalysisResult performDetailedSpeechRecognition(File wavFile) throws Exception {
        SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
        speechConfig.setSpeechRecognitionLanguage("en-US");

        // Request detailed output
        speechConfig.setProperty(PropertyId.SpeechServiceResponse_RequestWordLevelTimestamps, "true");

        AudioConfig audioConfig = AudioConfig.fromWavFileInput(wavFile.getAbsolutePath());
        SpeechRecognizer recognizer = new SpeechRecognizer(speechConfig, audioConfig);

        // Get audio duration
        long audioDurationMs = getAudioDuration(wavFile);

        // Use continuous recognition for long audio
        ContinuousRecognitionResult result = performContinuousRecognition(recognizer);

        recognizer.close();
        audioConfig.close();
        speechConfig.close();

        if (result.getFullText().isEmpty()) {
            throw new ApiException("No speech could be recognized from the audio file",
                    HttpStatus.BAD_REQUEST.value());
        }

        return parseDetailedRecognitionResult(
                result.getAllJsonResults(),
                result.getFullText(),
                audioDurationMs
        );
    }

    /**
     * Perform continuous recognition and collect all results
     */
    private ContinuousRecognitionResult performContinuousRecognition(SpeechRecognizer recognizer)
            throws Exception {

        StringBuilder fullText = new StringBuilder();
        List<String> jsonResults = new ArrayList<>();

        CountDownLatch stopLatch = new CountDownLatch(1);
        AtomicBoolean hasError = new AtomicBoolean(false);
        AtomicReference<String> errorMessage = new AtomicReference<>("");

        // Handle recognized events
        recognizer.recognized.addEventListener((s, e) -> {
            if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                String text = e.getResult().getText();
                if (text != null && !text.trim().isEmpty()) {
                    fullText.append(text).append(" ");

                    // Collect JSON for detailed analysis
                    String json = e.getResult().getProperties()
                            .getProperty(PropertyId.SpeechServiceResponse_JsonResult);
                    if (json != null && !json.isEmpty()) {
                        jsonResults.add(json);
                    }

                    log.debug("Recognized: {}", text);
                }
            } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                log.debug("No match: speech could not be recognized");
            }
        });

        // Handle session stopped
        recognizer.sessionStopped.addEventListener((s, e) -> {
            log.debug("Session stopped");
            stopLatch.countDown();
        });

        // Handle canceled
        recognizer.canceled.addEventListener((s, e) -> {
            log.error("Recognition canceled: {}", e.getReason());
            if (e.getReason() == CancellationReason.Error) {
                log.error("Error details: {}", e.getErrorDetails());
                hasError.set(true);
                errorMessage.set(e.getErrorDetails());
            }
            stopLatch.countDown();
        });

        // Start continuous recognition
        recognizer.startContinuousRecognitionAsync().get();

        // Wait for completion (with timeout)
        boolean completed = stopLatch.await(5, TimeUnit.MINUTES);

        // Stop recognition
        recognizer.stopContinuousRecognitionAsync().get();

        if (!completed) {
            throw new ApiException("Recognition timeout after 5 minutes",
                    HttpStatus.REQUEST_TIMEOUT.value());
        }

        if (hasError.get()) {
            throw new ApiException("Recognition failed: " + errorMessage.get(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return ContinuousRecognitionResult.builder()
                .fullText(fullText.toString().trim())
                .allJsonResults(jsonResults)
                .build();
    }

    /**
     * Parse detailed recognition results from multiple JSON responses
     */
    private SpeechAnalysisResult parseDetailedRecognitionResult(
            List<String> jsonResults,
            String recognizedText,
            long audioDurationMs) {

        try {
            List<WordAnalysis> allWords = new ArrayList<>();
            double totalConfidence = 0.0;
            int confidenceCount = 0;

            long totalSpeechDurationTicks = 0;

            // Parse each JSON result
            for (String jsonResult : jsonResults) {
                JsonNode rootNode = objectMapper.readTree(jsonResult);
                JsonNode nBestArray = rootNode.get("NBest");

                if (nBestArray == null || !nBestArray.isArray() || nBestArray.isEmpty()) {
                    continue;
                }

                JsonNode nBestItem = nBestArray.get(0);

                // Accumulate confidence
                if (nBestItem.has("Confidence")) {
                    totalConfidence += nBestItem.get("Confidence").asDouble();
                    confidenceCount++;
                }

                // Parse words
                JsonNode wordsArray = nBestItem.get("Words");
                if (wordsArray != null && wordsArray.isArray()) {
                    for (JsonNode wordNode : wordsArray) {
                        String word = wordNode.get("Word").asText();

                        double confidence = wordNode.has("Confidence")
                                ? wordNode.get("Confidence").asDouble() * 100
                                : 0.0;

                        long durationTicks = wordNode.has("Duration")
                                ? wordNode.get("Duration").asLong()
                                : 0;

                        long offsetTicks = wordNode.has("Offset")
                                ? wordNode.get("Offset").asLong()
                                : 0;

                        totalSpeechDurationTicks += durationTicks;

                        allWords.add(WordAnalysis.builder()
                                .word(word)
                                .confidence(confidence)
                                .durationMs(ticksToMilliseconds(durationTicks))
                                .offsetMs(ticksToMilliseconds(offsetTicks))
                                .build());
                    }
                }
            }

            // Calculate overall confidence
            double overallConfidence = confidenceCount > 0
                    ? (totalConfidence / confidenceCount) * 100
                    : 0.0;

            // Calculate metrics
            int wordCount = allWords.size();
            double avgConfidence = allWords.stream()
                    .mapToDouble(WordAnalysis::getConfidence)
                    .average()
                    .orElse(0.0);

            // Speaking rate (words per minute)
            long totalSpeechDurationMs = ticksToMilliseconds(totalSpeechDurationTicks);
            double speakingRate = wordCount > 0 && totalSpeechDurationMs > 0
                    ? (wordCount / (totalSpeechDurationMs / 60000.0))
                    : 0.0;

            // Pause ratio
            long totalAudioDurationMs = audioDurationMs;
            long silenceDurationMs = totalAudioDurationMs - totalSpeechDurationMs;
            double pauseRatio = totalAudioDurationMs > 0
                    ? (silenceDurationMs / (double) totalAudioDurationMs)
                    : 0.0;

            // Count low-confidence words
            long lowConfidenceCount = allWords.stream()
                    .filter(w -> w.getConfidence() < 60)
                    .count();

            log.info("Parsed {} words from {} JSON results", wordCount, jsonResults.size());
            log.info("Speaking rate: {:.1f} WPM, Pause ratio: {:.1f}%",
                    speakingRate, pauseRatio * 100);

            return SpeechAnalysisResult.builder()
                    .recognizedText(recognizedText)
                    .overallConfidence(overallConfidence)
                    .wordCount(wordCount)
                    .words(allWords)
                    .avgConfidence(avgConfidence)
                    .speakingRate(speakingRate)
                    .pauseRatio(pauseRatio)
                    .totalDurationMs(totalAudioDurationMs)
                    .speechDurationMs(totalSpeechDurationMs)
                    .lowConfidenceWordCount(lowConfidenceCount)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse recognition results: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse speech recognition results", e);
        }
    }

    // Supporting class
    @Data
    @Builder
    private static class ContinuousRecognitionResult {
        private String fullText;
        private List<String> allJsonResults;
    }

    /**
     * Convert Azure ticks (100-nanosecond units) to milliseconds
     */
    private long ticksToMilliseconds(long ticks) {
        return ticks / 10000; // 1 tick = 100 nanoseconds = 0.0001 ms
    }

    /**
     * Get audio file duration in milliseconds
     */
    private long getAudioDuration(File wavFile) {
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(wavFile)) {
            AudioFormat format = audioInputStream.getFormat();
            long frames = audioInputStream.getFrameLength();
            double durationInSeconds = frames / format.getFrameRate();
            return (long) (durationInSeconds * 1000);
        } catch (Exception e) {
            log.warn("Could not get audio duration, using default: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Generate AI-powered assessment from speech analysis
     */
    private PronunciationAssessmentResponse generateAIAssessment(SpeechAnalysisResult analysis) {

        // Build prompt for AI
        String prompt = buildAIAssessmentPrompt(analysis);

        // Call OpenAI
        String aiResponse = openAiServiceImpl.callOpenAI(prompt);

        // Parse AI response
        return parseAIAssessmentResponse(aiResponse, analysis);
    }

    /**
     * Build comprehensive prompt for AI assessment
     */
    private String buildAIAssessmentPrompt(SpeechAnalysisResult analysis) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert English pronunciation coach. Analyze the following speech recognition results and provide a comprehensive pronunciation assessment.\n\n");

//        String ageInstructions = openAiServiceImpl.getAgeBasedLevelInstructions(age);
//        prompt.append(ageInstructions).append("\n\n");

        prompt.append("SPEECH ANALYSIS DATA:\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        prompt.append(String.format("Recognized Text: \"%s\"\n", analysis.getRecognizedText()));
        prompt.append(String.format("Word Count: %d words\n", analysis.getWordCount()));
        prompt.append(String.format("Total Audio Duration: %.1f seconds\n", analysis.getTotalDurationMs() / 1000.0));
        prompt.append(String.format("Actual Speech Duration: %.1f seconds\n", analysis.getSpeechDurationMs() / 1000.0));
        prompt.append(String.format("Speaking Rate: %.1f words/minute\n", analysis.getSpeakingRate()));
        prompt.append(String.format("Pause Ratio: %.1f%% (silence/total time)\n", analysis.getPauseRatio() * 100));
        prompt.append(String.format("Overall Confidence: %.1f%%\n", analysis.getOverallConfidence()));
        prompt.append(String.format("Average Word Confidence: %.1f%%\n", analysis.getAvgConfidence()));
        prompt.append(String.format("Low-Confidence Words: %d\n", analysis.getLowConfidenceWordCount()));
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // Word-level details
        if (!analysis.getWords().isEmpty()) {
            prompt.append("WORD-LEVEL ANALYSIS:\n");
            for (WordAnalysis word : analysis.getWords()) {
                prompt.append(String.format("- '%s': confidence %.1f%%, duration %.0fms\n",
                        word.getWord(), word.getConfidence(), word.getDurationMs()));
            }
            prompt.append("\n");
        }

        prompt.append("ASSESSMENT TASK:\n");
        prompt.append("Based on the above data, provide a pronunciation assessment in JSON format with these scores (0-100):\n\n");

        prompt.append("1. **pronunciationScore** (0-100): Overall pronunciation quality\n");
        prompt.append("   - Consider: confidence scores, clarity, word recognition accuracy\n");
        prompt.append("   - High confidence (>80%) = better pronunciation\n");
        prompt.append("   - Low confidence (<60%) = unclear/mispronounced words\n\n");

        prompt.append("2. **fluencyScore** (0-100): Speaking fluency and rhythm\n");
        prompt.append("   - Ideal speaking rate: 120-160 words/minute\n");
        prompt.append("   - Too fast (>180) or too slow (<100) = lower score\n");
        prompt.append("   - Excessive pauses (>40%) = hesitation, lower score\n");
        prompt.append("   - Natural pauses (15-30%) = good fluency\n\n");

        prompt.append("3. **clarityScore** (0-100): Speech clarity and articulation\n");
        prompt.append("   - Based on overall and average confidence scores\n");
        prompt.append("   - Few low-confidence words = clear articulation\n\n");

        prompt.append("4. **confidenceScore** (0-100): Speaker confidence and delivery\n");
        prompt.append("   - Steady pace + normal pauses = confident\n");
        prompt.append("   - Too many pauses or very slow = hesitant\n\n");

        prompt.append("5. **feedback** (string): Detailed feedback in Vietnamese\n");
        prompt.append("   - Start with overall impression\n");
        prompt.append("   - Mention specific strengths (clear words, good pace, etc.)\n");
        prompt.append("   - Point out specific issues (unclear words, too fast/slow, hesitation)\n");
        prompt.append("   - Give 2-3 actionable improvement tips\n");
        prompt.append("   - Keep it natural, encouraging, and concise (150-200 words)\n\n");

        prompt.append("OUTPUT FORMAT (JSON only, no markdown, no extra text):\n");
        prompt.append("{\n");
        prompt.append("  \"pronunciationScore\": 75.0,\n");
        prompt.append("  \"fluencyScore\": 80.0,\n");
        prompt.append("  \"clarityScore\": 70.0,\n");
        prompt.append("  \"confidenceScore\": 85.0,\n");
        prompt.append("  \"feedback\": \"Phản hồi chi tiết bằng tiếng Việt...\"\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * Parse AI assessment response
     */
    private PronunciationAssessmentResponse parseAIAssessmentResponse(
            String jsonResponse,
            SpeechAnalysisResult analysis) {

        try {
            String cleaned = openAiServiceImpl.cleanJsonResponse(jsonResponse);
            JsonNode root = objectMapper.readTree(cleaned);

            double pronunciationScore = root.has("pronunciationScore")
                    ? root.get("pronunciationScore").asDouble() : 0.0;
            double fluencyScore = root.has("fluencyScore")
                    ? root.get("fluencyScore").asDouble() : 0.0;
            double clarityScore = root.has("clarityScore")
                    ? root.get("clarityScore").asDouble() : 0.0;
            double confidenceScore = root.has("confidenceScore")
                    ? root.get("confidenceScore").asDouble() : 0.0;
            String feedback = root.has("feedback")
                    ? root.get("feedback").asText() : "";

            // Clamp scores
            pronunciationScore = clampScore(pronunciationScore);
            fluencyScore = clampScore(fluencyScore);
            clarityScore = clampScore(clarityScore);
            confidenceScore = clampScore(confidenceScore);

            return PronunciationAssessmentResponse.builder()
                    .pronunciationScore(pronunciationScore)
                    .accuracyScore(clarityScore) // Map clarity to accuracy
                    .fluencyScore(fluencyScore)
                    .completenessScore(confidenceScore) // Map confidence to completeness
                    .prosodyScore(null) // Not available in free-form
                    .recognizedText(analysis.getRecognizedText())
                    .referenceText(null) // No reference text
                    .feedback(feedback)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse AI assessment: {}", e.getMessage(), e);
            // Return fallback based on raw metrics
            return createFallbackAssessment(analysis);
        }
    }

    /**
     * Clamp score to 0-100 range
     */
    private double clampScore(double score) {
        if (Double.isNaN(score) || score < 0) return 0.0;
        if (score > 100) return 100.0;
        return score;
    }

    /**
     * Create fallback assessment from metrics
     */
    private PronunciationAssessmentResponse createFallbackAssessment(SpeechAnalysisResult analysis) {
        // Simple scoring based on metrics
        double pronunciationScore = analysis.getAvgConfidence();
        double fluencyScore = calculateFluencyScore(analysis.getSpeakingRate(), analysis.getPauseRatio());
        double clarityScore = analysis.getOverallConfidence();
        double confidenceScore = 100.0 - (analysis.getPauseRatio() * 100);

        String feedback = String.format(
                "Bạn đã nói %d từ với tốc độ %.1f từ/phút. " +
                        "Độ tự tin trung bình: %.1f%%. " +
                        "Hãy luyện tập thêm để cải thiện độ rõ ràng và tự tin khi nói.",
                analysis.getWordCount(),
                analysis.getSpeakingRate(),
                analysis.getAvgConfidence()
        );

        return PronunciationAssessmentResponse.builder()
                .pronunciationScore(pronunciationScore)
                .accuracyScore(clarityScore)
                .fluencyScore(fluencyScore)
                .completenessScore(confidenceScore)
                .recognizedText(analysis.getRecognizedText())
                .feedback(feedback)
                .build();
    }

    /**
     * Calculate fluency score from speaking rate and pause ratio
     */
    private double calculateFluencyScore(double speakingRate, double pauseRatio) {
        double rateScore = 100.0;

        // Ideal rate: 120-160 WPM
        if (speakingRate < 100) {
            rateScore = speakingRate * 0.8; // Too slow
        } else if (speakingRate > 180) {
            rateScore = Math.max(60, 100 - (speakingRate - 180) * 0.5); // Too fast
        }

        // Ideal pause ratio: 15-30%
        double pauseScore = 100.0;
        if (pauseRatio > 0.40) {
            pauseScore = Math.max(50, 100 - (pauseRatio - 0.30) * 200); // Too many pauses
        } else if (pauseRatio < 0.10) {
            pauseScore = Math.max(70, pauseRatio * 500); // Too few pauses
        }

        return (rateScore + pauseScore) / 2.0;
    }

    private void validateEnglishOnly(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        // Vietnamese Unicode ranges
        // À-ỹ covers most Vietnamese diacritics
        String vietnamesePattern = "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ]";

        // Count Vietnamese characters
        int vietnameseCount = 0;
        int totalLetters = 0;

        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                totalLetters++;
                if (String.valueOf(c).matches(vietnamesePattern)) {
                    vietnameseCount++;
                }
            }
        }

        // If more than 30% of letters are Vietnamese, reject
        if (totalLetters > 0) {
            double vietnameseRatio = (double) vietnameseCount / totalLetters;

            if (vietnameseRatio > 0.30) {
                log.warn("Detected Vietnamese content: {} Vietnamese chars out of {} total letters ({:.1f}%)",
                        vietnameseCount, totalLetters, vietnameseRatio * 100);
                throw new ApiException(
                        "This assessment only supports English pronunciation. Please provide English text or speech only.",
                        HttpStatus.BAD_REQUEST.value()
                );
            }
        }
    }

    // Supporting classes
    @Data
    @Builder
    private static class SpeechAnalysisResult {
        private String recognizedText;
        private double overallConfidence;
        private int wordCount;
        private List<WordAnalysis> words;
        private double avgConfidence;
        private double speakingRate;
        private double pauseRatio;
        private long totalDurationMs;
        private long speechDurationMs;
        private long lowConfidenceWordCount;
    }

    @Data
    @Builder
    private static class WordAnalysis {
        private String word;
        private double confidence;
        private double durationMs;
        private double offsetMs;
    }
}
