package com.learning.progress.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import com.learning.progress.common.DifficultyLevel;
import com.learning.progress.common.LessonFocus;
import com.learning.progress.dto.ai.*;
import com.learning.progress.dto.challenge.section.*;
import com.learning.progress.entity.DailyChallenge;
import com.learning.progress.entity.Level;
import com.learning.progress.entity.Syllabus;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.DailyChallengeRepository;
import com.learning.progress.repository.LevelRepository;
import com.learning.progress.service.OpenAiService;
import com.learning.progress.util.FileContentExtractor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OpenAiServiceImpl implements OpenAiService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DailyChallengeRepository dailyChallengeRepository;
    private final LevelRepository levelRepository;
    private ExecutorService executorService;

    @Value("${azure.openai.batch-size}")
    private int batchSize;

    @Value("${azure.openai.thread-pool-size}")
    private int threadPoolSize;

    @Value("${azure.openai.max-question}")
    private int maxQuestion;

    @Value("${azure.openai.endpoint}")
    private String endpoint;

    @Value("${azure.openai.api-key}")
    private String apiKey;

    @Value("${azure.openai.reading-passage.words-per-paragraph}")
    private int wordsPerParagraphDefault;

    @Value("${azure.openai.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${azure.openai.read-timeout-ms:300000}")
    private int readTimeoutMs;

    @Value("${azure.openai.generation-timeout-minutes:10}")
    private int generationTimeoutMinutes;

    @Value("${azure.openai.batch-retry.max-attempts:3}")
    private int batchRetryMaxAttempts;

    @Value("${azure.openai.batch-retry.delay-ms:2000}")
    private int batchRetryDelayMs;

    private RestTemplate restTemplate;

    private static final String API_VERSION = "2025-04-01-preview";
    private static final Pattern POSITION_PATTERN = Pattern.compile("\\[\\[pos_([a-z0-9]+)\\]\\]");
    private static final String SYSTEM_ROLE_JSON_INSTRUCTION = "You are an expert English teacher. Return ONLY valid JSON (no markdown, no comments, no extra text). " +
            "Do NOT include trailing commas or non-standard JSON syntax.";

    public OpenAiServiceImpl(DailyChallengeRepository dailyChallengeRepository,
                             LevelRepository levelRepository) {
        this.dailyChallengeRepository = dailyChallengeRepository;
        this.levelRepository = levelRepository;
    }

    @PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(Math.max(1, threadPoolSize));
        log.info("Initialized thread pool with size: {}", threadPoolSize);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
        log.info("Initialized RestTemplate with connectTimeout={}ms readTimeout={}ms",
                connectTimeoutMs, readTimeoutMs);
    }

    @PreDestroy
    public void cleanup() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * ✅ UPDATED: Validate input content with context-specific rules
     */
    private InputValidationResponse validateInputContent(
            String description,
            String vocabularyList,
            List<LessonFocus> lessonFocus,
            String customLessonFocus,
            String sectionContent,
            String fileContent,
            ChallengeContext context,
            ValidationType validationType,
            LevelInfo levelInfo) {
        try {
            log.info("Validating input content for type: {}", validationType);

            StringBuilder contentToCheck = new StringBuilder();

            // Build content based on validation type
            switch (validationType) {
                case GV:
                    // GV: Only check description, vocabulary, lesson focus
                    if (description != null && !description.isBlank()) {
                        contentToCheck.append("Description: ").append(description).append("\n");
                    }
                    if (vocabularyList != null && !vocabularyList.isBlank()) {
                        contentToCheck.append("Vocabulary: ").append(vocabularyList).append("\n");
                    }
                    if (customLessonFocus != null && !customLessonFocus.isBlank()) {
                        contentToCheck.append("Custom Focus: ").append(customLessonFocus).append("\n");
                    }
                    break;

                case CONTENT_BASED:
                    // BASED: Check section content and description
                    if (sectionContent != null && !sectionContent.isBlank()) {
                        contentToCheck.append("Section Content: ").append(sectionContent).append("\n");
                    }
                    if (description != null && !description.isBlank()) {
                        contentToCheck.append("Description: ").append(description).append("\n");
                    }
                    break;

                case FILE:
                    // FILE: Check file content
                    if (fileContent != null && !fileContent.isBlank()) {
                        contentToCheck.append("File Content: ").append(fileContent).append("\n");
                    }
                    break;
            }

            // If nothing to check, return valid
            if (contentToCheck.length() == 0) {
                return new InputValidationResponse(null, null, null ,null, null);
            }

            // Build lesson context
            StringBuilder lessonContext = new StringBuilder();
            if (context != null) {
                lessonContext.append("Lesson: ").append(context.classLessonName).append("\n");
                lessonContext.append("Chapter: ").append(context.classChapterName).append("\n");
                if (context.classLessonContent != null && !context.classLessonContent.isBlank()) {
                    lessonContext.append("Lesson Content: ")
                            .append(context.classLessonContent.length() > 500
                                    ? context.classLessonContent.substring(0, 500) + "..."
                                    : context.classLessonContent)
                            .append("\n");
                }
            }

            String prompt = buildInputValidationPrompt(
                    contentToCheck.toString(),
                    lessonContext.toString(),
                    validationType,
                    levelInfo
            );
            String aiResponse = callOpenAI(prompt);

            return parseInputValidationResponse(aiResponse);

        } catch (Exception e) {
            log.error("Error validating input content: {}", e.getMessage(), e);
            // In case of error, return warning
            return new InputValidationResponse(null,
                    "Could not validate content. Please review manually.", null, null, null);
        }
    }

    private String buildInputValidationPrompt(String content, String lessonContext, ValidationType validationType, LevelInfo levelInfo) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a content safety and quality checker for an educational English learning platform.\n\n");

        if (levelInfo != null) {
            prompt.append("STUDENT LEVEL:\n");
            prompt.append("Level: ").append(levelInfo.levelName).append("\n");
            if (levelInfo.levelDescription != null && !levelInfo.levelDescription.isBlank()) {
                prompt.append("Description: ").append(levelInfo.levelDescription).append("\n");
            }
            if (levelInfo.learningObjective != null && !levelInfo.learningObjective.isBlank()) {
                prompt.append("Learning Objective: ").append(levelInfo.learningObjective).append("\n");
            }
            prompt.append("\n");
        }

        // Lesson context
        if (!lessonContext.isBlank()) {
            prompt.append("LESSON CONTEXT:\n");
            prompt.append(lessonContext).append("\n");
        }

        prompt.append("YOUR TASK:\n");
        prompt.append("1. Check the provided content for safety and appropriateness.\n");
        prompt.append("2. Process content to English for better AI generation.\n\n");

        // ❌ ERROR cases (apply to ALL types)
        prompt.append("❌ SEVERE ISSUES - Set 'error' field (MUST REJECT):\n");
        prompt.append("- Violence, hate speech, discrimination, racism\n");
        prompt.append("- Sexual, adult, or inappropriate content\n");
        prompt.append("- Profanity or offensive language\n");
        prompt.append("- Political propaganda or extremist ideology\n");
        prompt.append("- Drugs, illegal activities, dangerous behavior\n");
        prompt.append("- Self-harm or psychologically harmful content\n");
        prompt.append("- Content NOT related to English learning (e.g., math problems like '1+1=?', pure science, history facts without English context)\n");
        prompt.append("- Any content unsafe for students\n\n");

        if ("CONTENT_BASED".equals(validationType.toString())) {
            prompt.append("- [CONTENT_BASED ONLY] Section content must be at least 90% English; excessive use of any other language is not allowed\n");
        }
        prompt.append("\n");

        // ⚠️ WARNING cases (depend on validation type)
        prompt.append("⚠️ MINOR ISSUES - Set 'warning' field:\n");

        switch (validationType) {
            case GV:
                prompt.append("FOR GRAMMAR/VOCABULARY QUESTIONS:\n");
                prompt.append("- Requests for Vietnamese language questions (all questions MUST be in English)\n");
                prompt.append("- Requests to change the number of questions in description (use configured count only)\n");
                prompt.append("- Vocabulary or grammar concepts are TOO ADVANCED or TOO BASIC for the selected student level\n");
                prompt.append("- Content is NOT relevant to the lesson topic\n");
                prompt.append("- Content is completely unrelated to English learning\n");
                prompt.append("NOTE: Spelling errors, grammar mistakes, and Vietnamese text are ACCEPTABLE - do NOT warn about these.\n\n");
                break;

            case CONTENT_BASED:
                prompt.append("FOR CONTENT-BASED QUESTIONS:\n");

                // Độ khó & level
                prompt.append("- Content difficulty is TOO ADVANCED or TOO BASIC for the selected student level\n");
                prompt.append("- Vocabulary level is not aligned with the lesson or student level\n");
                prompt.append("- Sentence structures are too complex or too simple for the target level\n");

                // Độ dài & chất lượng bài đọc
                prompt.append("- Content is TOO SHORT to be meaningful (may cause repetitive or trivial questions)\n");
                prompt.append("- Content lacks sufficient information to generate diverse questions\n");

                // Lặp & chất lượng ngôn ngữ
                prompt.append("- Excessive sentence repetition or paraphrased repetition\n");
                prompt.append("- Unnatural, machine-like, or poorly written English\n");
                prompt.append("- Content contains many broken or incomplete sentences\n");

                // Liên quan & tính giáo dục
                prompt.append("- Content is NOT relevant to the lesson topic\n");
                prompt.append("- Content is not educational or suitable for students\n");
                prompt.append("- Content focuses on opinions, ads, or storytelling unrelated to learning goals\n");

                // Logic & nhất quán
                prompt.append("- Description does not match the section content\n");
                prompt.append("- Content lacks a clear topic, context, or logical flow\n");

                prompt.append("- Content is NOT relevant or appropriate for the lesson\n");
                prompt.append("- Description does not match section content\n\n");
                break;

            case FILE:
                prompt.append("FOR FILE UPLOAD:\n");
                prompt.append("- File contains duplicate questions (same or very similar)\n");
                prompt.append("- File contains images or non-text content\n");
                prompt.append("- Question content is NOT relevant to the lesson\n");
                prompt.append("- Questions are not educational or appropriate for students\n");
                prompt.append("- Misleading or deceptive information\n\n");
                prompt.append("- File contains extra descriptive text or paragraphs outside of the questions\n\n");
                break;
        }

        prompt.append("CONTENT TO CHECK:\n");
        prompt.append("--------------------------------------------------\n");
        prompt.append(content).append("\n");
        prompt.append("--------------------------------------------------\n\n");

        // ✅ UPDATED: Processing instructions
        prompt.append("🌐 CONTENT PROCESSING TASK:\n\n");

        prompt.append("1️⃣ DESCRIPTION:\n");
        prompt.append("   - Translate from Vietnamese to English if needed\n");
        prompt.append("   - If already in English, return unchanged\n");
        prompt.append("   - If not present, set to null\n\n");

        prompt.append("2️⃣ VOCABULARY LIST (SPECIAL HANDLING):\n");
        prompt.append("   - Format is usually: \"word: Vietnamese meaning\" (e.g., \"bug: lỗi\", \"feature: tính năng\")\n");
        prompt.append("   - EXTRACT ONLY THE ENGLISH WORDS, separated by commas\n");
        prompt.append("   - REMOVE Vietnamese meanings completely\n");
        prompt.append("   - Example input: \"bug: lỗi, feature: tính năng, developer: lập trình viên\"\n");
        prompt.append("   - Example output: \"bug, feature, developer\"\n");
        prompt.append("   - If vocabulary is a simple list without meanings, return as-is\n");
        prompt.append("   - If not present, set to null\n\n");

        prompt.append("3️⃣ CUSTOM LESSON FOCUS:\n");
        prompt.append("   - Translate from Vietnamese to English if needed\n");
        prompt.append("   - If already in English, return unchanged\n");
        prompt.append("   - If not present, set to null\n\n");

        prompt.append("OUTPUT FORMAT (JSON ONLY):\n");
        prompt.append("{\n");
        prompt.append("  \"error\": \"short message in English or null\",\n");
        prompt.append("  \"warning\": \"short message in English or null\",\n");
        prompt.append("  \"translatedDescription\": \"translated text or null\",\n");
        prompt.append("  \"translatedVocabularyList\": \"English words only, comma-separated, or null\",\n");
        prompt.append("  \"translatedCustomLessonFocus\": \"translated text or null\"\n");
        prompt.append("}\n\n");

        prompt.append("RULES:\n");
        prompt.append("- Error/warning messages must be SHORT (max 1 sentence)\n");
        prompt.append("- Use clear, user-friendly language\n");
        prompt.append("- Set ONLY ONE of error or warning if applicable\n");
        prompt.append("- Both error and warning must be null if content is acceptable\n");
        prompt.append("- For vocabulary: ONLY extract English words, NO Vietnamese meanings\n");
        prompt.append("- Return valid JSON only\n\n");

        prompt.append("EXAMPLES:\n\n");

        prompt.append("Example 1 (vocabulary with meanings):\n");
        prompt.append("Input: \"Vocabulary: bug: lỗi, feature: tính năng, developer: lập trình viên\"\n");
        prompt.append("{\n");
        prompt.append("  \"error\": null,\n");
        prompt.append("  \"warning\": null,\n");
        prompt.append("  \"translatedDescription\": null,\n");
        prompt.append("  \"translatedVocabularyList\": \"bug, feature, developer\",\n");
        prompt.append("  \"translatedCustomLessonFocus\": null\n");
        prompt.append("}\n\n");

        prompt.append("Example 2 (Vietnamese description):\n");
        prompt.append("Input: \"Description: Tạo câu hỏi về thì quá khứ đơn\"\n");
        prompt.append("{\n");
        prompt.append("  \"error\": null,\n");
        prompt.append("  \"warning\": null,\n");
        prompt.append("  \"translatedDescription\": \"Create questions about past simple tense\",\n");
        prompt.append("  \"translatedVocabularyList\": null,\n");
        prompt.append("  \"translatedCustomLessonFocus\": null\n");
        prompt.append("}\n\n");

        prompt.append("Example 3 (full content):\n");
        prompt.append("Input:\n");
        prompt.append("\"Description: Tập trung vào động từ bất quy tắc\n");
        prompt.append("Vocabulary: go: đi, went: đã đi, gone: đã đi (hoàn thành), see: nhìn, saw: đã nhìn\n");
        prompt.append("Custom Focus: Học sinh cần phân biệt giữa quá khứ đơn và hiện tại hoàn thành\"\n");
        prompt.append("{\n");
        prompt.append("  \"error\": null,\n");
        prompt.append("  \"warning\": null,\n");
        prompt.append("  \"translatedDescription\": \"Focus on irregular verbs\",\n");
        prompt.append("  \"translatedVocabularyList\": \"go, went, gone, see, saw\",\n");
        prompt.append("  \"translatedCustomLessonFocus\": \"Students need to distinguish between past simple and present perfect\"\n");
        prompt.append("}\n\n");

        prompt.append("Analyze and process now.\n");

        return prompt.toString();
    }

    /**
     * Enum for validation types
     */
    private enum ValidationType {
        GV,              // Grammar/Vocabulary questions
        CONTENT_BASED,   // Content-based questions
        FILE             // File upload
    }


    /**
     * ✅ NEW: Parse input validation response
     */
    private InputValidationResponse parseInputValidationResponse(String jsonResponse) {
        try {
            String cleaned = cleanJsonResponse(jsonResponse);
            JsonNode root = objectMapper.readTree(cleaned);

            String error = root.has("error") && !root.get("error").isNull()
                    ? root.get("error").asText() : null;
            String warning = root.has("warning") && !root.get("warning").isNull()
                    ? root.get("warning").asText() : null;

            // ✅ NEW: Parse translated fields
            String translatedDescription = root.has("translatedDescription") && !root.get("translatedDescription").isNull()
                    ? root.get("translatedDescription").asText() : null;
            String translatedVocabularyList = root.has("translatedVocabularyList") && !root.get("translatedVocabularyList").isNull()
                    ? root.get("translatedVocabularyList").asText() : null;
            String translatedCustomLessonFocus = root.has("translatedCustomLessonFocus") && !root.get("translatedCustomLessonFocus").isNull()
                    ? root.get("translatedCustomLessonFocus").asText() : null;

            return new InputValidationResponse(
                    error,
                    warning,
                    translatedDescription,
                    translatedVocabularyList,
                    translatedCustomLessonFocus
            );

        } catch (Exception e) {
            log.error("Failed to parse input validation response: {}", e.getMessage(), e);
            return new InputValidationResponse(
                    null,
                    "Could not parse validation result. Please review content manually.",
                    null,
                    null,
                    null
            );
        }
    }

    /**
     * ✅ UPDATED: Validate output JSON format comprehensively
     */
    private void validateOutputFormat(List<QuestionDto> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new ApiException("No questions generated", HttpStatus.BAD_REQUEST.value());
        }

        for (int i = 0; i < questions.size(); i++) {
            QuestionDto question = questions.get(i);
            int questionNumber = i + 1;

            // Basic field validation
            if (question.getQuestionText() == null || question.getQuestionText().trim().isEmpty()) {
                throw new ApiException("Question " + questionNumber + ": questionText is required",
                        HttpStatus.BAD_REQUEST.value());
            }

            if (question.getQuestionType() == null || question.getQuestionType().trim().isEmpty()) {
                throw new ApiException("Question " + questionNumber + ": questionType is required",
                        HttpStatus.BAD_REQUEST.value());
            }

            if (question.getContent() == null || question.getContent().getData() == null ||
                    question.getContent().getData().isEmpty()) {
                throw new ApiException("Question " + questionNumber + ": content.data is required and cannot be empty",
                        HttpStatus.BAD_REQUEST.value());
            }

            // Validate based on question type
            String questionType = question.getQuestionType();
            List<DataItem> dataItems = question.getContent().getData();

            switch (questionType) {
                case "MULTIPLE_CHOICE":
                    validateMultipleChoiceFormat(question, dataItems, questionNumber);
                    break;
                case "TRUE_OR_FALSE":
                    validateTrueOrFalseFormat(question, dataItems, questionNumber);
                    break;
                case "FILL_IN_THE_BLANK":
                    validateFillInTheBlankFormat(question, dataItems, questionNumber);
                    break;
                case "DROPDOWN":
                    validateDropdownFormat(question, dataItems, questionNumber);
                    break;
                case "REARRANGE":
                    validateRearrangeFormat(question, dataItems, questionNumber);
                    break;
                case "DRAG_AND_DROP":
                    validateDragAndDropFormat(question, dataItems, questionNumber);
                    break;
                case "MULTIPLE_SELECT":
                    validateMultipleSelectFormat(question, dataItems, questionNumber);
                    break;
                case "REWRITE":
                    validateRewriteFormat(question, dataItems, questionNumber);
                    break;
                default:
                    throw new ApiException("Question " + questionNumber + ": Unknown question type '" + questionType + "'",
                            HttpStatus.BAD_REQUEST.value());
            }
        }

        log.info("✅ All questions passed format validation");
    }

    private void validateMultipleChoiceFormat(QuestionDto question, List<DataItem> dataItems, int questionNumber) {
        if (dataItems.size() != 4) {
            throw new ApiException("Question " + questionNumber + " (MULTIPLE_CHOICE): Must have exactly 4 options, found " + dataItems.size(),
                    HttpStatus.BAD_REQUEST.value());
        }

        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != 1) {
            throw new ApiException("Question " + questionNumber + " (MULTIPLE_CHOICE): Must have exactly 1 correct answer, found " + correctCount,
                    HttpStatus.BAD_REQUEST.value());
        }

        for (DataItem item : dataItems) {
            if (item.getPositionId() != null) {
                throw new ApiException("Question " + questionNumber + " (MULTIPLE_CHOICE): Options must have positionId=null",
                        HttpStatus.BAD_REQUEST.value());
            }
            if (item.getValue() == null || item.getValue().trim().isEmpty()) {
                throw new ApiException("Question " + questionNumber + " (MULTIPLE_CHOICE): Option value cannot be empty",
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateTrueOrFalseFormat(QuestionDto question, List<DataItem> dataItems, int questionNumber) {
        if (dataItems.size() != 2) {
            throw new ApiException("Question " + questionNumber + " (TRUE_OR_FALSE): Must have exactly 2 options, found " + dataItems.size(),
                    HttpStatus.BAD_REQUEST.value());
        }

        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != 1) {
            throw new ApiException("Question " + questionNumber + " (TRUE_OR_FALSE): Must have exactly 1 correct answer, found " + correctCount,
                    HttpStatus.BAD_REQUEST.value());
        }

        for (DataItem item : dataItems) {
            if (item.getPositionId() != null) {
                throw new ApiException("Question " + questionNumber + " (TRUE_OR_FALSE): Options must have positionId=null",
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateFillInTheBlankFormat(QuestionDto question, List<DataItem> dataItems, int questionNumber) {
        String questionText = question.getQuestionText();

        // Must contain [[pos_xxx]] placeholders
        Matcher matcher = POSITION_PATTERN.matcher(questionText);
        Set<String> positionsInText = new HashSet<>();
        while (matcher.find()) {
            positionsInText.add(matcher.group(1));
        }

        if (positionsInText.isEmpty()) {
            throw new ApiException("Question " + questionNumber + " (FILL_IN_THE_BLANK): Must contain [[pos_xxx]] placeholders",
                    HttpStatus.BAD_REQUEST.value());
        }

        // ✅ CRITICAL: Only 1 correct answer allowed
        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != 1) {
            throw new ApiException("Question " + questionNumber + " (FILL_IN_THE_BLANK): Must have exactly 1 correct answer, found " + correctCount,
                    HttpStatus.BAD_REQUEST.value());
        }

        if (dataItems.size() != 1) {
            throw new ApiException("Question " + questionNumber + " (FILL_IN_THE_BLANK): Can only have 1 answer, found " + dataItems.size(),
                    HttpStatus.BAD_REQUEST.value());
        }

        // All answers must be correct (isCorrect=true)
        for (DataItem item : dataItems) {
            if (!item.isCorrect()) {
                throw new ApiException("Question " + questionNumber + " (FILL_IN_THE_BLANK): All answers must have isCorrect=true",
                        HttpStatus.BAD_REQUEST.value());
            }

            if (item.getPositionId() == null || item.getPositionId().trim().isEmpty()) {
                throw new ApiException("Question " + questionNumber + " (FILL_IN_THE_BLANK): Answers must have valid positionId",
                        HttpStatus.BAD_REQUEST.value());
            }

            if (!positionsInText.contains(item.getPositionId())) {
                throw new ApiException("Question " + questionNumber + " (FILL_IN_THE_BLANK): Answer positionId '" +
                        item.getPositionId() + "' not found in question text",
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateDropdownFormat(QuestionDto question, List<DataItem> dataItems, int questionNumber) {
        String questionText = question.getQuestionText();

        Matcher matcher = POSITION_PATTERN.matcher(questionText);
        Set<String> positionsInText = new HashSet<>();
        while (matcher.find()) {
            positionsInText.add(matcher.group(1));
        }

        if (positionsInText.isEmpty()) {
            throw new ApiException("Question " + questionNumber + " (DROPDOWN): Must contain [[pos_xxx]] placeholders",
                    HttpStatus.BAD_REQUEST.value());
        }

        Map<String, List<DataItem>> itemsByPosition = dataItems.stream()
                .filter(item -> item.getPositionId() != null)
                .collect(Collectors.groupingBy(DataItem::getPositionId));

        for (String posId : positionsInText) {
            List<DataItem> options = itemsByPosition.get(posId);
            if (options == null || options.size() != 4) {
                throw new ApiException("Question " + questionNumber + " (DROPDOWN): Position '" + posId +
                        "' must have exactly 4 options, found " + (options == null ? 0 : options.size()),
                        HttpStatus.BAD_REQUEST.value());
            }

            long correctCount = options.stream().filter(DataItem::isCorrect).count();
            if (correctCount != 1) {
                throw new ApiException("Question " + questionNumber + " (DROPDOWN): Position '" + posId +
                        "' must have exactly 1 correct answer, found " + correctCount,
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateRearrangeFormat(QuestionDto question, List<DataItem> dataItems, int questionNumber) {
        String questionText = question.getQuestionText();

        Matcher matcher = POSITION_PATTERN.matcher(questionText);
        Set<String> positionsInText = new HashSet<>();
        while (matcher.find()) {
            positionsInText.add(matcher.group(1));
        }

        if (positionsInText.isEmpty()) {
            throw new ApiException("Question " + questionNumber + " (REARRANGE): Must contain [[pos_xxx]] placeholders",
                    HttpStatus.BAD_REQUEST.value());
        }

        if (positionsInText.size() < 5 || positionsInText.size() > 8) {
            throw new ApiException("Question " + questionNumber + " (REARRANGE): Must have 5-8 items, found " + positionsInText.size(),
                    HttpStatus.BAD_REQUEST.value());
        }

        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != dataItems.size()) {
            throw new ApiException("Question " + questionNumber + " (REARRANGE): All items must have isCorrect=true, found " +
                    (dataItems.size() - correctCount) + " incorrect items",
                    HttpStatus.BAD_REQUEST.value());
        }

        for (DataItem item : dataItems) {
            if (item.getPositionId() == null || item.getPositionId().trim().isEmpty()) {
                throw new ApiException("Question " + questionNumber + " (REARRANGE): Items must have valid positionId",
                        HttpStatus.BAD_REQUEST.value());
            }
            if (!positionsInText.contains(item.getPositionId())) {
                throw new ApiException("Question " + questionNumber + " (REARRANGE): Item positionId '" +
                        item.getPositionId() + "' not found in question text",
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateDragAndDropFormat(QuestionDto question, List<DataItem> dataItems, int questionNumber) {
        String questionText = question.getQuestionText();

        Matcher matcher = POSITION_PATTERN.matcher(questionText);
        Set<String> positionsInText = new HashSet<>();
        while (matcher.find()) {
            positionsInText.add(matcher.group(1));
        }

        if (positionsInText.isEmpty()) {
            throw new ApiException("Question " + questionNumber + " (DRAG_AND_DROP): Must contain [[pos_xxx]] placeholders",
                    HttpStatus.BAD_REQUEST.value());
        }

        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != positionsInText.size()) {
            throw new ApiException("Question " + questionNumber + " (DRAG_AND_DROP): Must have " + positionsInText.size() +
                    " correct answers (matching placeholders), found " + correctCount,
                    HttpStatus.BAD_REQUEST.value());
        }

        for (DataItem item : dataItems) {
            if (item.isCorrect()) {
                if (item.getPositionId() == null || item.getPositionId().trim().isEmpty()) {
                    throw new ApiException("Question " + questionNumber + " (DRAG_AND_DROP): Correct answers must have valid positionId",
                            HttpStatus.BAD_REQUEST.value());
                }
                if (!positionsInText.contains(item.getPositionId())) {
                    throw new ApiException("Question " + questionNumber + " (DRAG_AND_DROP): Answer positionId '" +
                            item.getPositionId() + "' not found in question text",
                            HttpStatus.BAD_REQUEST.value());
                }
            }
        }
    }

    private void validateMultipleSelectFormat(QuestionDto question, List<DataItem> dataItems, int questionNumber) {
        if (dataItems.size() < 4 || dataItems.size() > 6) {
            throw new ApiException("Question " + questionNumber + " (MULTIPLE_SELECT): Must have 4-6 options, found " + dataItems.size(),
                    HttpStatus.BAD_REQUEST.value());
        }

        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount < 2 || correctCount > 3) {
            throw new ApiException("Question " + questionNumber + " (MULTIPLE_SELECT): Must have 2-3 correct answers, found " + correctCount,
                    HttpStatus.BAD_REQUEST.value());
        }

        for (DataItem item : dataItems) {
            if (item.getPositionId() != null) {
                throw new ApiException("Question " + questionNumber + " (MULTIPLE_SELECT): Options must have positionId=null",
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateRewriteFormat(QuestionDto question, List<DataItem> dataItems, int questionNumber) {
        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != dataItems.size()) {
            throw new ApiException("Question " + questionNumber + " (REWRITE): All answers must have isCorrect=true, found " +
                    (dataItems.size() - correctCount) + " incorrect answers",
                    HttpStatus.BAD_REQUEST.value());
        }

        for (DataItem item : dataItems) {
            if (item.getPositionId() != null) {
                throw new ApiException("Question " + questionNumber + " (REWRITE): Answers must have positionId=null",
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GenerateQuestionsResponse generateGVQuestions(GenerateGVQuestionsRequest request) {
        LevelInfo levelInfo = parseLevelInfo(request.getLevel());

        log.info("Starting GV question generation for challengeId: {}", request.getChallengeId());

        DailyChallenge challenge = dailyChallengeRepository.findByIdWithFullHierarchy(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        int existingQuestionsCount = countExistingQuestions(challenge);
        int remainingQuota = 100 - existingQuestionsCount;

        log.info("Existing questions: {}, Remaining quota: {}/{}",
                existingQuestionsCount, remainingQuota, maxQuestion);

        if (remainingQuota <= 0) {
            log.error("Challenge already has maximum questions: {}/{}", existingQuestionsCount, maxQuestion);
            throw new ApiException("Challenge already has maximum " + maxQuestion +
                    " questions. Cannot generate more.", HttpStatus.BAD_REQUEST.value());
        }

        int totalQuestions = request.getQuestionTypeConfigs().stream()
                .mapToInt(GenerateGVQuestionsRequest.QuestionTypeConfig::getNumberOfQuestions)
                .sum();

        if (totalQuestions > maxQuestion) {
            log.error("Total questions exceeds remaining quota: {} > {}", totalQuestions, maxQuestion);
            throw new ApiException("You can only generate up to " + maxQuestion +  " questions per request",
                    HttpStatus.BAD_REQUEST.value());
        }

        if (totalQuestions > remainingQuota) {
            log.error("Total questions exceeds remaining quota: {} > {}", totalQuestions, remainingQuota);
            throw new ApiException("You can only generate " + remainingQuota + " more questions.",
                    HttpStatus.BAD_REQUEST.value());
        }

        log.info("Total questions to generate: {}", totalQuestions);


        ChallengeContext context = eagerLoadChallengeContext(challenge);

        InputValidationResponse validation = validateInputContent(
                request.getDescription(),
                request.getVocabularyList(),
                request.getLessonFocus(),
                request.getCustomLessonFocus(),
                null,  // sectionContent
                null,  // fileContent
                context,  // ChallengeContext
                ValidationType.GV,
                levelInfo
        );

        String descriptionToUse = validation.getTranslatedDescription() != null
                ? validation.getTranslatedDescription()
                : request.getDescription();

        String vocabularyToUse = validation.getTranslatedVocabularyList() != null
                ? validation.getTranslatedVocabularyList()
                : request.getVocabularyList();

        String customFocusToUse = validation.getTranslatedCustomLessonFocus() != null
                ? validation.getTranslatedCustomLessonFocus()
                : request.getCustomLessonFocus();

        log.info("Using translated inputs - Description: {}, Vocabulary: {}, Custom Focus: {}",
                validation.getTranslatedDescription() != null ? "✓" : "✗",
                validation.getTranslatedVocabularyList() != null ? "✓" : "✗",
                validation.getTranslatedCustomLessonFocus() != null ? "✓" : "✗");

        log.info("Level info - Name: {}, Description: {}", levelInfo.levelName, levelInfo.levelDescription);

        List<QuestionGenerationTask> allTasks = new ArrayList<>();
        int sectionOrder = 1;

        for (GenerateGVQuestionsRequest.QuestionTypeConfig config : request.getQuestionTypeConfigs()) {
            String questionType = config.getQuestionType();
            int numberOfQuestions = config.getNumberOfQuestions();
            String contextInfo = buildEnhancedContextInfo(questionType);

            log.info("Preparing {} {} questions", numberOfQuestions, questionType);

            for (int i = 0; i < numberOfQuestions; i++) {
                allTasks.add(new QuestionGenerationTask(
                        context,
                        questionType,
                        descriptionToUse,
                        contextInfo,
                        sectionOrder++,
                        levelInfo,
                        request.getLessonFocus(),
                        customFocusToUse,
                        vocabularyToUse
                ));
            }
        }

        Map<String, List<QuestionGenerationTask>> tasksByType = allTasks.stream()
                .collect(Collectors.groupingBy(task -> task.questionType));

        log.info("Grouped tasks into {} question types", tasksByType.size());

        List<CompletableFuture<List<QuestionWithOrderDto>>> futures = new ArrayList<>();

        for (Map.Entry<String, List<QuestionGenerationTask>> entry : tasksByType.entrySet()) {
            String questionType = entry.getKey();
            List<QuestionGenerationTask> tasksForType = entry.getValue();

            log.info("Processing {} tasks for question type: {}", tasksForType.size(), questionType);

            List<List<QuestionGenerationTask>> batches = splitIntoBatches(tasksForType, batchSize);

            for (List<QuestionGenerationTask> batch : batches) {
                CompletableFuture<List<QuestionWithOrderDto>> future = CompletableFuture.supplyAsync(
                        () -> generateBatchOfGVQuestions(batch),
                        executorService
                );
                futures.add(future);
            }
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allOf.get(generationTimeoutMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Error waiting for parallel batch completion: {}", e.getMessage(), e);
            throw new ApiException("Failed to generate questions in parallel: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        List<QuestionWithOrderDto> allGeneratedQuestions = futures.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        log.error("Error getting batch result: {}", e.getMessage());
                        return Collections.<QuestionWithOrderDto>emptyList();
                    }
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());

        allGeneratedQuestions.sort(Comparator.comparingInt(q -> q.originalSectionOrder));

        List<SectionWithQuestionsDto> results = new ArrayList<>();
        for (int i = 0; i < allGeneratedQuestions.size(); i++) {
            QuestionWithOrderDto qWithOrder = allGeneratedQuestions.get(i);
            QuestionDto question = qWithOrder.question;

            question.setId(null);
            question.setOrderNumber(1);

            SectionDto section = new SectionDto();
            section.setId(null);
            section.setSectionTitle(null);
            section.setSectionsContent(null);
            section.setOrderNumber(i + 1);
            section.setResourceType("NONE");

            results.add(new SectionWithQuestionsDto(section, Collections.singletonList(question)));
        }

        List<QuestionDto> allQuestions = results.stream()
                .flatMap(s -> s.getQuestions().stream())
                .collect(Collectors.toList());

        ensureUniquePositionIds(allQuestions);

        try {
            validateOutputFormat(allQuestions);
        } catch (Exception e) {
            log.error("Output validation failed: {}", e.getMessage());
            throw new ApiException("Generated questions have invalid format: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        log.info("Successfully generated {} sections with {} total questions",
                results.size(), results.size());

        // ✅ NEW: Return GenerateQuestionsResponse with error and warning
        return new GenerateQuestionsResponse(
                results,
                validation.getError(),
                validation.getWarning()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public GenerateQuestionsResponse generateContentBasedQuestions(GenerateContentBasedQuestionsRequest request) {
        log.info("Starting content-based question generation for challengeId: {}", request.getChallengeId());

        LevelInfo levelInfo = parseLevelInfo(request.getLevel());

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        int existingQuestionsCount = countExistingQuestions(challenge);
        int remainingQuota = 100 - existingQuestionsCount;

        log.info("Existing questions: {}, Remaining quota: {}/{}",
                existingQuestionsCount, remainingQuota, maxQuestion);

        if (remainingQuota <= 0) {
            log.error("Challenge already has maximum questions: {}/{}", existingQuestionsCount, maxQuestion);
            throw new ApiException("Challenge already has maximum " + maxQuestion +
                    " questions. Cannot generate more.", HttpStatus.BAD_REQUEST.value());
        }

        int totalQuestions = request.getSections().stream()
                .flatMap(section -> section.getQuestionTypeConfigs().stream())
                .mapToInt(GenerateContentBasedQuestionsRequest.QuestionTypeConfig::getNumberOfQuestions)
                .sum();

        if (totalQuestions > maxQuestion) {
            log.error("Total questions exceeds remaining quota: {} > {}", totalQuestions, maxQuestion);
            throw new ApiException("You can only generate up to " + maxQuestion +  " questions per request",
                    HttpStatus.BAD_REQUEST.value());
        }

        if (totalQuestions > remainingQuota) {
            log.error("Total questions exceeds remaining quota: {} > {}", totalQuestions, remainingQuota);
            throw new ApiException("You can only generate " + remainingQuota + " more questions.",
                    HttpStatus.BAD_REQUEST.value());
        }

        ChallengeContext context = eagerLoadChallengeContext(challenge);

        // ✅ Build section content for validation
        StringBuilder allSectionContent = new StringBuilder();
        for (GenerateContentBasedQuestionsRequest.SectionWithConfig sectionConfig : request.getSections()) {
            if (sectionConfig.getSection().getSectionsContent() != null) {
                allSectionContent.append(sectionConfig.getSection().getSectionsContent()).append("\n");
            }
        }

        // ✅ Start validation async - không wait kết quả
        CompletableFuture<InputValidationResponse> validationFuture = CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return validateInputContent(
                                request.getDescription(),
                                null,
                                null,
                                null,
                                allSectionContent.toString(),
                                null,
                                context,
                                ValidationType.CONTENT_BASED,
                                levelInfo
                        );
                    } catch (Exception e) {
                        log.error("Validation failed: {}", e.getMessage(), e);
                        return new InputValidationResponse(null, null, null, null, null);
                    }
                },
                executorService
        );

        String dailyChallengeType = challenge.getChallengeType().toString();

        log.info("Daily Challenge Type: {}, Level: {}", dailyChallengeType, levelInfo.levelName);

        List<SectionWithQuestionsDto> results = new ArrayList<>();
        String outputError = null;

        for (GenerateContentBasedQuestionsRequest.SectionWithConfig sectionConfig : request.getSections()) {
            SectionDto section = sectionConfig.getSection();

            log.info("Processing section: {} (ResourceType: {})",
                    section.getSectionTitle(), section.getResourceType());

            try {
                if (section.getSectionsContent() == null || section.getSectionsContent().isBlank()) {
                    throw new ApiException("Section content is required", HttpStatus.BAD_REQUEST.value());
                }

                String enhancedContent = section.getSectionsContent();

                SectionDto enhancedSection = new SectionDto();
                enhancedSection.setId(section.getId());
                enhancedSection.setSectionTitle(section.getSectionTitle());
                enhancedSection.setSectionsContent(enhancedContent);
                enhancedSection.setResourceType(section.getResourceType());
                enhancedSection.setSectionsUrl(section.getSectionsUrl());
                enhancedSection.setOrderNumber(section.getOrderNumber());

                List<ContentBasedQuestionTask> sectionTasks = new ArrayList<>();
                int questionOrder = 1;

                for (GenerateContentBasedQuestionsRequest.QuestionTypeConfig config :
                        sectionConfig.getQuestionTypeConfigs()) {
                    String questionType = config.getQuestionType();
                    int numberOfQuestions = config.getNumberOfQuestions();
                    String contextInfo = buildEnhancedContextInfo(questionType);

                    log.info("Preparing {} {} questions", numberOfQuestions, questionType);

                    for (int i = 0; i < numberOfQuestions; i++) {
                        sectionTasks.add(new ContentBasedQuestionTask(
                                context,
                                enhancedSection,
                                questionType,
                                request.getDescription(),
                                contextInfo,
                                dailyChallengeType,
                                questionOrder++,
                                levelInfo
                        ));
                    }
                }

                Map<String, List<ContentBasedQuestionTask>> tasksByType = sectionTasks.stream()
                        .collect(Collectors.groupingBy(task -> task.questionType));

                List<CompletableFuture<List<QuestionWithOrderDto>>> futures = new ArrayList<>();

                for (Map.Entry<String, List<ContentBasedQuestionTask>> entry : tasksByType.entrySet()) {
                    List<ContentBasedQuestionTask> tasksForType = entry.getValue();

                    List<List<ContentBasedQuestionTask>> batches = splitIntoBatches(tasksForType, batchSize);

                    for (List<ContentBasedQuestionTask> batch : batches) {
                        CompletableFuture<List<QuestionWithOrderDto>> future = CompletableFuture.supplyAsync(
                                () -> generateBatchOfContentBasedQuestions(batch),
                                executorService
                        );
                        futures.add(future);
                    }
                }

                CompletableFuture<Void> allOf = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0])
                );

                allOf.get(generationTimeoutMinutes, TimeUnit.MINUTES);

                List<QuestionWithOrderDto> allGeneratedQuestions = futures.stream()
                        .map(future -> {
                            try {
                                return future.get();
                            } catch (Exception e) {
                                log.error("Error getting batch result: {}", e.getMessage());
                                return Collections.<QuestionWithOrderDto>emptyList();
                            }
                        })
                        .flatMap(List::stream)
                        .collect(Collectors.toList());

                allGeneratedQuestions.sort(Comparator.comparingInt(q -> q.originalSectionOrder));

                List<QuestionDto> allQuestions = new ArrayList<>();
                for (int i = 0; i < allGeneratedQuestions.size(); i++) {
                    QuestionDto question = allGeneratedQuestions.get(i).question;
                    question.setId(null);
                    question.setOrderNumber(i + 1);
                    allQuestions.add(question);
                }

                ensureUniquePositionIds(allQuestions);

                // ✅ Validate output format
                try {
                    validateOutputFormat(allQuestions);
                } catch (Exception e) {
                    log.error("Output validation failed: {}", e.getMessage());
                    if (outputError == null) {
                        outputError = "Generated questions have invalid format: " + e.getMessage();
                    }
                }

                SectionWithQuestionsDto result = new SectionWithQuestionsDto(section, allQuestions);
                results.add(result);

                log.info("Generated {} questions for section", allQuestions.size());

            } catch (Exception e) {
                log.error("Failed to generate questions for section: {}", e.getMessage(), e);
                if (outputError == null) {
                    outputError = "Failed to generate questions: " + e.getMessage();
                }
            }
        }

        log.info("Successfully generated {} sections with {} total questions",
                results.size(), results.stream().mapToInt(s -> s.getQuestions().size()).sum());

        // ✅ Get validation result (không block vì đã chạy song song)
        InputValidationResponse validation;
        try {
            validation = validationFuture.get(5, TimeUnit.SECONDS); // timeout ngắn vì đã chạy song song
        } catch (Exception e) {
            log.error("Failed to get validation result: {}", e.getMessage());
            validation = new InputValidationResponse(null, null, null, null, null);
        }

        // ✅ Return với validation result
        return new GenerateQuestionsResponse(
                results,
                validation.getError(),
                validation.getWarning()
        );
    }

    private int countExistingQuestions(DailyChallenge challenge) {
        if (challenge.getSections() == null) {
            return 0;
        }

        return challenge.getSections().stream()
                .filter(section -> section.getDeletedAt() == null)
                .mapToInt(section -> {
                    if (section.getQuestions() == null) {
                        return 0;
                    }
                    return (int) section.getQuestions().stream()
                            .filter(question -> question.getDeletedAt() == null)
                            .count();
                })
                .sum();
    }

    public ChallengeContext eagerLoadChallengeContext(DailyChallenge challenge) {
        ChallengeContext context = new ChallengeContext();

        if (challenge.getClassLesson() != null) {
            context.classLessonContent = challenge.getClassLesson().getClassLessonContent();
            context.classLessonName = challenge.getClassLesson().getClassLessonName();

            if (challenge.getClassLesson().getClassChapter() != null) {
                Hibernate.initialize(challenge.getClassLesson().getClassChapter());
                context.classChapterName = challenge.getClassLesson().getClassChapter().getClassChapterName();

                if (challenge.getClassLesson().getClassChapter().getClazz() != null) {
                    Hibernate.initialize(challenge.getClassLesson().getClassChapter().getClazz());

                    if (challenge.getClassLesson().getClassChapter().getClazz().getSyllabus() != null) {
                        Syllabus syllabus = challenge.getClassLesson().getClassChapter().getClazz().getSyllabus();
                        Hibernate.initialize(syllabus);

                        if (syllabus.getLevel() != null) {
                            Hibernate.initialize(syllabus.getLevel());
                            context.studentLevel = syllabus.getLevel().getLevelName();
                        }
                    }
                }
            }
        }

        if (context.classLessonContent == null) {
            context.classLessonContent = "No lesson content available";
        }
        if (context.classLessonName == null) {
            context.classLessonName = "No lesson name available";
        }
        if (context.classChapterName == null) {
            context.classChapterName = "No chapter name available";
        }
        if (context.studentLevel == null) {
            context.studentLevel = "Intermediate";
        }

        log.debug("Loaded challenge context - Level: {}, Lesson: {}, ContentLength: {}",
                context.studentLevel, context.classLessonName, context.classLessonContent.length());

        return context;
    }

    private LevelInfo getLevelInfoFromDifficulty(DifficultyLevel difficulty) {
        LevelInfo info = new LevelInfo();
        info.levelName = difficulty.getDisplayName();
        info.levelDescription = difficulty.getDescription();
        info.learningObjective = "";
        return info;
    }

    @Transactional(readOnly = true)
    protected LevelInfo getLevelInfoFromDatabase(Long levelId) {
        Level level = levelRepository.findById(levelId)
                .orElseThrow(() -> new ApiException("Level not found with id: " + levelId,
                        HttpStatus.NOT_FOUND.value()));

        LevelInfo info = new LevelInfo();
        info.levelName = level.getLevelName();
        info.levelDescription = level.getDescription();
        info.learningObjective = level.getLearningObjectives();
        return info;
    }

    /**
     * Build content moderation instructions
     */
    private String getContentModerationInstructions() {
        StringBuilder instructions = new StringBuilder();
        instructions.append("🚨 CONTENT MODERATION (CRITICAL - MUST FOLLOW):\n\n");
        instructions.append("You MUST filter and reject ANY inappropriate content including:\n");
        instructions.append("❌ Violence, hate speech, discrimination, or offensive language\n");
        instructions.append("❌ Sexual, adult, or inappropriate content\n");
        instructions.append("❌ Profanity, vulgar language, or inappropriate slang\n");
        instructions.append("❌ Political propaganda or controversial ideologies\n");
        instructions.append("❌ Harmful, dangerous, or illegal activities\n");
        instructions.append("❌ Misleading, false, or deceptive information\n");
        instructions.append("❌ Personal attacks or cyberbullying content\n");
        instructions.append("❌ Drug abuse, alcohol abuse, or substance misuse\n");
        instructions.append("❌ Self-harm or mental health triggering content\n\n");
        instructions.append("✅ ONLY accept:\n");
        instructions.append("- Educational, age-appropriate content\n");
        instructions.append("- Positive, constructive topics\n");
        instructions.append("- Culturally sensitive and inclusive material\n");
        instructions.append("- Safe, ethical, and professional subject matter\n\n");
        instructions.append("If the user input contains ANY inappropriate content:\n");
        instructions.append("- IGNORE those parts completely\n");
        instructions.append("- Create questions based ONLY on appropriate lesson content\n");
        instructions.append("- DO NOT mention or reference the inappropriate content\n\n");
        instructions.append("⚠️ ABSOLUTE REJECTION: If the ENTIRE input is inappropriate with NO educational value:\n");
        instructions.append("- REFUSE to generate questions\n");
        instructions.append("- Return error stating content is not suitable for educational purposes\n\n");
        return instructions.toString();
    }

    private String buildLessonFocusPrompt(List<LessonFocus> lessonFocusList, String customLessonFocus) {
        StringBuilder prompt = new StringBuilder();

        if ((lessonFocusList != null && !lessonFocusList.isEmpty()) ||
                (customLessonFocus != null && !customLessonFocus.isBlank())) {

            prompt.append("🎯 LESSON FOCUS - Questions must target these specific learning points:\n\n");

            if (lessonFocusList != null && !lessonFocusList.isEmpty()) {
                for (LessonFocus focus : lessonFocusList) {
                    prompt.append("• ").append(focus.getDisplayName())
                            .append(": ").append(focus.getDescription()).append("\n");
                }
                prompt.append("\n");
            }

            if (customLessonFocus != null && !customLessonFocus.isBlank()) {
                prompt.append("• Custom Focus: ").append(customLessonFocus).append("\n\n");
            }

            prompt.append("CRITICAL: All questions MUST directly test the lesson focus areas listed above.\n");
            prompt.append("Questions should be designed specifically to assess student understanding of these points.\n\n");
        }

        return prompt.toString();
    }

    private String buildVocabularyPrompt(String vocabularyList) {
        if (vocabularyList != null && !vocabularyList.isBlank()) {
            StringBuilder prompt = new StringBuilder();
            prompt.append("📚 REQUIRED VOCABULARY - Prioritize using these words in questions:\n");
            prompt.append(vocabularyList).append("\n\n");
            prompt.append("IMPORTANT: Incorporate these vocabulary words naturally into questions where appropriate.\n");
            prompt.append("Use them in context to test student understanding of word usage and meaning.\n\n");
            return prompt.toString();
        }
        return "";
    }

    private String getDifficultyLevelInstructions(LevelInfo levelInfo) {
        StringBuilder instructions = new StringBuilder();

        instructions.append("📊 STUDENT LEVEL INFORMATION:\n\n");
        instructions.append("Level: ").append(levelInfo.levelName).append("\n");
        instructions.append("Description: ").append(levelInfo.levelDescription).append("\n");

        if (levelInfo.learningObjective != null && !levelInfo.learningObjective.isBlank()) {
            instructions.append("Learning Objective: ").append(levelInfo.learningObjective).append("\n");
        }

        instructions.append("\n");
        instructions.append("🎯 LEVEL-APPROPRIATE REQUIREMENTS:\n");
        instructions.append("All questions must be appropriate for this level:\n");
        instructions.append("- Vocabulary should match student proficiency\n");
        instructions.append("- Grammar complexity should be suitable\n");
        instructions.append("- Question difficulty should be challenging but achievable\n");
        instructions.append("- Content should be age and level appropriate\n\n");

        return instructions.toString();
    }

    public static class ChallengeContext {
        String classLessonContent;
        String classLessonName;
        String studentLevel;
        String classChapterName;
    }

    public static class LevelInfo {
        String levelName;
        String levelDescription;
        String learningObjective;
    }

    private List<QuestionWithOrderDto> generateBatchOfGVQuestions(List<QuestionGenerationTask> batch) {
        int maxRetries = batchRetryMaxAttempts;
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                attempt++;
                QuestionGenerationTask firstTask = batch.get(0);

                log.info("Generating batch of {} {} questions (attempt {}/{})",
                        batch.size(), firstTask.questionType, attempt, maxRetries);

                String prompt = buildBatchGVQuestionPrompt(
                        firstTask.context,
                        firstTask.questionType,
                        firstTask.userDescription,
                        firstTask.contextInfo,
                        batch.size(),
                        firstTask.levelInfo,
                        firstTask.lessonFocus,
                        firstTask.customLessonFocus,
                        firstTask.vocabularyList
                );

                String aiResponse = callOpenAI(prompt);
                List<QuestionDto> questions = parseQuestionsFromResponse(aiResponse);

                // Validate all questions
                for (QuestionDto question : questions) {
                    validateQuestion(question);
                }

                if (questions.size() > batch.size()) {
                    questions = questions.subList(0, batch.size());
                }

                List<QuestionWithOrderDto> result = new ArrayList<>();
                for (int i = 0; i < questions.size() && i < batch.size(); i++) {
                    result.add(new QuestionWithOrderDto(questions.get(i), batch.get(i).sectionOrder));
                }

                log.info("Successfully generated batch of {} questions on attempt {}", result.size(), attempt);
                return result;

            } catch (Exception e) {
                lastException = e;
                log.error("Failed to generate batch on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    log.info("Retrying batch generation...");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        log.error("Failed to generate batch after {} attempts: {}",
                maxRetries, lastException != null ? lastException.getMessage() : "unknown error");
        return Collections.emptyList();
    }

    private List<QuestionWithOrderDto> generateBatchOfContentBasedQuestions(List<ContentBasedQuestionTask> batch) {
        int maxRetries = 2;
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                attempt++;
                ContentBasedQuestionTask firstTask = batch.get(0);

                log.info("Generating batch of {} {} questions (attempt {}/{})",
                        batch.size(), firstTask.questionType, attempt, maxRetries);

                String prompt = buildBatchContentBasedQuestionPrompt(
                        firstTask.context,
                        firstTask.section,
                        firstTask.questionType,
                        batch.size(),
                        firstTask.userDescription,
                        firstTask.contextInfo,
                        firstTask.dailyChallengeType,
                        firstTask.levelInfo
                );

                String aiResponse = callOpenAI(prompt);
                List<QuestionDto> questions = parseQuestionsFromResponse(aiResponse);

                // Validate all questions
                for (QuestionDto question : questions) {
                    validateQuestion(question);
                }

                if (questions.size() > batch.size()) {
                    questions = questions.subList(0, batch.size());
                }

                List<QuestionWithOrderDto> result = new ArrayList<>();
                for (int i = 0; i < questions.size() && i < batch.size(); i++) {
                    result.add(new QuestionWithOrderDto(questions.get(i), batch.get(i).orderNumber));
                }

                log.info("Successfully generated batch of {} questions on attempt {}", result.size(), attempt);
                return result;

            } catch (Exception e) {
                lastException = e;
                log.error("Failed to generate batch on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    log.info("Retrying batch generation...");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        log.error("Failed to generate batch after {} attempts: {}",
                maxRetries, lastException != null ? lastException.getMessage() : "unknown error");
        return Collections.emptyList();
    }

    /**
     * Validate a single question based on its type
     */
    private void validateQuestion(QuestionDto question) {
        if (question == null) {
            throw new ApiException("Question cannot be null", HttpStatus.BAD_REQUEST.value());
        }

        if (question.getQuestionText() == null || question.getQuestionText().trim().isEmpty()) {
            throw new ApiException("Question text cannot be empty", HttpStatus.BAD_REQUEST.value());
        }

        if (question.getQuestionType() == null || question.getQuestionType().trim().isEmpty()) {
            throw new ApiException("Question type cannot be empty", HttpStatus.BAD_REQUEST.value());
        }

        if (question.getContent() == null || question.getContent().getData() == null ||
                question.getContent().getData().isEmpty()) {
            throw new ApiException("Question must have content data", HttpStatus.BAD_REQUEST.value());
        }

        String questionType = question.getQuestionType();
        List<DataItem> dataItems = question.getContent().getData();

        switch (questionType) {
            case "MULTIPLE_CHOICE":
                validateMultipleChoice(question, dataItems);
                break;
            case "TRUE_OR_FALSE":
                validateTrueOrFalse(question, dataItems);
                break;
            case "FILL_IN_THE_BLANK":
                validateFillInTheBlank(question, dataItems);
                break;
            case "DROPDOWN":
                validateDropdown(question, dataItems);
                break;
            case "REARRANGE":
                validateRearrange(question, dataItems);
                break;
            case "DRAG_AND_DROP":
                validateDragAndDrop(question, dataItems);
                break;
            case "MULTIPLE_SELECT":
                validateMultipleSelect(question, dataItems);
                break;
            case "REWRITE":
                validateRewrite(question, dataItems);
                break;
            default:
                log.warn("Unknown question type: {}, skipping specific validation", questionType);
        }

        log.debug("Question validated successfully: type={}, text={}", questionType,
                question.getQuestionText().substring(0, Math.min(50, question.getQuestionText().length())));
    }

    private void validateMultipleChoice(QuestionDto question, List<DataItem> dataItems) {
        if (dataItems.size() != 4) {
            throw new ApiException("MULTIPLE_CHOICE must have exactly 4 options. Found: " + dataItems.size(),
                    HttpStatus.BAD_REQUEST.value());
        }

        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != 1) {
            throw new ApiException("MULTIPLE_CHOICE must have exactly 1 correct answer. Found: " + correctCount,
                    HttpStatus.BAD_REQUEST.value());
        }

        for (DataItem item : dataItems) {
            if (item.getPositionId() != null) {
                throw new ApiException("MULTIPLE_CHOICE options must have positionId=null",
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateTrueOrFalse(QuestionDto question, List<DataItem> dataItems) {
        if (dataItems.size() != 2) {
            throw new ApiException("TRUE_OR_FALSE must have exactly 2 options. Found: " + dataItems.size(),
                    HttpStatus.BAD_REQUEST.value());
        }

        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != 1) {
            throw new ApiException("TRUE_OR_FALSE must have exactly 1 correct answer. Found: " + correctCount,
                    HttpStatus.BAD_REQUEST.value());
        }

        for (DataItem item : dataItems) {
            if (item.getPositionId() != null) {
                throw new ApiException("TRUE_OR_FALSE options must have positionId=null",
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateFillInTheBlank(QuestionDto question, List<DataItem> dataItems) {
        String questionText = question.getQuestionText();

        // Check for [[pos_xxx]] placeholders
        Matcher matcher = POSITION_PATTERN.matcher(questionText);
        Set<String> positionsInText = new HashSet<>();
        while (matcher.find()) {
            positionsInText.add(matcher.group(1));
        }

        if (positionsInText.isEmpty()) {
            throw new ApiException("FILL_IN_THE_BLANK must contain [[pos_xxx]] placeholders",
                    HttpStatus.BAD_REQUEST.value());
        }

        // ✅ CRITICAL: All answers must be correct AND only 1 answer allowed
        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != 1) {
            throw new ApiException("FILL_IN_THE_BLANK must have exactly 1 correct answer. Found: " + correctCount,
                    HttpStatus.BAD_REQUEST.value());
        }

        if (dataItems.size() != 1) {
            throw new ApiException("FILL_IN_THE_BLANK can only have 1 answer. Found: " + dataItems.size(),
                    HttpStatus.BAD_REQUEST.value());
        }

        // Each answer must have positionId matching placeholder
        for (DataItem item : dataItems) {
            if (!item.isCorrect()) {
                throw new ApiException("FILL_IN_THE_BLANK all answers must have isCorrect=true",
                        HttpStatus.BAD_REQUEST.value());
            }

            if (item.getPositionId() == null || item.getPositionId().trim().isEmpty()) {
                throw new ApiException("FILL_IN_THE_BLANK answers must have valid positionId",
                        HttpStatus.BAD_REQUEST.value());
            }
            if (!positionsInText.contains(item.getPositionId())) {
                throw new ApiException("FILL_IN_THE_BLANK answer positionId '" + item.getPositionId() +
                        "' not found in question text", HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateDropdown(QuestionDto question, List<DataItem> dataItems) {
        String questionText = question.getQuestionText();

        Matcher matcher = POSITION_PATTERN.matcher(questionText);
        Set<String> positionsInText = new HashSet<>();
        while (matcher.find()) {
            positionsInText.add(matcher.group(1));
        }

        if (positionsInText.isEmpty()) {
            throw new ApiException("DROPDOWN must contain [[pos_xxx]] placeholders",
                    HttpStatus.BAD_REQUEST.value());
        }

        // Group by positionId
        Map<String, List<DataItem>> itemsByPosition = dataItems.stream()
                .filter(item -> item.getPositionId() != null)
                .collect(Collectors.groupingBy(DataItem::getPositionId));

        for (String posId : positionsInText) {
            List<DataItem> options = itemsByPosition.get(posId);
            if (options == null || options.size() != 4) {
                throw new ApiException("DROPDOWN position '" + posId + "' must have exactly 4 options. Found: " +
                        (options == null ? 0 : options.size()), HttpStatus.BAD_REQUEST.value());
            }

            long correctCount = options.stream().filter(DataItem::isCorrect).count();
            if (correctCount != 1) {
                throw new ApiException("DROPDOWN position '" + posId +
                        "' must have exactly 1 correct answer. Found: " + correctCount,
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateRearrange(QuestionDto question, List<DataItem> dataItems) {
        String questionText = question.getQuestionText();

        Matcher matcher = POSITION_PATTERN.matcher(questionText);
        Set<String> positionsInText = new HashSet<>();
        while (matcher.find()) {
            positionsInText.add(matcher.group(1));
        }

        if (positionsInText.isEmpty()) {
            throw new ApiException("REARRANGE must contain [[pos_xxx]] placeholders",
                    HttpStatus.BAD_REQUEST.value());
        }

        if (positionsInText.size() < 5 || positionsInText.size() > 8) {
            throw new ApiException("REARRANGE must have 5-8 items. Found: " + positionsInText.size(),
                    HttpStatus.BAD_REQUEST.value());
        }

        // All items must be correct
        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != dataItems.size()) {
            throw new ApiException("REARRANGE all items must have isCorrect=true. Found " +
                    (dataItems.size() - correctCount) + " incorrect items",
                    HttpStatus.BAD_REQUEST.value());
        }

        // Each item must have unique positionId
        for (DataItem item : dataItems) {
            if (item.getPositionId() == null || item.getPositionId().trim().isEmpty()) {
                throw new ApiException("REARRANGE items must have valid positionId",
                        HttpStatus.BAD_REQUEST.value());
            }
            if (!positionsInText.contains(item.getPositionId())) {
                throw new ApiException("REARRANGE item positionId '" + item.getPositionId() +
                        "' not found in question text", HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateDragAndDrop(QuestionDto question, List<DataItem> dataItems) {
        String questionText = question.getQuestionText();

        Matcher matcher = POSITION_PATTERN.matcher(questionText);
        Set<String> positionsInText = new HashSet<>();
        while (matcher.find()) {
            positionsInText.add(matcher.group(1));
        }

        if (positionsInText.isEmpty()) {
            throw new ApiException("DRAG_AND_DROP must contain [[pos_xxx]] placeholders",
                    HttpStatus.BAD_REQUEST.value());
        }

        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != positionsInText.size()) {
            throw new ApiException("DRAG_AND_DROP must have " + positionsInText.size() +
                    " correct answers (matching placeholders). Found: " + correctCount,
                    HttpStatus.BAD_REQUEST.value());
        }

        for (DataItem item : dataItems) {
            if (item.isCorrect()) {
                if (item.getPositionId() == null || item.getPositionId().trim().isEmpty()) {
                    throw new ApiException("DRAG_AND_DROP correct answers must have valid positionId",
                            HttpStatus.BAD_REQUEST.value());
                }
                if (!positionsInText.contains(item.getPositionId())) {
                    throw new ApiException("DRAG_AND_DROP answer positionId '" + item.getPositionId() +
                            "' not found in question text", HttpStatus.BAD_REQUEST.value());
                }
            } else {
                // Distractors should have positionId=null
                if (item.getPositionId() != null) {
                    log.warn("DRAG_AND_DROP distractor should have positionId=null");
                }
            }
        }
    }

    private void validateMultipleSelect(QuestionDto question, List<DataItem> dataItems) {
        if (dataItems.size() < 4 || dataItems.size() > 6) {
            throw new ApiException("MULTIPLE_SELECT must have 4-6 options. Found: " + dataItems.size(),
                    HttpStatus.BAD_REQUEST.value());
        }

        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount < 2 || correctCount > 3) {
            throw new ApiException("MULTIPLE_SELECT must have 2-3 correct answers. Found: " + correctCount,
                    HttpStatus.BAD_REQUEST.value());
        }

        for (DataItem item : dataItems) {
            if (item.getPositionId() != null) {
                throw new ApiException("MULTIPLE_SELECT options must have positionId=null",
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private void validateRewrite(QuestionDto question, List<DataItem> dataItems) {
        // All answers must be correct
        long correctCount = dataItems.stream().filter(DataItem::isCorrect).count();
        if (correctCount != dataItems.size()) {
            throw new ApiException("REWRITE can only have correct answers (isCorrect=true). Found " +
                    (dataItems.size() - correctCount) + " incorrect answers",
                    HttpStatus.BAD_REQUEST.value());
        }

        for (DataItem item : dataItems) {
            if (item.getPositionId() != null) {
                throw new ApiException("REWRITE answers must have positionId=null",
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    private <T> List<List<T>> splitIntoBatches(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(new ArrayList<>(list.subList(i, end)));
        }
        return batches;
    }

    private static class QuestionGenerationTask {
        ChallengeContext context;
        String questionType;
        String userDescription;
        String contextInfo;
        int sectionOrder;
        LevelInfo levelInfo;
        List<LessonFocus> lessonFocus;
        String customLessonFocus;
        String vocabularyList;

        QuestionGenerationTask(ChallengeContext context, String questionType, String userDescription,
                               String contextInfo, int sectionOrder, LevelInfo levelInfo,
                               List<LessonFocus> lessonFocus, String customLessonFocus, String vocabularyList) {
            this.context = context;
            this.questionType = questionType;
            this.userDescription = userDescription;
            this.contextInfo = contextInfo;
            this.sectionOrder = sectionOrder;
            this.levelInfo = levelInfo;
            this.lessonFocus = lessonFocus;
            this.customLessonFocus = customLessonFocus;
            this.vocabularyList = vocabularyList;
        }
    }

    private static class ContentBasedQuestionTask {
        ChallengeContext context;
        SectionDto section;
        String questionType;
        String userDescription;
        String contextInfo;
        String dailyChallengeType;
        int orderNumber;
        LevelInfo levelInfo;

        ContentBasedQuestionTask(ChallengeContext context, SectionDto section, String questionType,
                                 String userDescription, String contextInfo, String dailyChallengeType,
                                 int orderNumber, LevelInfo levelInfo) {
            this.context = context;
            this.section = section;
            this.questionType = questionType;
            this.userDescription = userDescription;
            this.contextInfo = contextInfo;
            this.dailyChallengeType = dailyChallengeType;
            this.orderNumber = orderNumber;
            this.levelInfo = levelInfo;
        }
    }

    private static class QuestionWithOrderDto {
        QuestionDto question;
        int originalSectionOrder;

        QuestionWithOrderDto(QuestionDto question, int originalSectionOrder) {
            this.question = question;
            this.originalSectionOrder = originalSectionOrder;
        }
    }

    private String buildBatchGVQuestionPrompt(
            ChallengeContext context,
            String questionType,
            String userDescription,
            String contextInfo,
            int numberOfQuestions,
            LevelInfo levelInfo,
            List<LessonFocus> lessonFocus,
            String customLessonFocus,
            String vocabularyList) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an experienced English teacher working at a reputable English language center.\n\n");

        // Content moderation
        prompt.append(getContentModerationInstructions());

        prompt.append(getDifficultyLevelInstructions(levelInfo));

        prompt.append("📖 LESSON CONTEXT:\n");
        prompt.append("Lesson Name: ").append(context.classLessonName).append("\n");
        prompt.append("Chapter: ").append(context.classChapterName).append("\n");
        prompt.append("Lesson Content:\n").append(context.classLessonContent).append("\n\n");

        prompt.append(buildLessonFocusPrompt(lessonFocus, customLessonFocus));
        prompt.append(buildVocabularyPrompt(vocabularyList));

        prompt.append("You are responsible for creating professional, age-appropriate, lesson-aligned English test questions.\n\n");
        prompt.append("Always analyze the lesson content and chapter topic carefully before writing questions.\n");
        prompt.append("Your questions must directly test the grammar, vocabulary, and language skills actually taught in the current lesson, not random English knowledge.\n\n");

        prompt.append("🌍 LANGUAGE REQUIREMENT:\n");
        prompt.append("- ALL questions MUST be in English ONLY\n");
        prompt.append("- ALL answer options MUST be in English ONLY\n");
        prompt.append("- Do NOT use any other language (Vietnamese, etc.)\n\n");

        prompt.append("Each question must:\n");
        prompt.append("- Match the student's level: ").append(levelInfo.levelName).append("\n");
        prompt.append("- Be written in natural, clear, age-appropriate English.\n");
        prompt.append(getGrammarAndFormattingInstructions());
        prompt.append("- Have plausible distractors and one clear correct answer.\n");

        if (userDescription != null && !userDescription.isBlank()) {
            prompt.append("💡 ADDITIONAL SUGGESTIONS (OPTIONAL - USE ONLY IF RELEVANT AND APPROPRIATE):\n");
            prompt.append(userDescription).append("\n\n");
            prompt.append("⚠️ IMPORTANT INSTRUCTION FOR USER SUGGESTIONS:\n");
            prompt.append("- These suggestions are SECONDARY and OPTIONAL\n");
            prompt.append("- ONLY apply suggestions that are:\n");
            prompt.append("  • Relevant to the lesson content\n");
            prompt.append("  • Appropriate and educational\n");
            prompt.append("  • Safe and positive\n");
            prompt.append("- If suggestions contain inappropriate content → IGNORE them completely\n");
            prompt.append("- If suggestions contradict or are unrelated to the lesson → IGNORE them\n");
            prompt.append("- Lesson content alignment and appropriateness are ALWAYS the top priorities\n\n");
        }

        prompt.append("TASK:\n");
        prompt.append("Generate EXACTLY ").append(numberOfQuestions).append(" HIGH-QUALITY ").append(questionType).append(" questions\n");
        prompt.append("Level: ").append(levelInfo.levelName).append("\n\n");

        appendJSONFormat(prompt, questionType);
        appendQuestionTypeRules(prompt, questionType, "GV");

        prompt.append("\n✅ FINAL CHECKLIST:\n");
        prompt.append("□ Questions aligned with lesson content\n");
        prompt.append("□ Appropriate for level: ").append(levelInfo.levelName).append("\n");
        if (lessonFocus != null && !lessonFocus.isEmpty()) {
            prompt.append("□ Tests specified lesson focus areas\n");
        }
        if (vocabularyList != null && !vocabularyList.isBlank()) {
            prompt.append("□ Incorporates required vocabulary\n");
        }
        prompt.append("□ Professional THPT QG standard\n");
        prompt.append("□ ALL content in English only\n");
        prompt.append("□ No inappropriate content\n");
        prompt.append("□ Valid JSON format\n");
        prompt.append("□ Exactly ").append(numberOfQuestions);
        prompt.append("Generate professional exam-quality questions now:\n");

        return prompt.toString();
    }

    private String buildBatchContentBasedQuestionPrompt(
            ChallengeContext context,
            SectionDto section,
            String questionType,
            int numberOfQuestions,
            String userDescription,
            String contextInfo,
            String dailyChallengeType,
            LevelInfo levelInfo) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an experienced English teacher working at a reputable English language center.\n\n");

        // Content moderation
        prompt.append(getContentModerationInstructions());

        prompt.append(getDifficultyLevelInstructions(levelInfo));

        prompt.append("📖 LESSON CONTEXT:\n");
        prompt.append("Lesson Name: ").append(context.classLessonName).append("\n");
        prompt.append("Chapter: ").append(context.classChapterName).append("\n\n");

        prompt.append("CHALLENGE TYPE: ").append(dailyChallengeType).append("\n");
        appendDCTypeInstructions(prompt, dailyChallengeType);

        prompt.append("PASSAGE TO CREATE QUESTIONS FROM:\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        prompt.append(section.getSectionsContent()).append("\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        prompt.append("🌍 LANGUAGE REQUIREMENT:\n");
        prompt.append("- The passage above MUST be in English\n");
        prompt.append("- ALL questions MUST be in English ONLY\n");
        prompt.append("- ALL answer options MUST be in English ONLY\n");
        prompt.append("- Do NOT use any other language\n\n");

        prompt.append("🚨 CRITICAL CONTENT-BASED REQUIREMENTS (MUST FOLLOW):\n");
        prompt.append("- ALL questions MUST be answerable ONLY from the passage above\n");
        prompt.append("- ONLY ask about information, facts, details, or vocabulary that EXISTS in the passage\n");
        prompt.append("- DO NOT ask about general English grammar rules\n");
        prompt.append("- DO NOT ask about knowledge not mentioned in the passage\n");
        prompt.append("- DO NOT create questions testing skills beyond passage comprehension\n\n");

        prompt.append("Each question must:\n");
        prompt.append("- Match the student's level: ").append(levelInfo.levelName).append("\n");
        prompt.append("- Be written in natural, clear, age-appropriate English.\n");
        prompt.append(getGrammarAndFormattingInstructions());
        prompt.append("- Have plausible distractors and one clear correct answer.\n");

        prompt.append("✅ ALLOWED QUESTION TYPES:\n");
        prompt.append("- Main idea / purpose questions based on passage content\n");
        prompt.append("- Detail questions about specific information in the passage\n");
        prompt.append("- Inference questions that can be answered from passage clues\n");
        prompt.append("- Vocabulary questions about words/phrases used IN THE PASSAGE\n");
        prompt.append("- Reference questions (what does 'it/they/this' refer to in the passage)\n\n");

        prompt.append("❌ FORBIDDEN QUESTION TYPES:\n");
        prompt.append("- Grammar rules not demonstrated in the passage\n");
        prompt.append("- Vocabulary not present in the passage\n");
        prompt.append("- General knowledge questions\n");
        prompt.append("- Questions requiring external information\n\n");

        prompt.append("📝 PARAPHRASING RULES:\n");
        prompt.append("- You MAY paraphrase words/phrases from the passage in questions and options\n");
        prompt.append("- Paraphrasing level should match difficulty level\n");
        prompt.append("- Keep the SAME meaning as in the passage\n");
        prompt.append("- The correct answer must match passage information (even if paraphrased)\n\n");

        prompt.append("⚠️ VALIDATION BEFORE GENERATING:\n");
        prompt.append("For EVERY question, ask yourself:\n");
        prompt.append("1. Can this be answered by ONLY reading the passage?\n");
        prompt.append("2. Is the information needed in the passage?\n");
        prompt.append("3. Would someone who hasn't read the passage struggle to answer?\n");
        prompt.append("If ANY answer is NO → DO NOT create that question\n\n");

        if (userDescription != null && !userDescription.isBlank()) {
            prompt.append("💡 ADDITIONAL SUGGESTIONS (OPTIONAL - USE ONLY IF RELEVANT AND APPROPRIATE):\n");
            prompt.append(userDescription).append("\n\n");
            prompt.append("⚠️ IMPORTANT INSTRUCTION FOR USER SUGGESTIONS:\n");
            prompt.append("- These suggestions are SECONDARY and OPTIONAL\n");
            prompt.append("- ONLY apply suggestions that are:\n");
            prompt.append("  • Relevant to the lesson content\n");
            prompt.append("  • Appropriate and educational\n");
            prompt.append("  • Safe and positive\n");
            prompt.append("- If suggestions contain inappropriate content → IGNORE them completely\n");
            prompt.append("- If suggestions contradict or are unrelated to the lesson → IGNORE them\n");
            prompt.append("- Lesson content alignment and appropriateness are ALWAYS the top priorities\n\n");
        }

        prompt.append("TASK:\n");
        prompt.append("Generate EXACTLY ").append(numberOfQuestions).append(" ").append(questionType).append(" questions about the passage\n");
        prompt.append("Level: ").append(levelInfo.levelName).append("\n\n");

        appendJSONFormat(prompt, questionType);
        appendQuestionTypeRules(prompt, questionType, "BASED");

        prompt.append("\n✅ FINAL CHECKLIST:\n");
        prompt.append("□ Questions based on passage content\n");
        prompt.append("□ Appropriate for level: ").append(levelInfo.levelName).append("\n");
        prompt.append("□ ALL content in English only\n");
        prompt.append("□ No inappropriate content\n");
        prompt.append("□ Valid JSON format\n");
        prompt.append("□ Exactly ").append(numberOfQuestions).append(" questions\n\n");

        prompt.append("Generate professional exam-quality questions now:\n");

        return prompt.toString();
    }

    private void appendDCTypeInstructions(StringBuilder prompt, String dcType) {
        switch (dcType) {
            case "RE":
                prompt.append("📖 READING COMPREHENSION:\n");
                prompt.append("- Base ALL questions on the section content (reading passage)\n");
                prompt.append("- Test comprehension, inference, vocabulary in context\n");
                prompt.append("- Questions should reference specific parts of the passage\n");
                prompt.append("- Ensure questions can ONLY be answered by reading the passage\n\n");
                break;
            case "LI":
                prompt.append("🎧 LISTENING COMPREHENSION:\n");
                prompt.append("- Base ALL questions on the section content (transcript)\n");
                prompt.append("- Test listening comprehension and understanding\n");
                prompt.append("- Questions should reference specific information from the transcript\n");
                prompt.append("- Ensure questions can ONLY be answered by understanding the transcript\n\n");
                break;
            default:
                prompt.append("- Generate questions based on the section content\n\n");
        }
    }

    private void appendJSONFormat(StringBuilder prompt, String questionType) {
        prompt.append("📋 JSON FORMAT:\n");
        prompt.append("{\n");
        prompt.append("  \"questions\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"questionText\": \"string (required)\",\n");
        prompt.append("      \"orderNumber\": 1,\n");
        prompt.append("      \"score\": 1.0,\n");
        prompt.append("      \"questionType\": \"").append(questionType).append("\",\n");
        prompt.append("      \"content\": {\n");
        prompt.append("        \"data\": [\n");
        prompt.append("          {\n");
        prompt.append("            \"id\": \"string (required)\",\n");
        prompt.append("            \"value\": \"string (required)\",\n");
        prompt.append("            \"isCorrect\": boolean (required),\n");
        prompt.append("            \"positionId\": \"string or null\"\n");
        prompt.append("          }\n");
        prompt.append("        ]\n");
        prompt.append("      }\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
    }

    private void appendQuestionTypeRules(StringBuilder prompt, String questionType, String dailyChallengeType) {
        prompt.append("📚 DETAILED SPECIFICATIONS FOR ").append(questionType).append(":\n\n");

        switch (questionType) {
            case "MULTIPLE_CHOICE":
                prompt.append("FORMAT: Clear question stem + 4 options (A, B, C, D)\n");
                prompt.append("REQUIREMENTS:\n");
                prompt.append("- Exactly 4 options per question\n");
                prompt.append("- Exactly 1 option with isCorrect=true\n");
                prompt.append("- positionId=null for all options\n\n");
                prompt.append("EXAMPLE:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"My brother _____ in London for three years before he moved to Paris.\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"MULTIPLE_CHOICE\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"null\", \"value\": \"lived\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"null\", \"value\": \"was living\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"null\", \"value\": \"had lived\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"null\", \"value\": \"has lived\", \"isCorrect\": false, \"positionId\": null}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");
                break;

            case "TRUE_OR_FALSE":
                prompt.append("REQUIREMENTS:\n");
                prompt.append("- 2 options: \"True\" and \"False\"\n");
                prompt.append("- Exactly 1 option with isCorrect=true\n");
                prompt.append("- positionId=null for both\n\n");
                prompt.append("EXAMPLE:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"According to the passage, the author believes that technology has improved education.\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"TRUE_OR_FALSE\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"null\", \"value\": \"True\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"null\", \"value\": \"False\", \"isCorrect\": false, \"positionId\": null}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");
                break;

            case "FILL_IN_THE_BLANK":
                prompt.append("⚠️ CRITICAL REQUIREMENTS:\n");
                prompt.append("- EXACTLY 1 correct answer only (NOT multiple answers)\n");
                prompt.append("- The single answer MUST have isCorrect=true\n");
                prompt.append("- NO alternative or multiple correct answers allowed\n\n");

                if (dailyChallengeType != null && dailyChallengeType.equals("GV")) {
                    prompt.append("⚠️ FORMAT FOR GV (GRAMMAR/VOCABULARY) QUESTIONS:\n\n");

                    prompt.append("📌 TYPE 1: WORD FORM / VERB CONJUGATION (with hint)\n");
                    prompt.append("When testing grammar (verb tenses, word forms), provide the BASE WORD as a hint:\n");
                    prompt.append("FORMAT: \"Text [[pos_xxxxxx]](base word) more text.\"\n");
                    prompt.append("- The word in parentheses ( ) is the BASE/INFINITIVE form\n");
                    prompt.append("- Students must transform it to the correct form\n");
                    prompt.append("EXAMPLES:\n");
                    prompt.append("• Verb tense: \"If I [[pos_a7k3m2]](know) her address, I would visit her.\" → knew\n");
                    prompt.append("• Word form: \"She speaks English [[pos_b8n4k1]](fluent).\" → fluently\n");
                    prompt.append("• Verb form: \"He enjoys [[pos_c2m9p5]](read) books.\" → reading\n\n");

                    prompt.append("📌 TYPE 2: VOCABULARY / CONTENT WORDS (without hint)\n");
                    prompt.append("When testing vocabulary or lesson content, NO hint needed:\n");
                    prompt.append("FORMAT: \"Text [[pos_xxxxxx]] more text.\"\n");
                    prompt.append("- Students must recall the word from context or lesson vocabulary\n");
                    prompt.append("EXAMPLES:\n");
                    prompt.append("• Vocabulary: \"A place where you buy books is called a [[pos_d3t6r8]].\" → bookstore\n");
                    prompt.append("• Preposition: \"She is good [[pos_e5h2w9]] mathematics.\" → at\n");
                    prompt.append("• Collocation: \"Please [[pos_f7k4n1]] attention to the lesson.\" → pay\n\n");

                    prompt.append("COMPLETE EXAMPLE (Grammar with hint):\n");
                    prompt.append("{\n")
                            .append("  \"questionText\": \"If I [[pos_a7k3m2]](know) her address, I would visit her tomorrow.\",\n")
                            .append("  \"orderNumber\": 1,\n")
                            .append("  \"score\": 1.0,\n")
                            .append("  \"questionType\": \"FILL_IN_THE_BLANK\",\n")
                            .append("  \"content\": {\n")
                            .append("    \"data\": [\n")
                            .append("      {\"id\": \"ans1\", \"value\": \"knew\", \"isCorrect\": true, \"positionId\": \"a7k3m2\"}\n")
                            .append("    ]\n")
                            .append("  }\n")
                            .append("}\n\n");

                    prompt.append("COMPLETE EXAMPLE (Vocabulary without hint):\n");
                    prompt.append("{\n")
                            .append("  \"questionText\": \"She is very good [[pos_t9m3k7]] playing the piano.\",\n")
                            .append("  \"orderNumber\": 1,\n")
                            .append("  \"score\": 1.0,\n")
                            .append("  \"questionType\": \"FILL_IN_THE_BLANK\",\n")
                            .append("  \"content\": {\n")
                            .append("    \"data\": [\n")
                            .append("      {\"id\": \"ans1\", \"value\": \"at\", \"isCorrect\": true, \"positionId\": \"t9m3k7\"}\n")
                            .append("    ]\n")
                            .append("  }\n")
                            .append("}\n\n");

                } else {
                    prompt.append("⚠️ FORMAT FOR CONTENT-BASED QUESTIONS (READING/LISTENING):\n\n");

                    prompt.append("📌 CRITICAL: NO HINTS ALLOWED\n");
                    prompt.append("Fill in the blank for reading/listening tests information recall from the passage/audio.\n");
                    prompt.append("Students must find the answer IN THE PASSAGE/TRANSCRIPT.\n");
                    prompt.append("FORMAT: \"Text [[pos_xxxxxx]] more text.\"\n");
                    prompt.append("- NO parentheses ( ) hints\n");
                    prompt.append("- Answer must be a word/phrase that appears in or can be inferred from the passage\n\n");

                    prompt.append("QUESTION TYPES:\n");
                    prompt.append("• Factual detail: \"According to the passage, the author moved to [[pos_a7k3m2]] in 2010.\"\n");
                    prompt.append("• Number/Date: \"The research was conducted over [[pos_b8n4k1]] years.\"\n");
                    prompt.append("• Key term: \"The process of plants making food is called [[pos_c2m9p5]].\"\n");
                    prompt.append("• Summary: \"The main character felt [[pos_d3t6r8]] after hearing the news.\"\n\n");

                    prompt.append("❌ WRONG (Don't do this for content-based):\n");
                    prompt.append("• \"The author [[pos_xxx]](move) to London in 2010.\" ← NO! This is grammar, not content\n");
                    prompt.append("• \"She [[pos_xxx]](feel) happy.\" ← NO! This tests grammar, not reading comprehension\n\n");

                    prompt.append("✅ CORRECT:\n");
                    prompt.append("• \"The author moved to [[pos_xxx]] in 2010.\" → London (from passage)\n");
                    prompt.append("• \"She felt [[pos_xxx]] after the news.\" → happy/sad/surprised (from passage)\n\n");

                    prompt.append("COMPLETE EXAMPLE:\n");
                    prompt.append("{\n")
                            .append("  \"questionText\": \"According to the passage, the scientist discovered the element in [[pos_a7k3m2]].\",\n")
                            .append("  \"orderNumber\": 1,\n")
                            .append("  \"score\": 1.0,\n")
                            .append("  \"questionType\": \"FILL_IN_THE_BLANK\",\n")
                            .append("  \"content\": {\n")
                            .append("    \"data\": [\n")
                            .append("      {\"id\": \"ans1\", \"value\": \"1869\", \"isCorrect\": true, \"positionId\": \"a7k3m2\"}\n")
                            .append("    ]\n")
                            .append("  }\n")
                            .append("}\n\n");

                    prompt.append("REMEMBER:\n");
                    prompt.append("- Reading/Listening fill-in-the-blank = FIND information from passage\n");
                    prompt.append("- NOT about grammar transformation\n");
                    prompt.append("- NO hints in parentheses ( )\n\n");
                }
                break;

            case "DROPDOWN":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders\n");
                prompt.append("- Each dropdown has exactly 4 options, exactly 1 with isCorrect=true\n");
                prompt.append("- All options for one dropdown share the same positionId\n\n");
                prompt.append("- Option ID naming rule:\n");
                prompt.append("  + Correct option: optN\n");
                prompt.append("  + Other options: optN_1, optN_2, optN_3\n");
                prompt.append("  + N increases sequentially for each dropdown in a section (opt1, opt2, ...)\n\n");
                prompt.append("EXAMPLE:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"The company [[pos_k5l6m7]] expand into Asian markets next year.\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"DROPDOWN\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"opt1\", \"value\": \"plans to\", \"isCorrect\": true, \"positionId\": \"k5l6m7\"},\n")
                        .append("      {\"id\": \"opt1_1\", \"value\": \"is planning\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"},\n")
                        .append("      {\"id\": \"opt1_2\", \"value\": \"will plan\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"},\n")
                        .append("      {\"id\": \"opt1_3\", \"value\": \"planned\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");
                break;

            case "REARRANGE":
                if ("GV".equalsIgnoreCase(dailyChallengeType)) {
                    // Grammar: Sắp xếp từ thành câu
                    prompt.append("⚠️ REARRANGE TYPE: GRAMMAR - Sắp xếp TỪ/CỤM TỪ thành CÂU đúng\n\n");
                    prompt.append("CRITICAL FORMAT:\n");
                    prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders for EACH word/phrase\n");
                    prompt.append("- Each item in data[] is a WORD or SHORT PHRASE (1-3 words max)\n");
                    prompt.append("- Must form a COMPLETE, GRAMMATICALLY CORRECT sentence\n");
                    prompt.append("- Use 5-10 words/phrases total\n");
                    prompt.append("- Items in data[] should be SHUFFLED (not in correct order)\n");
                    prompt.append("- Placeholders in questionText must be in CORRECT order\n\n");
                    prompt.append("EXAMPLE:\n");
                    prompt.append("{\n")
                            .append("  \"questionText\": \"Rearrange the words: [[pos_a1b2c3]] [[pos_d4e5f6]] [[pos_g7h8i9]] [[pos_j1k2l3]] [[pos_m4n5o6]]\",\n")
                            .append("  \"orderNumber\": 1,\n")
                            .append("  \"score\": 1.0,\n")
                            .append("  \"questionType\": \"REARRANGE\",\n")
                            .append("  \"content\": {\n")
                            .append("    \"data\": [\n")
                            .append("      {\"id\": \"item1\", \"value\": \"has been\", \"isCorrect\": true, \"positionId\": \"d4e5f6\"},\n")
                            .append("      {\"id\": \"item2\", \"value\": \"She\", \"isCorrect\": true, \"positionId\": \"a1b2c3\"},\n")
                            .append("      {\"id\": \"item3\", \"value\": \"English\", \"isCorrect\": true, \"positionId\": \"j1k2l3\"},\n")
                            .append("      {\"id\": \"item4\", \"value\": \"studying\", \"isCorrect\": true, \"positionId\": \"g7h8i9\"},\n")
                            .append("      {\"id\": \"item5\", \"value\": \"recently\", \"isCorrect\": true, \"positionId\": \"m4n5o6\"}\n")
                            .append("    ]\n")
                            .append("  }\n")
                            .append("}\n");
                    prompt.append("Correct answer: She has been studying English recently\n\n");
                } else {
                    // Reading/Listening: Sắp xếp events/paragraphs
                    prompt.append("⚠️ REARRANGE TYPE: READING/LISTENING - Sắp xếp SỰ KIỆN hoặc ĐOẠN VĂN theo thứ tự logic\n\n");
                    prompt.append("CRITICAL FORMAT:\n");
                    prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders for EACH event/paragraph\n");
                    prompt.append("- Each item in data[] is a COMPLETE SENTENCE or PARAGRAPH\n");
                    prompt.append("- Use 4-6 items total\n");
                    prompt.append("- For LISTENING: Events in chronological order from the audio\n");
                    prompt.append("- For READING: Paragraphs in logical order (use discourse markers: First, However, Finally)\n");
                    prompt.append("- Items in data[] should be SHUFFLED (not in correct order)\n");
                    prompt.append("- Placeholders in questionText must be in CORRECT order\n\n");
                    prompt.append("EXAMPLE (Listening - Events):\n");
                    prompt.append("{\n")
                            .append("  \"questionText\": \"Listen and arrange events: [[pos_a1b2]] [[pos_c3d4]] [[pos_e5f6]] [[pos_g7h8]]\",\n")
                            .append("  \"orderNumber\": 1,\n")
                            .append("  \"score\": 1.0,\n")
                            .append("  \"questionType\": \"REARRANGE\",\n")
                            .append("  \"content\": {\n")
                            .append("    \"instruction\": \"Put the events in chronological order.\",\n")
                            .append("    \"data\": [\n")
                            .append("      {\"id\": \"item1\", \"value\": \"They arrived at the airport\", \"isCorrect\": true, \"positionId\": \"c3d4\"},\n")
                            .append("      {\"id\": \"item2\", \"value\": \"Sarah checked in online\", \"isCorrect\": true, \"positionId\": \"a1b2\"},\n")
                            .append("      {\"id\": \"item3\", \"value\": \"The flight was delayed\", \"isCorrect\": true, \"positionId\": \"e5f6\"},\n")
                            .append("      {\"id\": \"item4\", \"value\": \"They boarded at 3 PM\", \"isCorrect\": true, \"positionId\": \"g7h8\"}\n")
                            .append("    ]\n")
                            .append("  }\n")
                            .append("}\n\n");
                    prompt.append("EXAMPLE (Reading - Paragraphs):\n");
                    prompt.append("{\n")
                            .append("  \"questionText\": \"Arrange paragraphs: [[pos_a1]] [[pos_b2]] [[pos_c3]] [[pos_d4]]\",\n")
                            .append("  \"orderNumber\": 1,\n")
                            .append("  \"score\": 1.0,\n")
                            .append("  \"questionType\": \"REARRANGE\",\n")
                            .append("  \"content\": {\n")
                            .append("    \"instruction\": \"Put the paragraphs in logical order.\",\n")
                            .append("    \"data\": [\n")
                            .append("      {\"id\": \"item1\", \"value\": \"Finally, climate change is a major challenge.\", \"isCorrect\": true, \"positionId\": \"d4\"},\n")
                            .append("      {\"id\": \"item2\", \"value\": \"Environmental issues are increasingly important.\", \"isCorrect\": true, \"positionId\": \"a1\"},\n")
                            .append("      {\"id\": \"item3\", \"value\": \"In addition, deforestation destroys habitats.\", \"isCorrect\": true, \"positionId\": \"c3\"},\n")
                            .append("      {\"id\": \"item4\", \"value\": \"First, pollution affects air quality.\", \"isCorrect\": true, \"positionId\": \"b2\"}\n")
                            .append("    ]\n")
                            .append("  }\n")
                            .append("}\n\n");
                }
                break;

            case "DRAG_AND_DROP":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText contains [[pos_xxxxxx]] placeholders for drop zones\n");
                prompt.append("- Each placeholder needs exactly 1 correct answer with matching positionId\n");
                prompt.append("- Can include distractor answers (isCorrect=false, positionId=null) to increase difficulty\n");
                prompt.append("- Number of correct answers = number of placeholders\n");
                prompt.append("- Answer length rule: EVERY answer 'value' (correct + distractor) MUST be <= 50 characters\n");
                prompt.append("- If a value would exceed 50 characters, shorten it while keeping the meaning\n\n");
                prompt.append("EXAMPLE:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"Complete the sentence: [[pos_a1b2c3]] is the capital of [[pos_d4e5f6]], and [[pos_g7h8i9]] is spoken there.\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"DRAG_AND_DROP\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"ans1\", \"value\": \"Paris\", \"isCorrect\": true, \"positionId\": \"a1b2c3\"},\n")
                        .append("      {\"id\": \"ans2\", \"value\": \"France\", \"isCorrect\": true, \"positionId\": \"d4e5f6\"},\n")
                        .append("      {\"id\": \"ans3\", \"value\": \"French\", \"isCorrect\": true, \"positionId\": \"g7h8i9\"},\n")
                        .append("      {\"id\": \"dist1\", \"value\": \"Berlin\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"dist2\", \"value\": \"Spain\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"dist3\", \"value\": \"German\", \"isCorrect\": false, \"positionId\": null}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");
                break;

            case "MULTIPLE_SELECT":
                prompt.append("FORMAT: Similar to MULTIPLE_CHOICE but allows selecting multiple correct answers\n");
                prompt.append("REQUIREMENTS:\n");
                prompt.append("- 4–6 options total\n");
                prompt.append("- 2–3 options with isCorrect=true\n");
                prompt.append("- positionId=null for all\n\n");
                prompt.append("EXAMPLE:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"Which of the following are correct uses of the present perfect tense?\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"MULTIPLE_SELECT\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"opt1\", \"value\": \"I have lived here for 5 years.\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt2\", \"value\": \"She has just finished her homework.\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt3\", \"value\": \"They went to Paris last year.\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt4\", \"value\": \"We are studying now.\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt5\", \"value\": \"He has visited London twice.\", \"isCorrect\": true, \"positionId\": null}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");
                break;

            case "REWRITE":
                prompt.append("REQUIREMENTS:\n");
                prompt.append("- Can have MULTIPLE correct answers (no incorrect answers)\n");
                prompt.append("- All answers with isCorrect=true are acceptable\n");
                prompt.append("- positionId=null for all\n\n");
                prompt.append("EXAMPLE:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"Rewrite this sentence in the passive voice: 'The teacher explained the lesson.'\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"REWRITE\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"null\", \"value\": \"The lesson was explained by the teacher.\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"null\", \"value\": \"The lesson was explained by the teacher yesterday.\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"null\", \"value\": \"The lesson has been explained by the teacher.\", \"isCorrect\": true, \"positionId\": null}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");
                break;

            default:
                prompt.append("Follow standard question format with all required fields.\n");
        }

        prompt.append("\n✅ VALIDATION:\n");
        prompt.append("□ Valid JSON format\n");
        prompt.append("□ Use ONLY lowercase letters (a-z) and numbers (0-9) for position IDs\n");
        prompt.append("□ Each position ID is UNIQUE\n");
    }

    private String buildEnhancedContextInfo(String questionType) {
        switch (questionType) {
            case "MULTIPLE_CHOICE":
                return "Multiple Choice Questions";
            case "MULTIPLE_SELECT":
                return "Multiple Selection Questions";
            case "TRUE_OR_FALSE":
                return "True or False Questions";
            case "FILL_IN_THE_BLANK":
                return "Fill in the Blank";
            case "DROPDOWN":
                return "Dropdown Selection";
            case "DRAG_AND_DROP":
                return "Drag and Drop Matching";
            case "REARRANGE":
                return "Sentence Rearrangement";
            case "REWRITE":
                return "Sentence Rewriting";
            default:
                return questionType + " Exercise";
        }
    }

    @Override
    public String callOpenAI(String prompt) {
        log.info("OpenAI start response");

        String url = UriComponentsBuilder
                .fromHttpUrl(endpoint + "/openai/deployments/gpt-5-mini/chat/completions")
                .queryParam("api-version", API_VERSION)
                .toUriString();

        Map<String, Object> requestBody = Map.of(
                "messages", new Object[]{
                        Map.of("role", "system", "content", SYSTEM_ROLE_JSON_INSTRUCTION),
                        Map.of("role", "user", "content", prompt)
                },
                "max_completion_tokens", 16000
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                var choices = (List<Map>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map message = (Map) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    content = cleanJsonResponse(content);

                    log.debug("OpenAI response (cleaned, first 1000 chars): {}",
                            content.length() > 1000 ? content.substring(0, 1000) : content);

                    return content;
                }
            }
        } catch (Exception e) {
            log.error("Error calling OpenAI: {}", e.getMessage(), e);
            throw new ApiException("Failed to call OpenAI: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        throw new ApiException("No response from OpenAI", HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    public String cleanJsonResponse(String content) {
        if (content == null) return "";

        String s = content.trim();
        s = s.replaceAll("(?s)^```(?:json)?\\s*", "");
        s = s.replaceAll("(?s)\\s*```\\s*$", "");
        s = s.replaceFirst("(?i)^\\s*here('?s)?\\s*the\\s*json[:\\s]*", "");
        s = s.replaceFirst("(?i)^\\s*response[:\\s]*", "");
        s = s.replaceAll("(?m)//.*?$", "");
        s = s.replaceAll("(?s)/\\*.*?\\*/", "");
        s = s.replaceAll(",\\s*}", "}");
        s = s.replaceAll(",\\s*\\]", "]");

        if ((s.startsWith("\"{") && s.endsWith("}\"")) || (s.startsWith("'{") && s.endsWith("}'"))) {
            s = s.substring(1, s.length() - 1).replace("\\\"", "\"");
        }

        return s.trim();
    }

    private List<QuestionDto> parseQuestionsFromResponse(String jsonResponse) {
        try {
            log.debug("Parsing questions from response");
            String cleaned = cleanJsonResponse(jsonResponse);

            JsonNode rootNode;
            try {
                rootNode = objectMapper.readTree(cleaned);
            } catch (Exception parseEx) {
                log.error("Failed to parse JSON. Full response: {}", cleaned);
                throw new ApiException("Invalid JSON format from AI: " + parseEx.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value());
            }

            JsonNode questionsNode = rootNode.get("questions");
            if (questionsNode == null || !questionsNode.isArray()) {
                throw new ApiException("Invalid response: missing or invalid 'questions' array",
                        HttpStatus.INTERNAL_SERVER_ERROR.value());
            }

            List<QuestionDto> questions = new ArrayList<>();
            int questionIndex = 0;

            for (JsonNode questionNode : questionsNode) {
                questionIndex++;
                try {
                    QuestionDto question = parseQuestion(questionNode, questionIndex, null);
                    questions.add(question);
                } catch (Exception e) {
                    log.error("Error parsing question {}: {}", questionIndex, e.getMessage());
                }
            }

            if (questions.isEmpty()) {
                throw new ApiException("No questions were successfully parsed",
                        HttpStatus.INTERNAL_SERVER_ERROR.value());
            }

            ensureUniquePositionIds(questions);

            log.info("Successfully parsed {} questions", questions.size());
            return questions;

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error parsing questions response: {}", e.getMessage(), e);
            throw new ApiException("Failed to parse questions: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private QuestionDto parseQuestion(JsonNode questionNode, int index, String expectedType) {
        QuestionDto question = new QuestionDto();

        JsonNode textNode = questionNode.get("questionText");
        if (textNode == null || textNode.isNull()) {
            throw new ApiException("Missing questionText for question " + index,
                    HttpStatus.BAD_REQUEST.value());
        }
        question.setQuestionText(textNode.asText());

        JsonNode orderNode = questionNode.get("orderNumber");
        question.setOrderNumber(orderNode != null ? orderNode.asInt() : index);

        JsonNode scoreNode = questionNode.get("score");
        question.setWeight(scoreNode != null ? scoreNode.asDouble() : 1.0);

        JsonNode typeNode = questionNode.get("questionType");
        question.setQuestionType(typeNode != null ? typeNode.asText() : expectedType);

        JsonNode contentNode = questionNode.get("content");
        if (contentNode == null || contentNode.isNull()) {
            throw new ApiException("Missing content for question " + index,
                    HttpStatus.BAD_REQUEST.value());
        }

        JsonNode dataNode = contentNode.get("data");
        if (dataNode == null || !dataNode.isArray()) {
            throw new ApiException("Missing or invalid data array for question " + index,
                    HttpStatus.BAD_REQUEST.value());
        }

        List<DataItem> dataItems = new ArrayList<>();
        for (JsonNode itemNode : dataNode) {
            dataItems.add(parseDataItem(itemNode));
        }

        if (dataItems.isEmpty()) {
            throw new ApiException("No data items found for question " + index,
                    HttpStatus.BAD_REQUEST.value());
        }

        DataContent dataContent = new DataContent();
        dataContent.setData(dataItems);
        question.setContent(dataContent);

        return question;
    }

    private DataItem parseDataItem(JsonNode itemNode) {
        DataItem dataItem = new DataItem();

        JsonNode idNode = itemNode.get("id");
        if (idNode == null || idNode.isNull()) {
            throw new ApiException("Missing 'id' in data item", HttpStatus.BAD_REQUEST.value());
        }
        dataItem.setId(idNode.asText());

        JsonNode valueNode = itemNode.get("value");
        if (valueNode == null || valueNode.isNull()) {
            throw new ApiException("Missing 'value' in data item", HttpStatus.BAD_REQUEST.value());
        }
        dataItem.setValue(valueNode.asText());

        JsonNode correctNode = itemNode.get("isCorrect");
        if (correctNode == null || correctNode.isNull()) {
            throw new ApiException("Missing 'isCorrect' in data item", HttpStatus.BAD_REQUEST.value());
        }
        dataItem.setCorrect(correctNode.asBoolean());

        JsonNode posNode = itemNode.get("positionId");
        if (posNode != null && !posNode.isNull()) {
            dataItem.setPositionId(posNode.asText());
        }

        return dataItem;
    }

    private void ensureUniquePositionIds(List<QuestionDto> questions) {
        Set<String> usedPositionIds = new HashSet<>();

        for (QuestionDto question : questions) {
            String questionText = question.getQuestionText();
            Map<String, String> replacements = new HashMap<>();

            Matcher matcher = POSITION_PATTERN.matcher(questionText);
            while (matcher.find()) {
                String oldId = matcher.group(1);
                if (usedPositionIds.contains(oldId)) {
                    String newId = generateUniquePositionId(usedPositionIds);
                    replacements.put(oldId, newId);
                    usedPositionIds.add(newId);
                } else {
                    usedPositionIds.add(oldId);
                }
            }

            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                questionText = questionText.replace(
                        "[[pos_" + entry.getKey() + "]]",
                        "[[pos_" + entry.getValue() + "]]"
                );
            }

            question.setQuestionText(questionText);

            if (question.getContent() != null && question.getContent().getData() != null) {
                for (DataItem item : question.getContent().getData()) {
                    String posId = item.getPositionId();
                    if (posId != null && replacements.containsKey(posId)) {
                        item.setPositionId(replacements.get(posId));
                    }
                }
            }
        }
    }

    private String generateUniquePositionId(Set<String> usedIds) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        String newId;

        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            newId = sb.toString();
        } while (usedIds.contains(newId));

        return newId;
    }

    @Override
    @Transactional(readOnly = true)
    public GenerateQuestionsResponse parseQuestionsFromFile(
            MultipartFile file,
            String description) throws IOException {

        log.info("Starting to parse questions from file: {}", file.getOriginalFilename());

        FileContentExtractor.validateFileNotEmpty(file);
        FileContentExtractor.validateFileSize(file);

        String fileContent = FileContentExtractor.extractContent(file);
        if (fileContent.isEmpty()) {
            throw new ApiException("No content extracted from file", HttpStatus.BAD_REQUEST.value());
        }

        log.info("Extracted {} characters from file", fileContent.length());

        // ✅ Start validation async - không wait kết quả
        CompletableFuture<InputValidationResponse> validationFuture = CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return validateInputContent(
                                null,  // description
                                null,  // vocabularyList
                                null,  // lessonFocus
                                null,  // customLessonFocus
                                null,  // sectionContent
                                fileContent,  // fileContent
                                null,  // ChallengeContext
                                ValidationType.FILE,
                                null
                        );
                    } catch (Exception e) {
                        log.error("Validation failed: {}", e.getMessage(), e);
                        return new InputValidationResponse(null, null, null, null, null);
                    }
                },
                executorService
        );

        String prompt = buildParsingPrompt(fileContent, description);
        int maxRetries = batchRetryMaxAttempts;
        int attempt = 0;
        Exception lastException = null;

        List<SectionWithQuestionsDto> sections = null;

        while (attempt < maxRetries) {
            try {
                attempt++;
                log.info("Parsing file attempt {}/{}", attempt, maxRetries);

                String aiResponse = callOpenAI(prompt);
                sections = parseMultipleSectionsResponse(aiResponse);

                // ✅ Validate and clean sections
                for (SectionWithQuestionsDto section : sections) {
                    section.getSection().setId(null);

                    List<QuestionDto> validQuestions = new ArrayList<>();
                    for (QuestionDto question : section.getQuestions()) {
                        question.setId(null);

                        try {
                            // Validate each question
                            validateQuestion(question);
                            validQuestions.add(question);
                        } catch (Exception e) {
                            log.warn("Skipping invalid question from file: {}", e.getMessage());
                        }
                    }

                    if (!validQuestions.isEmpty()) {
                        validateOutputFormat(validQuestions);
                        section.setQuestions(validQuestions);
                    }
                }

                sections.removeIf(s -> s.getQuestions().isEmpty());

                if (sections.isEmpty()) {
                    throw new RuntimeException("No valid questions found in file");
                }

                log.info("Successfully parsed {} sections with {} total questions",
                        sections.size(),
                        sections.stream().mapToInt(s -> s.getQuestions().size()).sum());

                // ✅ Success - break retry loop
                break;

            } catch (Exception e) {
                lastException = e;
                log.error("Parse file attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    log.info("Retrying file parsing after {} ms...", batchRetryDelayMs);
                    try {
                        Thread.sleep(batchRetryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ApiException("File parsing interrupted: " + ie.getMessage(),
                                HttpStatus.INTERNAL_SERVER_ERROR.value());
                    }
                }
            }
        }

        // ✅ Check if parsing failed after all retries
        if (sections == null || sections.isEmpty()) {
            log.error("Failed to parse file after {} attempts. Last error: {}",
                    maxRetries, lastException != null ? lastException.getMessage() : "unknown");
            throw new ApiException(
                    "Failed to parse questions from file after " + maxRetries + " attempts. " +
                            "Please check file format and try again.",
                    HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }

        // ✅ Get validation result (không block vì đã chạy song song)
        InputValidationResponse validation;
        try {
            validation = validationFuture.get(5, TimeUnit.SECONDS); // timeout ngắn vì đã chạy song song
        } catch (Exception e) {
            log.error("Failed to get validation result: {}", e.getMessage());
            validation = new InputValidationResponse(null, null, null, null, null);
        }

        // ✅ Return với validation result
        return new GenerateQuestionsResponse(
                sections,
                validation.getError(),
                validation.getWarning()
        );
    }

        @Override
    @Transactional(readOnly = true)
    public GenerateReadingPassageResponse generateReadingPassage(GenerateReadingPassageRequest request) {
        return null;
    }

    private String buildParsingPrompt(String fileContent, String description) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert at parsing educational content into structured JSON format.\n\n");

        prompt.append("🎯 YOUR TASK:\n");
        prompt.append("Parse the provided file content and extract all questions into a structured JSON format.\n");
        prompt.append("You MUST identify the question type correctly and format each question according to its type.\n\n");

        prompt.append("⚠️ CRITICAL: PRESERVE ORIGINAL CONTENT\n");
        prompt.append("- Keep ALL original text EXACTLY as written in the file\n");
        prompt.append("- DO NOT translate or modify any content\n");
        prompt.append("- DO NOT correct grammar or spelling errors\n");
        prompt.append("- DO NOT filter or remove any questions\n");
        prompt.append("- Accept Vietnamese, English, or mixed language content\n");
        prompt.append("- Your ONLY job is to convert the content into correct JSON format\n");
        prompt.append("⚠️ IMPORTANT: ANSWER VALUE CLEANING (keep content only)\n");
        prompt.append("- For each answer option/value, keep ONLY the answer text content.\n");
        prompt.append("- REMOVE leading labels such as: \"A.\", \"B.\", \"C.\", \"D.\", \"A)\", \"B)\", \"1.\", \"2)\", \"(A)\", \"-\", \"•\".\n");
        prompt.append("- Example: \"A. Paris\" -> \"Paris\"; \"B) France\" -> \"France\".\n");
        prompt.append("- This applies to ALL question types with options/answers.\n\n");

        if (description != null && !description.isBlank()) {
            prompt.append("📝 ADDITIONAL PARSING INSTRUCTIONS:\n");
            prompt.append(description).append("\n\n");
        }

        prompt.append("📄 FILE CONTENT TO PARSE:\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        prompt.append(fileContent).append("\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        prompt.append("⚠️ CRITICAL: You MUST respond with ONLY valid JSON in this EXACT format:\n\n");
        prompt.append("{\n");
        prompt.append("  \"sections\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"section\": {\n");
        prompt.append("        \"sectionTitle\": \"string or null\"\n");
        prompt.append("      },\n");
        prompt.append("      \"questions\": [\n");
        prompt.append("        {\n");
        prompt.append("          \"questionText\": \"string (required, keep ORIGINAL text, DO NOT include 'Question 1/2' or score)\",\n");
        prompt.append("          \"orderNumber\": 1,\n");
        prompt.append("          \"score\": 1.0,\n");
        prompt.append("          \"questionType\": \"QUESTION_TYPE\",\n");
        prompt.append("          \"content\": {\n");
        prompt.append("            \"data\": [\n");
        prompt.append("              {\n");
        prompt.append("                \"id\": \"string (required)\",\n");
        prompt.append("                \"value\": \"string (required, keep ORIGINAL text BUT content-only)\",\n");
        prompt.append("                \"isCorrect\": boolean (required),\n");
        prompt.append("                \"positionId\": \"string or null\"\n");
        prompt.append("              }\n");
        prompt.append("            ]\n");
        prompt.append("          }\n");
        prompt.append("        }\n");
        prompt.append("      ]\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");

        prompt.append("🔍 QUESTION TYPE IDENTIFICATION:\n");
        prompt.append("Carefully analyze each question and identify its type from these options:\n");
        prompt.append("- MULTIPLE_CHOICE: Question with 4 options, only 1 correct\n");
        prompt.append("- TRUE_OR_FALSE: Question with True/False options\n");
        prompt.append("- FILL_IN_THE_BLANK: Question with blanks to fill in (ONLY 1 correct answer)\n");
        prompt.append("- DROPDOWN: Question with dropdown selections\n");
        prompt.append("- DRAG_AND_DROP: Matching or drag-and-drop questions\n");
        prompt.append("- REARRANGE: Sentence ordering questions\n");
        prompt.append("- MULTIPLE_SELECT: Question with multiple correct answers\n");
        prompt.append("- REWRITE: Sentence transformation questions\n\n");

        appendDetailedQuestionTypeRulesForParsing(prompt);

        prompt.append("\n✅ VALIDATION CHECKLIST:\n");
        prompt.append("□ Valid JSON format (no markdown, no comments)\n");
        prompt.append("□ All questions have correct questionType\n");
        prompt.append("□ All required fields present (questionText, orderNumber, score, questionType, content.data)\n");
        prompt.append("□ Each data item has: id, value, isCorrect, positionId\n");
        prompt.append("□ Position IDs use only lowercase letters (a-z) and numbers (0-9)\n");
        prompt.append("□ Multiple choice has exactly 4 options\n");
        prompt.append("□ True/False has exactly 2 options\n");
        prompt.append("□ Fill in the blank has EXACTLY 1 correct answer\n");
        prompt.append("□ Answer values contain ONLY content (NO leading labels like A./B)/1./(A)/-)\n");
        prompt.append("□ If original option had labels (A,B,C,D...), labels are NOT included in value\n");
        prompt.append("□ ALL original content preserved (no translation, no modification)\n\n");

        prompt.append("🚨 CRITICAL REMINDERS:\n");
        prompt.append("- DO NOT add extra text, explanations, or markdown\n");
        prompt.append("- DO NOT include trailing commas\n");
        prompt.append("- Return ONLY the JSON object\n");
        prompt.append("- DO NOT translate or modify any content from the file\n");
        prompt.append("- PRESERVE original text exactly as written\n");
        prompt.append("- Answer 'value' MUST NOT include option labels (A./B)/1./(A)/-). Keep content only.\n");
        prompt.append("- Fill in the blank: ONLY 1 correct answer allowed\n");
        prompt.append("- If a question type is unclear, use MULTIPLE_CHOICE as default\n\n");

        prompt.append("Parse the content now and return ONLY valid JSON:\n");

        return prompt.toString();
    }

    private void appendDetailedQuestionTypeRulesForParsing(StringBuilder prompt) {
        prompt.append("1️⃣ MULTIPLE_CHOICE\n");
        prompt.append("IDENTIFICATION: Question with 4 answer options (A, B, C, D)\n");
        prompt.append("FORMAT REQUIREMENTS:\n");
        prompt.append("- Exactly 4 options in data array\n");
        prompt.append("- Exactly 1 option with isCorrect=true\n");
        prompt.append("- All options have positionId=null\n\n");
        prompt.append("EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"What is the capital of France?\",\n");
        prompt.append("  \"orderNumber\": 1,\n");
        prompt.append("  \"score\": 1.0,\n");
        prompt.append("  \"questionType\": \"MULTIPLE_CHOICE\",\n");
        prompt.append("  \"content\": {\n");
        prompt.append("    \"data\": [\n");
        prompt.append("      {\"id\": \"opt1\", \"value\": \"London\", \"isCorrect\": false, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"opt2\", \"value\": \"Paris\", \"isCorrect\": true, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"opt3\", \"value\": \"Berlin\", \"isCorrect\": false, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"opt4\", \"value\": \"Madrid\", \"isCorrect\": false, \"positionId\": null}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");

        // TRUE_OR_FALSE
        prompt.append("2️⃣ TRUE_OR_FALSE\n");
        prompt.append("IDENTIFICATION: Statement that can be marked as True or False\n");
        prompt.append("FORMAT REQUIREMENTS:\n");
        prompt.append("- Exactly 2 options: \"True\" and \"False\"\n");
        prompt.append("- Exactly 1 option with isCorrect=true\n");
        prompt.append("- Both options have positionId=null\n\n");
        prompt.append("EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"The Earth is flat.\",\n");
        prompt.append("  \"orderNumber\": 1,\n");
        prompt.append("  \"score\": 1.0,\n");
        prompt.append("  \"questionType\": \"TRUE_OR_FALSE\",\n");
        prompt.append("  \"content\": {\n");
        prompt.append("    \"data\": [\n");
        prompt.append("      {\"id\": \"opt1\", \"value\": \"True\", \"isCorrect\": false, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"opt2\", \"value\": \"False\", \"isCorrect\": true, \"positionId\": null}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");

        // FILL_IN_THE_BLANK
        prompt.append("3️⃣ FILL_IN_THE_BLANK\n");
        prompt.append("IDENTIFICATION: Sentence with blank(s) to complete\n");
        prompt.append("FORMAT REQUIREMENTS:\n");
        prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholder(s)\n");
        prompt.append("- xxxxxx = random 6-character ID (lowercase a-z and 0-9 only)\n");
        prompt.append("- Each answer has matching positionId\n");
        prompt.append("- ⚠️ CRITICAL: EXACTLY 1 correct answer ONLY (NOT multiple)\n");
        prompt.append("- The single answer MUST have isCorrect=true\n");
        prompt.append("- Multiple blanks = multiple data items with different positionIds\n\n");
        prompt.append("EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"If I [[pos_a7k3m2]] her address, I would visit her.\",\n");
        prompt.append("  \"orderNumber\": 1,\n");
        prompt.append("  \"score\": 1.0,\n");
        prompt.append("  \"questionType\": \"FILL_IN_THE_BLANK\",\n");
        prompt.append("  \"content\": {\n");
        prompt.append("    \"data\": [\n");
        prompt.append("      {\"id\": \"ans1\", \"value\": \"knew\", \"isCorrect\": true, \"positionId\": \"a7k3m2\"}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");

        // DROPDOWN
        prompt.append("4️⃣ DROPDOWN\n");
        prompt.append("IDENTIFICATION: Sentence with dropdown selection(s)\n");
        prompt.append("FORMAT REQUIREMENTS:\n");
        prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholder(s)\n");
        prompt.append("- Each dropdown has exactly 4 options\n");
        prompt.append("- Each dropdown has exactly 1 option with isCorrect=true\n");
        prompt.append("- All options for same dropdown share the same positionId\n");
        prompt.append("- xxxxxx = random 6-character ID (lowercase a-z and 0-9 only)\n\n");
        prompt.append("- Option ID naming rule:\n");
        prompt.append("  + Correct option: optN\n");
        prompt.append("  + Other options: optN_1, optN_2, optN_3\n");
        prompt.append("  + N increases sequentially for each dropdown in a section (opt1, opt2, ...)\n\n");
        prompt.append("EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"The company [[pos_k5l6m7]] expand into Asian markets next year.\",\n");
        prompt.append("  \"orderNumber\": 1,\n");
        prompt.append("  \"score\": 1.0,\n");
        prompt.append("  \"questionType\": \"DROPDOWN\",\n");
        prompt.append("  \"content\": {\n");
        prompt.append("    \"data\": [\n");
        prompt.append("      {\"id\": \"opt1\", \"value\": \"plans to\", \"isCorrect\": true, \"positionId\": \"k5l6m7\"},\n");
        prompt.append("      {\"id\": \"opt1_1\", \"value\": \"is planning\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"},\n");
        prompt.append("      {\"id\": \"opt1_2\", \"value\": \"will plan\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"},\n");
        prompt.append("      {\"id\": \"opt1_3\", \"value\": \"planned\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");

        // REARRANGE
        prompt.append("5️⃣ REARRANGE\n");
        prompt.append("IDENTIFICATION: Words/phrases to arrange in correct order\n");
        prompt.append("FORMAT REQUIREMENTS:\n");
        prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders for EACH word/phrase\n");
        prompt.append("- Must be a COMPLETE sentence (5-8 words/phrases)\n");
        prompt.append("- ALL items have isCorrect=true\n");
        prompt.append("- Each item has unique positionId matching placeholder\n");
        prompt.append("- xxxxxx = random 6-character ID (lowercase a-z and 0-9 only)\n\n");
        prompt.append("EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"[[pos_a1b2c3]] [[pos_d4e5f6]] [[pos_g7h8i9]] [[pos_j1k2l3]] [[pos_m4n5o6]] [[pos_p7q8r9]]\",\n");
        prompt.append("  \"orderNumber\": 1,\n");
        prompt.append("  \"score\": 1.0,\n");
        prompt.append("  \"questionType\": \"REARRANGE\",\n");
        prompt.append("  \"content\": {\n");
        prompt.append("    \"data\": [\n");
        prompt.append("      {\"id\": \"item1\", \"value\": \"She\", \"isCorrect\": true, \"positionId\": \"a1b2c3\"},\n");
        prompt.append("      {\"id\": \"item2\", \"value\": \"has\", \"isCorrect\": true, \"positionId\": \"d4e5f6\"},\n");
        prompt.append("      {\"id\": \"item3\", \"value\": \"been\", \"isCorrect\": true, \"positionId\": \"g7h8i9\"},\n");
        prompt.append("      {\"id\": \"item4\", \"value\": \"studying\", \"isCorrect\": true, \"positionId\": \"j1k2l3\"},\n");
        prompt.append("      {\"id\": \"item5\", \"value\": \"English\", \"isCorrect\": true, \"positionId\": \"m4n5o6\"},\n");
        prompt.append("      {\"id\": \"item6\", \"value\": \"recently\", \"isCorrect\": true, \"positionId\": \"p7q8r9\"}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");

        // DRAG_AND_DROP
        prompt.append("6️⃣ DRAG_AND_DROP\n");
        prompt.append("IDENTIFICATION: Match items to correct positions\n");
        prompt.append("FORMAT REQUIREMENTS:\n");
        prompt.append("- questionText contains [[pos_xxxxxx]] placeholders for drop zones\n");
        prompt.append("- Each placeholder needs exactly 1 correct answer with matching positionId\n");
        prompt.append("- Correct answers have isCorrect=true and matching positionId\n");
        prompt.append("- Can include distractor answers (isCorrect=false, positionId=null)\n");
        prompt.append("- Number of correct answers = number of placeholders\n");
        prompt.append("- xxxxxx = random 6-character ID (lowercase a-z and 0-9 only)\n");
        prompt.append("- Answer length rule: EVERY answer 'value' (correct + distractor) MUST be <= 50 characters\n");
        prompt.append("- If a value would exceed 50 characters, shorten it while keeping the meaning\n\n");
        prompt.append("EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"Complete: [[pos_a1b2c3]] is the capital of [[pos_d4e5f6]], and [[pos_g7h8i9]] is spoken there.\",\n");
        prompt.append("  \"orderNumber\": 1,\n");
        prompt.append("  \"score\": 1.0,\n");
        prompt.append("  \"questionType\": \"DRAG_AND_DROP\",\n");
        prompt.append("  \"content\": {\n");
        prompt.append("    \"data\": [\n");
        prompt.append("      {\"id\": \"ans1\", \"value\": \"Paris\", \"isCorrect\": true, \"positionId\": \"a1b2c3\"},\n");
        prompt.append("      {\"id\": \"ans2\", \"value\": \"France\", \"isCorrect\": true, \"positionId\": \"d4e5f6\"},\n");
        prompt.append("      {\"id\": \"ans3\", \"value\": \"French\", \"isCorrect\": true, \"positionId\": \"g7h8i9\"},\n");
        prompt.append("      {\"id\": \"dist1\", \"value\": \"Berlin\", \"isCorrect\": false, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"dist2\", \"value\": \"Spain\", \"isCorrect\": false, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"dist3\", \"value\": \"German\", \"isCorrect\": false, \"positionId\": null}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");

        // MULTIPLE_SELECT
        prompt.append("7️⃣ MULTIPLE_SELECT\n");
        prompt.append("IDENTIFICATION: Question with multiple correct answers\n");
        prompt.append("FORMAT REQUIREMENTS:\n");
        prompt.append("- 4-6 options total\n");
        prompt.append("- 2-3 options with isCorrect=true\n");
        prompt.append("- All options have positionId=null\n\n");
        prompt.append("EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"Which of the following are correct uses of the present perfect tense?\",\n");
        prompt.append("  \"orderNumber\": 1,\n");
        prompt.append("  \"score\": 1.0,\n");
        prompt.append("  \"questionType\": \"MULTIPLE_SELECT\",\n");
        prompt.append("  \"content\": {\n");
        prompt.append("    \"data\": [\n");
        prompt.append("      {\"id\": \"opt1\", \"value\": \"I have lived here for 5 years.\", \"isCorrect\": true, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"opt2\", \"value\": \"She has just finished her homework.\", \"isCorrect\": true, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"opt3\", \"value\": \"They went to Paris last year.\", \"isCorrect\": false, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"opt4\", \"value\": \"We are studying now.\", \"isCorrect\": false, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"opt5\", \"value\": \"He has visited London twice.\", \"isCorrect\": true, \"positionId\": null}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");

        // REWRITE
        prompt.append("8️⃣ REWRITE\n");
        prompt.append("IDENTIFICATION: Sentence transformation or rewriting task\n");
        prompt.append("FORMAT REQUIREMENTS:\n");
        prompt.append("- Can have MULTIPLE correct answers (all with isCorrect=true)\n");
        prompt.append("- No incorrect answers (no isCorrect=false)\n");
        prompt.append("- All answers have positionId=null\n");
        prompt.append("- Each answer is an acceptable rewrite/transformation\n\n");
        prompt.append("EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"Rewrite this sentence in the passive voice: 'The teacher explained the lesson.'\",\n");
        prompt.append("  \"orderNumber\": 1,\n");
        prompt.append("  \"score\": 1.0,\n");
        prompt.append("  \"questionType\": \"REWRITE\",\n");
        prompt.append("  \"content\": {\n");
        prompt.append("    \"data\": [\n");
        prompt.append("      {\"id\": \"ans1\", \"value\": \"The lesson was explained by the teacher.\", \"isCorrect\": true, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"ans2\", \"value\": \"The lesson has been explained by the teacher.\", \"isCorrect\": true, \"positionId\": null}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");

        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        prompt.append("⚠️ IMPORTANT NOTES:\n");
        prompt.append("- Position IDs MUST use ONLY lowercase letters (a-z) and numbers (0-9)\n");
        prompt.append("- Each position ID must be exactly 6 characters\n");
        prompt.append("- Each position ID must be UNIQUE across all questions\n");
        prompt.append("- For FILL_IN_THE_BLANK: EXACTLY 1 correct answer (NOT multiple)\n");
        prompt.append("- For REWRITE: Can have multiple correct answers (all isCorrect=true)\n");
        prompt.append("- For MULTIPLE_SELECT: Must have 2-3 correct answers\n\n");
    }

    private List<SectionWithQuestionsDto> parseMultipleSectionsResponse(String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(cleanJsonResponse(jsonResponse));

            JsonNode sectionsNode = rootNode.get("sections");
            if (sectionsNode == null || !sectionsNode.isArray()) {
                throw new ApiException("Invalid response: missing sections array",
                        HttpStatus.INTERNAL_SERVER_ERROR.value());
            }

            List<SectionWithQuestionsDto> sections = new ArrayList<>();

            for (JsonNode sectionNode : sectionsNode) {
                SectionDto section = new SectionDto();

                JsonNode sectionInfoNode = sectionNode.get("section");
                if (sectionInfoNode != null) {
                    JsonNode titleNode = sectionInfoNode.get("sectionTitle");
                    section.setSectionTitle(titleNode != null ? titleNode.asText() : null);
                }
                section.setResourceType("NONE");

                JsonNode questionsNode = sectionNode.get("questions");
                if (questionsNode != null && questionsNode.isArray()) {
                    List<QuestionDto> questions = new ArrayList<>();
                    int idx = 0;
                    for (JsonNode qNode : questionsNode) {
                        try {
                            questions.add(parseQuestion(qNode, ++idx, null));
                        } catch (Exception e) {
                            log.warn("Skipping invalid question in section: {}", e.getMessage());
                            // Skip invalid questions
                        }
                    }

                    if (!questions.isEmpty()) {
                        ensureUniquePositionIds(questions);
                        sections.add(new SectionWithQuestionsDto(section, questions));
                    }
                }
            }

            return sections;

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error parsing sections: {}", e.getMessage(), e);
            throw new ApiException("Failed to parse sections: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    @Override
    public GenerateDistractorsResponse generateDistractors(GenerateDistractorsRequest request) {
        try {
            int existingCount = 1;
            if (request.getExistingDistractors() != null) {
                existingCount += request.getExistingDistractors().size();
            }

            int distractorsToGenerate = existingCount < 4 ? (4 - existingCount) : 1;

            String prompt = buildDistractorsPrompt(request, distractorsToGenerate);
            String aiResponse = callOpenAI(prompt);

            List<String> distractors = parseDistractorsResponse(aiResponse);

            return new GenerateDistractorsResponse(distractors);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate distractors: {}", e.getMessage(), e);
            throw new ApiException("Failed to generate distractors: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private String buildDistractorsPrompt(GenerateDistractorsRequest request, int numberOfDistractors) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate ").append(numberOfDistractors).append(" wrong answer(s).\n\n");

        prompt.append("Question: ").append(request.getQuestionText()).append("\n");
        prompt.append("Correct answer: ").append(request.getCorrectAnswer()).append("\n");

        if (request.getExistingDistractors() != null && !request.getExistingDistractors().isEmpty()) {
            prompt.append("Existing wrong answers: ");
            prompt.append(String.join(", ", request.getExistingDistractors())).append("\n");
        }

        prompt.append("\nReturn ONLY a JSON array: [\"answer1\", \"answer2\"]\n");

        return prompt.toString();
    }

    private List<String> parseDistractorsResponse(String jsonResponse) {
        try {
            String cleaned = cleanJsonResponse(jsonResponse);
            JsonNode rootNode = objectMapper.readTree(cleaned);

            List<String> distractors = new ArrayList<>();

            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    distractors.add(node.asText());
                }
            }

            return distractors;

        } catch (Exception e) {
            log.error("Error parsing distractors: {}", e.getMessage());
            throw new ApiException("Failed to parse distractors: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    @Override
    public List<SectionWithQuestionsDto> parseQuestionsFromText(String textContent, String description) {
//        if (textContent == null || textContent.trim().isEmpty()) {
//            throw new ApiException("Text content cannot be empty", HttpStatus.BAD_REQUEST.value());
//        }
//
//        String prompt = buildParsingPrompt(textContent, description);
//        String aiResponse = callOpenAI(prompt);
//
//        List<SectionWithQuestionsDto> sections = parseMultipleSectionsResponse(aiResponse);
//
//        for (SectionWithQuestionsDto section : sections) {
//            section.getSection().setId(null);
//
//            List<QuestionDto> validQuestions = new ArrayList<>();
//            for (QuestionDto question : section.getQuestions()) {
//                question.setId(null);
//
//                try {
//                    // Validate each question
//                    validateQuestion(question);
//                    validQuestions.add(question);
//                } catch (Exception e) {
//                    log.warn("Skipping invalid question from text: {}", e.getMessage());
//                }
//            }
//
//            if (!validQuestions.isEmpty()) {
//                validateOutputFormat(validQuestions);
//                section.setQuestions(validQuestions);
//            }
//        }
//
//        // Filter out sections with no valid questions
//        sections.removeIf(s -> s.getQuestions().isEmpty());
//
//        if (sections.isEmpty()) {
//            throw new ApiException("No valid questions found in text", HttpStatus.BAD_REQUEST.value());
//        }

        return null;
    }

    private LevelInfo parseLevelInfo(String level) {
        if (level == null || level.isBlank()) {
            throw new ApiException("Level is required", HttpStatus.BAD_REQUEST.value());
        }

        // Try parse as number first (database ID)
        try {
            Long levelId = Long.parseLong(level.trim());
            return getLevelInfoFromDatabase(levelId);
        } catch (NumberFormatException e) {
            // Not a number, try parse as enum
            try {
                DifficultyLevel difficulty = DifficultyLevel.valueOf(level.trim().toUpperCase());
                return getLevelInfoFromDifficulty(difficulty);
            } catch (IllegalArgumentException ex) {
                throw new ApiException("Invalid level: " + level +
                        ". Must be either a number (DB ID) or valid difficulty level (L1-L12, A1-C2, UNIVERSITY)",
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    /**
     * Standard grammar and formatting instructions for all AI-generated content
     */
    private String getGrammarAndFormattingInstructions() {
        StringBuilder instructions = new StringBuilder();
        instructions.append("✍️ GRAMMAR & FORMATTING RULES (MANDATORY FOR ALL CONTENT):\n");
        instructions.append("- ALWAYS capitalize the first letter of EVERY sentence\n");
        instructions.append("- ALWAYS capitalize the pronoun 'I' (NEVER write lowercase 'i')\n");
        instructions.append("- Capitalize proper nouns (names, places, etc.)\n");
        instructions.append("- Use proper punctuation (periods, commas, question marks, apostrophes)\n");
        instructions.append("- Write complete, grammatically correct sentences\n");
        instructions.append("- Follow standard English capitalization and punctuation rules\n\n");
        return instructions.toString();
    }

}