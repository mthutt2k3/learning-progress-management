package com.learning.progress.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import com.learning.progress.common.DifficultyLevel;
import com.learning.progress.common.LessonFocus;
import com.learning.progress.dto.ai.*;
import com.learning.progress.dto.challenge.section.*;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.DailyChallengeRepository;
import com.learning.progress.repository.LevelRepository;
import com.learning.progress.service.OpenAiService;
import com.learning.progress.util.FileContentExtractor;
import com.learning.progress.util.TraceUtil;
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

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
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

    @Value("${azure.translator.endpoint}")
    private String translatorEndpoint;

    @Value("${azure.translator.key}")
    private String translatorKey;

    @Value("${azure.translator.region}")
    private String translatorRegion;

    private RestTemplate restTemplate;

    private static final String API_VERSION = "2025-04-01-preview";
    private static final Pattern POSITION_PATTERN = Pattern.compile("\\[\\[pos_([a-z0-9]+)\\]\\]");

    private static final String SYSTEM_ROLE_JSON_INSTRUCTION =
            "You are an expert English teacher. Return ONLY valid JSON (no markdown, no comments, no extra text). " +
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
        int connectTimeoutMs = 5000;
        int readTimeoutMs = 300000;
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restTemplate = new RestTemplate(requestFactory);
        log.info("Initialized RestTemplate with connectTimeout={}ms readTimeout={}ms", connectTimeoutMs, readTimeoutMs);
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

    @Override
    @Transactional(readOnly = true)
    public List<SectionWithQuestionsDto> generateGVQuestions(GenerateGVQuestionsRequest request) {

        LevelInfo levelInfo = parseLevelInfo(request.getLevel());

        int totalQuestions = request.getQuestionTypeConfigs().stream()
                .mapToInt(GenerateGVQuestionsRequest.QuestionTypeConfig::getNumberOfQuestions)
                .sum();

        if (totalQuestions > maxQuestion) {
            log.error("Total questions exceeds limit: {} > {}", totalQuestions, maxQuestion);
            throw new ApiException("Total number of questions cannot exceed " + maxQuestion + ". Requested: " + totalQuestions,
                    HttpStatus.BAD_REQUEST.value());
        }

        log.info("Total questions to generate: {}", totalQuestions);
        log.info("Starting GV question generation for challengeId: {}", request.getChallengeId());

        DailyChallenge challenge = dailyChallengeRepository.findByIdWithFullHierarchy(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        ChallengeContext context = eagerLoadChallengeContext(challenge);
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
                        request.getDescription(),
                        contextInfo,
                        sectionOrder++,
                        levelInfo,
                        request.getLessonFocus(),
                        request.getCustomLessonFocus(),
                        request.getVocabularyList()
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
            allOf.get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Error waiting for parallel batch completion: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate questions in parallel: " + e.getMessage(), e);
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

        log.info("Successfully generated {} sections with {} total questions",
                results.size(), results.size());

        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SectionWithQuestionsDto> generateContentBasedQuestions(GenerateContentBasedQuestionsRequest request) {
        log.info("Starting content-based question generation for challengeId: {}", request.getChallengeId());

        LevelInfo levelInfo = parseLevelInfo(request.getLevel());

        int totalQuestions = request.getSections().stream()
                .flatMap(section -> section.getQuestionTypeConfigs().stream())
                .mapToInt(GenerateContentBasedQuestionsRequest.QuestionTypeConfig::getNumberOfQuestions)
                .sum();

        if (totalQuestions > maxQuestion) {
            log.error("Total questions across all sections exceeds limit: {}", totalQuestions);
            throw new ApiException("Total number of questions across all sections cannot exceed " + maxQuestion + ". Requested: " + totalQuestions,
                    HttpStatus.BAD_REQUEST.value());
        }

        log.info("Total questions to generate across all sections: {}", totalQuestions);

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        ChallengeContext context = eagerLoadChallengeContext(challenge);
        String dailyChallengeType = challenge.getChallengeType().toString();
        log.info("Daily Challenge Type: {}, Level: {}", dailyChallengeType, levelInfo.levelName);

        List<SectionWithQuestionsDto> results = new ArrayList<>();

        for (GenerateContentBasedQuestionsRequest.SectionWithConfig sectionConfig : request.getSections()) {
            SectionDto section = sectionConfig.getSection();

            log.info("Processing section: {} (ResourceType: {})",
                    section.getSectionTitle(), section.getResourceType());

            try {
                if (section.getSectionsContent() == null || section.getSectionsContent().isBlank()) {
                    throw new IllegalArgumentException("Section content is required");
                }

                // ✅ Enhanced content = gốc + OCR (nếu có ảnh)
                String enhancedContent = section.getSectionsContent();

                // ✅ Chỉ với READING + có sectionUrl + là ảnh → OCR
                if ("RE".equals(dailyChallengeType) &&
                        section.getSectionsUrl() != null &&
                        !section.getSectionsUrl().trim().isEmpty()) {

                    String sectionUrl = section.getSectionsUrl().trim();

                    if (isImageUrl(sectionUrl)) {
                        log.info("Detected image URL in reading section, performing OCR: {}", sectionUrl);

                        try {
                            String traceId = TraceUtil.getTraceId();
                            String ocrText = extractTextFromImageUrl(sectionUrl, traceId);

                            if (ocrText != null && !ocrText.trim().isEmpty()) {
                                // ✅ Ghép OCR text vào content gốc
                                enhancedContent = section.getSectionsContent() + "\n\n" + ocrText;
                                log.info("Successfully appended OCR text ({} chars) to section content",
                                        ocrText.length());
                            }
                        } catch (Exception e) {
                            log.error("Failed to extract text from section image: {}", e.getMessage());
                            // Continue với content gốc thay vì fail
                        }
                    }
                }

                // ✅ Tạo section với content đã enhance (để không modify request object)
                SectionDto enhancedSection = new SectionDto();
                enhancedSection.setId(section.getId());
                enhancedSection.setSectionTitle(section.getSectionTitle());
                enhancedSection.setSectionsContent(enhancedContent); // ✅ Dùng content đã ghép OCR
                enhancedSection.setResourceType(section.getResourceType());
                enhancedSection.setSectionsUrl(section.getSectionsUrl());
                enhancedSection.setOrderNumber(section.getOrderNumber());

                List<ContentBasedQuestionTask> sectionTasks = new ArrayList<>();
                int questionOrder = 1;

                for (GenerateContentBasedQuestionsRequest.QuestionTypeConfig config : sectionConfig.getQuestionTypeConfigs()) {
                    String questionType = config.getQuestionType();
                    int numberOfQuestions = config.getNumberOfQuestions();
                    String contextInfo = buildEnhancedContextInfo(questionType);

                    log.info("Preparing {} {} questions", numberOfQuestions, questionType);

                    for (int i = 0; i < numberOfQuestions; i++) {
                        sectionTasks.add(new ContentBasedQuestionTask(
                                context,
                                enhancedSection, // ✅ Dùng section với content đã có OCR text
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

                allOf.get(5, TimeUnit.MINUTES);

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

                // ✅ Return với section gốc (không có OCR text trong response)
                SectionWithQuestionsDto result = new SectionWithQuestionsDto(section, allQuestions);
                results.add(result);

                log.info("Generated {} questions for section", allQuestions.size());

            } catch (Exception e) {
                log.error("Failed to generate questions for section: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to generate questions: " + e.getMessage(), e);
            }
        }

        log.info("Successfully generated {} sections with {} total questions",
                results.size(), results.stream().mapToInt(s -> s.getQuestions().size()).sum());

        return results;
    }

    /**
     * Check if content is an image URL
     */
    private boolean isImageUrl(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        String lowerContent = content.toLowerCase().trim();

        if (!lowerContent.startsWith("http://") && !lowerContent.startsWith("https://")) {
            return false;
        }

        return lowerContent.contains(".jpg")
                || lowerContent.contains(".jpeg")
                || lowerContent.contains(".png")
                || lowerContent.contains(".gif")
                || lowerContent.contains(".webp")
                || lowerContent.contains(".bmp")
                || (lowerContent.contains("blob.core.windows.net") &&
                !lowerContent.contains(".webm") &&
                !lowerContent.contains(".mp3") &&
                !lowerContent.contains(".mp4"));
    }

    /**
     * Extract text from image URL using OCR
     */
    private String extractTextFromImageUrl(String imageUrl, String traceId) {
        File imageFile = null;

        try {
            log.info("[{}] Downloading image from URL...", traceId);
            imageFile = downloadImageFromUrl(imageUrl);

            log.info("[{}] Extracting text from image using OCR...", traceId);
            return extractTextFromImage(imageFile);

        } catch (Exception e) {
            log.error("[{}] Failed to extract text from image URL: {}", traceId, e.getMessage(), e);
            throw new ApiException("Failed to extract text from image: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        } finally {
            if (imageFile != null && imageFile.exists()) {
                try {
                    imageFile.delete();
                    log.debug("[{}] Cleaned up temp image file", traceId);
                } catch (Exception e) {
                    log.warn("[{}] Failed to delete temp image file: {}", traceId, e.getMessage());
                }
            }
        }
    }

    /**
     * Download image from URL to temp file
     */
    private File downloadImageFromUrl(String imageUrl) throws IOException {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");

            String extension = ".jpg";
            String lowerUrl = imageUrl.toLowerCase();
            if (lowerUrl.contains(".png")) extension = ".png";
            else if (lowerUrl.contains(".jpeg")) extension = ".jpeg";
            else if (lowerUrl.contains(".gif")) extension = ".gif";
            else if (lowerUrl.contains(".webp")) extension = ".webp";
            else if (lowerUrl.contains(".bmp")) extension = ".bmp";

            String filename = "reading_section_" + UUID.randomUUID() + extension;
            File tempFile = new File(tempDir, filename);

            URL url = new URL(imageUrl);
            try (InputStream in = url.openStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            log.debug("Downloaded image to: {}", tempFile.getAbsolutePath());
            return tempFile;

        } catch (Exception e) {
            log.error("Failed to download image from URL: {}", e.getMessage());
            throw new IOException("Failed to download image from URL", e);
        }
    }

    /**
     * Extract text from image using OpenAI Vision API
     */
    private String extractTextFromImage(File imageFile) {
        try {
            String base64Image = convertImageToBase64(imageFile);
            String prompt = buildOCRPrompt();

            String extractedText = null;
            int maxRetries = 3;
            Exception lastException = null;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    log.info("Calling OpenAI Vision for OCR (attempt {}/{})...", attempt, maxRetries);
                    extractedText = callOpenAIVisionForOCR(prompt, base64Image);
                    break;
                } catch (Exception e) {
                    lastException = e;
                    log.warn("OCR failed on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());

                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(1000L * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }

            if (extractedText == null) {
                throw new RuntimeException("Failed to extract text after " + maxRetries + " attempts: "
                        + (lastException != null ? lastException.getMessage() : "unknown error"));
            }

            return parseOCRResponse(extractedText.trim());

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to extract text from image: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract text from image", e);
        }
    }

    /**
     * Parse OCR JSON response
     */
    private String parseOCRResponse(String jsonResponse) {
        try {
            String cleaned = cleanJsonResponse(jsonResponse);
            JsonNode root = objectMapper.readTree(cleaned);

            String status = root.hasNonNull("status") ? root.get("status").asText() : "UNKNOWN";

            switch (status) {
                case "SUCCESS":
                    String extractedText = root.hasNonNull("text") ? root.get("text").asText() : "";
                    if (extractedText.isEmpty()) {
                        throw new ApiException("OCR returned empty text", HttpStatus.BAD_REQUEST.value());
                    }
                    return extractedText;

                case "ILLEGIBLE_HANDWRITING":
                    throw new ApiException("Chữ viết tay không rõ ràng, không thể đọc được", HttpStatus.BAD_REQUEST.value());

                case "NO_TEXT_FOUND":
                    throw new ApiException("Không tìm thấy văn bản trong ảnh", HttpStatus.BAD_REQUEST.value());

                case "BLANK_IMAGE":
                    throw new ApiException("Ảnh trống hoặc không hợp lệ", HttpStatus.BAD_REQUEST.value());

                default:
                    throw new ApiException("Lỗi OCR: Trạng thái không xác định - " + status,
                            HttpStatus.INTERNAL_SERVER_ERROR.value());
            }

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse OCR response: {}", e.getMessage(), e);
            throw new ApiException("Không thể xử lý kết quả OCR: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * Build OCR prompt
     */
    private String buildOCRPrompt() {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert OCR system specialized in reading English text from images.\n\n");

        prompt.append("CRITICAL: You MUST respond with ONLY valid JSON in this exact format:\n\n");
        prompt.append("{\n");
        prompt.append("  \"status\": \"SUCCESS\" | \"ILLEGIBLE_HANDWRITING\" | \"NO_TEXT_FOUND\" | \"BLANK_IMAGE\",\n");
        prompt.append("  \"text\": \"extracted text (only if status is SUCCESS)\"\n");
        prompt.append("}\n\n");

        prompt.append("TASK: Extract ALL text from the image.\n\n");

        prompt.append("RULES:\n");
        prompt.append("- Transcribe EXACTLY what is written\n");
        prompt.append("- DO NOT correct spelling or grammar\n");
        prompt.append("- Preserve line breaks and structure\n");
        prompt.append("- If unclear but readable → transcribe best interpretation\n");
        prompt.append("- If 70%+ unreadable → return ILLEGIBLE_HANDWRITING\n\n");

        prompt.append("Return ONLY the JSON object, no markdown, no extra text.\n");

        return prompt.toString();
    }

    /**
     * Convert image to base64
     */
    private String convertImageToBase64(File imageFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(imageFile)) {
            byte[] imageBytes = fis.readAllBytes();
            return Base64.getEncoder().encodeToString(imageBytes);
        }
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

    private void validateDifficultyOrLevel(DifficultyLevel difficulty, Long levelId) {
        if (difficulty != null && levelId != null) {
            throw new ApiException("Cannot specify both difficulty and levelId. Please choose only one.",
                    HttpStatus.BAD_REQUEST.value());
        }
        if (difficulty == null && levelId == null) {
            throw new ApiException("Must specify either difficulty or levelId.",
                    HttpStatus.BAD_REQUEST.value());
        }
    }

    private LevelInfo getLevelInfo(DifficultyLevel difficulty, Long levelId) {
        if (difficulty != null) {
            return getLevelInfoFromDifficulty(difficulty);
        } else {
            return getLevelInfoFromDatabase(levelId);
        }
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
        int maxRetries = 2;
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

        log.error("Failed to generate batch after {} attempts: {}", maxRetries,
                lastException != null ? lastException.getMessage() : "unknown error");
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

        log.error("Failed to generate batch after {} attempts: {}", maxRetries,
                lastException != null ? lastException.getMessage() : "unknown error");
        return Collections.emptyList();
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

        QuestionGenerationTask(ChallengeContext context, String questionType,
                               String userDescription, String contextInfo, int sectionOrder,
                               LevelInfo levelInfo, List<LessonFocus> lessonFocus,
                               String customLessonFocus, String vocabularyList) {
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
        List<LessonFocus> lessonFocus;
        String customLessonFocus;
        String vocabularyList;

        ContentBasedQuestionTask(ChallengeContext context, SectionDto section,
                                 String questionType, String userDescription,
                                 String contextInfo, String dailyChallengeType, int orderNumber,
                                 LevelInfo levelInfo) {
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

        prompt.append("Each question must:\n");
        prompt.append("- Match the student's level: ").append(levelInfo.levelName).append("\n");
        prompt.append("- Be written in natural, clear, age-appropriate English.\n");
        prompt.append(getGrammarAndFormattingInstructions());
        prompt.append("- Have plausible distractors and one clear correct answer.\n");

        if (userDescription != null && !userDescription.isBlank()) {
            prompt.append("💡 ADDITIONAL SUGGESTIONS (OPTIONAL - USE ONLY IF RELEVANT):\n");
            prompt.append(userDescription).append("\n\n");

            prompt.append("⚠️ IMPORTANT INSTRUCTION FOR USER SUGGESTIONS:\n");
            prompt.append("- These suggestions are SECONDARY and OPTIONAL\n");
            prompt.append("- ONLY apply suggestions that are relevant to the lesson content\n");
            prompt.append("- If suggestions contradict or are unrelated to the lesson → IGNORE them completely\n");
            prompt.append("- If suggestions don't make sense or violate common sense → IGNORE them\n");
            prompt.append("- NEVER create questions based solely on user suggestions if they don't fit the lesson\n");
            prompt.append("- Lesson content alignment is ALWAYS the top priority\n\n");
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
        prompt.append("□ Valid JSON format\n");
        prompt.append("□ Exactly ").append(numberOfQuestions).append(" questions\n\n");

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
            prompt.append("💡 ADDITIONAL SUGGESTIONS (OPTIONAL - USE ONLY IF RELEVANT):\n");
            prompt.append(userDescription).append("\n\n");

            prompt.append("⚠️ IMPORTANT INSTRUCTION FOR USER SUGGESTIONS:\n");
            prompt.append("- These suggestions are SECONDARY and OPTIONAL\n");
            prompt.append("- ONLY apply suggestions that are relevant to the lesson content\n");
            prompt.append("- If suggestions contradict or are unrelated to the lesson → IGNORE them completely\n");
            prompt.append("- If suggestions don't make sense or violate common sense → IGNORE them\n");
            prompt.append("- NEVER create questions based solely on user suggestions if they don't fit the lesson\n");
            prompt.append("- Lesson content alignment is ALWAYS the top priority\n\n");
        }

        prompt.append("TASK:\n");
        prompt.append("Generate EXACTLY ").append(numberOfQuestions).append(" ").append(questionType).append(" questions about the passage\n");
        prompt.append("Level: ").append(levelInfo.levelName).append("\n\n");

        appendJSONFormat(prompt, questionType);
        appendQuestionTypeRules(prompt, questionType, "BASED");

        prompt.append("\n✅ FINAL CHECKLIST:\n");
        prompt.append("□ Questions based on passage content\n");
        prompt.append("□ Appropriate for level: ").append(levelInfo.levelName).append("\n");
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
                        .append("      {\"id\": \"opt1\", \"value\": \"lived\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt2\", \"value\": \"was living\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt3\", \"value\": \"had lived\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt4\", \"value\": \"has lived\", \"isCorrect\": false, \"positionId\": null}\n")
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
                        .append("      {\"id\": \"opt1\", \"value\": \"True\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt2\", \"value\": \"False\", \"isCorrect\": false, \"positionId\": null}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");
                break;

            case "FILL_IN_THE_BLANK":
                if(dailyChallengeType != null && dailyChallengeType.equals("GV")) {
                    prompt.append("⚠️ CRITICAL FORMAT:\n");
                    prompt.append("FORMAT: \"Text [[pos_xxxxxx]](base word) more text.\"\n");
                    prompt.append("- xxxxxx is a random 6-character ID (lowercase a-z and 0-9)\n");
                    prompt.append("- The word inside parentheses ( ) is the ORIGINAL / BASE FORM of the missing word (used as a hint)\n");
                    prompt.append("- positionId in data MUST match the xxxxxx\n\n");


                    prompt.append("EXAMPLE:\n");
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
                    break;
                } else {
                    prompt.append("⚠️ CRITICAL FORMAT:\n");
                    prompt.append("FORMAT: \"Text [[pos_xxxxxx]] more text.\"\n");
                    prompt.append("- xxxxxx is a random 6-character ID (lowercase a-z and 0-9)\n");
                    prompt.append("- positionId in data MUST match the xxxxxx\n\n");


                    prompt.append("EXAMPLE:\n");
                    prompt.append("{\n")
                            .append("  \"questionText\": \"If I knew her [[pos_a7k3m2]], I would visit her tomorrow.\",\n")
                            .append("  \"orderNumber\": 1,\n")
                            .append("  \"score\": 1.0,\n")
                            .append("  \"questionType\": \"FILL_IN_THE_BLANK\",\n")
                            .append("  \"content\": {\n")
                            .append("    \"data\": [\n")
                            .append("      {\"id\": \"ans1\", \"value\": \"address\", \"isCorrect\": true, \"positionId\": \"a7k3m2\"}\n")
                            .append("    ]\n")
                            .append("  }\n")
                            .append("}\n\n");
                    break;
                }


            case "DROPDOWN":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders\n");
                prompt.append("- Each dropdown has exactly 4 options, exactly 1 with isCorrect=true\n");
                prompt.append("- All options for one dropdown share the same positionId\n\n");

                prompt.append("EXAMPLE:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"The company [[pos_k5l6m7]] expand into Asian markets next year.\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"DROPDOWN\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"opt1\", \"value\": \"plans to\", \"isCorrect\": true, \"positionId\": \"k5l6m7\"},\n")
                        .append("      {\"id\": \"opt2\", \"value\": \"is planning\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"},\n")
                        .append("      {\"id\": \"opt3\", \"value\": \"will plan\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");
                break;

            case "REARRANGE":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders for EACH word/phrase\n");
                prompt.append("- Must be a COMPLETE sentence (subject + verb + complete thought)\n");
                prompt.append("- Use 5-8 words/phrases\n\n");

                prompt.append("EXAMPLE:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"[[pos_a1b2c3]] [[pos_d4e5f6]] [[pos_g7h8i9]] [[pos_j1k2l3]] [[pos_m4n5o6]] [[pos_p7q8r9]]\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"REARRANGE\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"item1\", \"value\": \"She\", \"isCorrect\": true, \"positionId\": \"a1b2c3\"},\n")
                        .append("      {\"id\": \"item2\", \"value\": \"has\", \"isCorrect\": true, \"positionId\": \"d4e5f6\"},\n")
                        .append("      {\"id\": \"item3\", \"value\": \"been\", \"isCorrect\": true, \"positionId\": \"g7h8i9\"},\n")
                        .append("      {\"id\": \"item4\", \"value\": \"studying\", \"isCorrect\": true, \"positionId\": \"j1k2l3\"},\n")
                        .append("      {\"id\": \"item5\", \"value\": \"English\", \"isCorrect\": true, \"positionId\": \"m4n5o6\"},\n")
                        .append("      {\"id\": \"item6\", \"value\": \"recently\", \"isCorrect\": true, \"positionId\": \"p7q8r9\"}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");
                break;

            case "DRAG_AND_DROP":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText contains [[pos_xxxxxx]] placeholders for drop zones\n");
                prompt.append("- Each placeholder needs exactly 1 correct answer with matching positionId\n");
                prompt.append("- Can include distractor answers (isCorrect=false, positionId=null) to increase difficulty\n");
                prompt.append("- Number of correct answers = number of placeholders\n\n");

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
                        .append("      {\"id\": \"ans1\", \"value\": \"The lesson was explained by the teacher.\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"ans2\", \"value\": \"The lesson was explained by the teacher yesterday.\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"ans3\", \"value\": \"The lesson has been explained by the teacher.\", \"isCorrect\": true, \"positionId\": null}\n")
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
            case "MULTIPLE_CHOICE": return "Multiple Choice Questions";
            case "MULTIPLE_SELECT": return "Multiple Selection Questions";
            case "TRUE_OR_FALSE": return "True or False Questions";
            case "FILL_IN_THE_BLANK": return "Fill in the Blank";
            case "DROPDOWN": return "Dropdown Selection";
            case "DRAG_AND_DROP": return "Drag and Drop Matching";
            case "REARRANGE": return "Sentence Rearrangement";
            case "REWRITE": return "Sentence Rewriting";
            default: return questionType + " Exercise";
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
                var choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    content = cleanJsonResponse(content);
                    log.debug("OpenAI response (cleaned, first 1000 chars): {}",
                            content.length() > 1000 ? content.substring(0, 1000) : content);
                    return content;
                }
            }
        } catch (Exception e) {
            log.error("Error calling OpenAI: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call OpenAI: " + e.getMessage(), e);
        }

        throw new RuntimeException("No response from OpenAI");
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

        if ((s.startsWith("\"{") && s.endsWith("}\"")) || (s.startsWith("'{" ) && s.endsWith("}'"))) {
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
                throw new RuntimeException("Invalid JSON format from AI: " + parseEx.getMessage(), parseEx);
            }

            JsonNode questionsNode = rootNode.get("questions");

            if (questionsNode == null || !questionsNode.isArray()) {
                throw new RuntimeException("Invalid response: missing or invalid 'questions' array");
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
                throw new RuntimeException("No questions were successfully parsed");
            }

            ensureUniquePositionIds(questions);

            log.info("Successfully parsed {} questions", questions.size());

            return questions;

        } catch (Exception e) {
            log.error("Error parsing questions response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse questions: " + e.getMessage(), e);
        }
    }

    private QuestionDto parseQuestion(JsonNode questionNode, int index, String expectedType) {
        QuestionDto question = new QuestionDto();

        JsonNode textNode = questionNode.get("questionText");
        if (textNode == null || textNode.isNull()) {
            throw new RuntimeException("Missing questionText for question " + index);
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
            throw new RuntimeException("Missing content for question " + index);
        }

        JsonNode dataNode = contentNode.get("data");
        if (dataNode == null || !dataNode.isArray()) {
            throw new RuntimeException("Missing or invalid data array for question " + index);
        }

        List<DataItem> dataItems = new ArrayList<>();
        for (JsonNode itemNode : dataNode) {
            dataItems.add(parseDataItem(itemNode));
        }

        if (dataItems.isEmpty()) {
            throw new RuntimeException("No data items found for question " + index);
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
            throw new RuntimeException("Missing 'id' in data item");
        }
        dataItem.setId(idNode.asText());

        JsonNode valueNode = itemNode.get("value");
        if (valueNode == null || valueNode.isNull()) {
            throw new RuntimeException("Missing 'value' in data item");
        }
        dataItem.setValue(valueNode.asText());

        JsonNode correctNode = itemNode.get("isCorrect");
        if (correctNode == null || correctNode.isNull()) {
            throw new RuntimeException("Missing 'isCorrect' in data item");
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
    public List<SectionWithQuestionsDto> parseQuestionsFromFile(
            MultipartFile file,
            String description) throws IOException {

        log.info("Starting to parse questions from file: {}", file.getOriginalFilename());

        FileContentExtractor.validateFileNotEmpty(file);
        FileContentExtractor.validateFileSize(file);

        String fileContent = FileContentExtractor.extractContent(file);

        if (fileContent.isEmpty()) {
            throw new RuntimeException("No content extracted from file");
        }

        log.info("Extracted {} characters from file", fileContent.length());

        String prompt = buildParsingPrompt(fileContent, description);
        String aiResponse = callOpenAI(prompt);
        List<SectionWithQuestionsDto> sections = parseMultipleSectionsResponse(aiResponse);

        for (SectionWithQuestionsDto section : sections) {
            section.getSection().setId(null);
            for (QuestionDto question : section.getQuestions()) {
                question.setId(null);
            }
        }

        log.info("Successfully parsed {} sections", sections.size());

        return sections;
    }

    @Override
    @Transactional(readOnly = true)
    public GenerateReadingPassageResponse generateReadingPassage(GenerateReadingPassageRequest request) {
        log.info("Generating reading passage for challengeId: {}", request.getChallengeId());

        LevelInfo levelInfo = parseLevelInfo(request.getLevel());

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

//        ChallengeContext context = eagerLoadChallengeContext(challenge);

        String prompt = buildReadingPassagePrompt(
                request.getNumberOfParagraphs(),
                wordsPerParagraphDefault,
                request.getDescription(),
                levelInfo,
                request.getVocabularyList()
        );

        String aiResponse = callOpenAI(prompt);
        GenerateReadingPassageResponse response = parseReadingPassageResponse(aiResponse, levelInfo.levelName);

        log.info("Successfully generated passage: {} paragraphs", response.getNumberOfParagraphs());

        return response;
    }

    private String buildReadingPassagePrompt(
            int numberOfParagraphs,
            int wordsPerParagraph,
            String description,
            LevelInfo levelInfo,
            String vocabularyList) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert English teacher creating reading passages.\n\n");

        prompt.append(getDifficultyLevelInstructions(levelInfo));

        prompt.append(buildVocabularyPrompt(vocabularyList));

        prompt.append("⚠️ CRITICAL REQUIREMENTS:\n");
        prompt.append("- Passage MUST be appropriate for English language learners at level: ").append(levelInfo.levelName).append("\n");
        prompt.append("- Content must be educational, age-appropriate, and culturally sensitive\n");
        prompt.append("- Passage should have clear structure and coherent flow\n");
        prompt.append("- Language complexity must match the student level\n\n");

        prompt.append(getGrammarAndFormattingInstructions());

        if (description != null && !description.isBlank()) {
            prompt.append("💡 TOPIC/THEME SUGGESTIONS (OPTIONAL - USE ONLY IF APPROPRIATE):\n");
            prompt.append(description).append("\n\n");

            prompt.append("⚠️ IMPORTANT INSTRUCTION FOR TOPIC SUGGESTIONS:\n");
            prompt.append("- These suggestions are OPTIONAL and should guide the general theme/topic\n");
            prompt.append("- ONLY use suggestions that are:\n");
            prompt.append("  • Appropriate for language learners\n");
            prompt.append("  • Educational and meaningful\n");
            prompt.append("  • Suitable for the student level (").append(levelInfo.levelName).append(")\n");
            prompt.append("  • Culturally appropriate and not controversial\n");
            prompt.append("- If suggestions are inappropriate, irrelevant, or too complex → CREATE a suitable alternative topic\n");
            prompt.append("- If suggestions are too vague → Interpret them in an educational context\n");
            prompt.append("- NEVER create passages with inappropriate, offensive, or non-educational content\n");
            prompt.append("- Educational value and level appropriateness are ALWAYS the top priorities\n\n");
        } else {
            prompt.append("💡 TOPIC SELECTION:\n");
            prompt.append("Choose an engaging, educational topic appropriate for level ").append(levelInfo.levelName).append("\n");
            prompt.append("Examples: culture, science, technology, environment, daily life, history, etc.\n\n");
        }

        prompt.append("TASK:\n");
        prompt.append("Generate a reading passage with EXACTLY ").append(numberOfParagraphs).append(" paragraph(s)\n");
        prompt.append("Level: ").append(levelInfo.levelName).append("\n");
        prompt.append("Each paragraph: approximately ").append(wordsPerParagraph).append(" words\n\n");

        prompt.append("JSON FORMAT:\n");
        prompt.append("{\n");
        prompt.append("  \"passage\": \"Full HTML text with each paragraph wrapped in <p> tags\",\n");
        prompt.append("  \"numberOfParagraphs\": ").append(numberOfParagraphs).append(",\n");
        prompt.append("  \"totalWords\": <actual count>\n");
        prompt.append("}\n\n");

        prompt.append("⚠️ HTML FORMATTING REQUIREMENTS:\n");
        prompt.append("- Wrap EACH paragraph in <p></p> tags\n");
        prompt.append("- Format: <p>Paragraph 1 content...</p><p>Paragraph 2 content...</p>\n");
        prompt.append("- Do NOT use \\n\\n or line breaks, use HTML tags only\n");
        prompt.append("- Ensure proper HTML entity encoding if needed\n\n");

        prompt.append("Return ONLY valid JSON.\n");

        return prompt.toString();
    }

    private GenerateReadingPassageResponse parseReadingPassageResponse(String jsonResponse, String level) {
        try {
            String clean = cleanJsonResponse(jsonResponse);
            JsonNode root = objectMapper.readTree(clean);

            return new GenerateReadingPassageResponse(
                    root.get("passage").asText(),
                    root.get("numberOfParagraphs").asInt(),
                    root.get("totalWords").asInt(),
                    level
            );

        } catch (Exception e) {
            log.error("Failed to parse reading passage: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse reading passage: " + e.getMessage(), e);
        }
    }

    private String buildParsingPrompt(String fileContent, String description) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert at parsing educational content into structured JSON format.\n\n");

        prompt.append("🎯 YOUR TASK:\n");
        prompt.append("Parse the provided file content and extract all questions into a structured JSON format.\n");
        prompt.append("You MUST identify the question type correctly and format each question according to its type.\n\n");

        if (description != null && !description.isBlank()) {
            prompt.append("📝 ADDITIONAL PARSING INSTRUCTIONS:\n");
            prompt.append(description).append("\n\n");
        }

        prompt.append("📄 FILE CONTENT TO PARSE:\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        prompt.append(fileContent).append("\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        prompt.append(getGrammarAndFormattingInstructions());

        prompt.append("⚠️ CRITICAL: You MUST respond with ONLY valid JSON in this EXACT format:\n\n");
        prompt.append("{\n");
        prompt.append("  \"sections\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"section\": {\n");
        prompt.append("        \"sectionTitle\": \"string or null\"\n");
        prompt.append("      },\n");
        prompt.append("      \"questions\": [\n");
        prompt.append("        {\n");
        prompt.append("          \"questionText\": \"string (required, ONLY question content, DO NOT include 'Question 1/2' or score))\",\n");
        prompt.append("          \"orderNumber\": 1,\n");
        prompt.append("          \"score\": 1.0,\n");
        prompt.append("          \"questionType\": \"QUESTION_TYPE\",\n");
        prompt.append("          \"content\": {\n");
        prompt.append("            \"data\": [\n");
        prompt.append("              {\n");
        prompt.append("                \"id\": \"string (required)\",\n");
        prompt.append("                \"value\": \"string (required)\",\n");
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
        prompt.append("- FILL_IN_THE_BLANK: Question with blanks to fill in\n");
        prompt.append("- DROPDOWN: Question with dropdown selections\n");
        prompt.append("- DRAG_AND_DROP: Matching or drag-and-drop questions\n");
        prompt.append("- REARRANGE: Sentence ordering questions\n");
        prompt.append("- MULTIPLE_SELECT: Question with multiple correct answers\n");
        prompt.append("- REWRITE: Sentence transformation questions\n\n");

        // Append detailed rules for each question type
        appendDetailedQuestionTypeRulesForParsing(prompt);

        prompt.append("\n✅ VALIDATION CHECKLIST:\n");
        prompt.append("□ Valid JSON format (no markdown, no comments)\n");
        prompt.append("□ All questions have correct questionType\n");
        prompt.append("□ All required fields present (questionText, orderNumber, score, questionType, content.data)\n");
        prompt.append("□ Each data item has: id, value, isCorrect, positionId\n");
        prompt.append("□ Position IDs use only lowercase letters (a-z) and numbers (0-9)\n");
        prompt.append("□ Multiple choice has exactly 4 options\n");
        prompt.append("□ True/False has exactly 2 options\n");
        prompt.append("□ All questions follow grammar rules (capitalize first letter, capitalize 'I')\n\n");

        prompt.append("🚨 CRITICAL REMINDERS:\n");
        prompt.append("- DO NOT add extra text, explanations, or markdown\n");
        prompt.append("- DO NOT include trailing commas\n");
        prompt.append("- Return ONLY the JSON object\n");
        prompt.append("- Preserve original question content while ensuring proper formatting\n");
        prompt.append("- If a question type is unclear, use MULTIPLE_CHOICE as default\n\n");

        prompt.append("Parse the content now and return ONLY valid JSON:\n");

        return prompt.toString();
    }

    /**
     * Detailed question type rules specifically for parsing (not generation)
     */
    private void appendDetailedQuestionTypeRulesForParsing(StringBuilder prompt) {
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        prompt.append("📚 DETAILED SPECIFICATIONS FOR EACH QUESTION TYPE\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // MULTIPLE_CHOICE
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
        prompt.append("- Use [[pos_xxxxxx]](hint) format for GV questions (hint in parentheses)\n");
        prompt.append("- Use [[pos_xxxxxx]] format for content-based questions (no hint)\n");
        prompt.append("- xxxxxx = random 6-character ID (lowercase a-z and 0-9 only)\n");
        prompt.append("- Each answer has matching positionId\n");
        prompt.append("- Multiple blanks = multiple data items with different positionIds\n\n");
        prompt.append("EXAMPLE (with hint):\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"If I [[pos_a7k3m2]](know) her address, I would visit her.\",\n");
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
        prompt.append("IDENTIFICATION: Sentence with dropdown menu(s) for selection\n");
        prompt.append("FORMAT REQUIREMENTS:\n");
        prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholder(s)\n");
        prompt.append("- Each dropdown has exactly 4 options\n");
        prompt.append("- All options for one dropdown share the same positionId\n");
        prompt.append("- Exactly 1 option per dropdown with isCorrect=true\n\n");
        prompt.append("EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"The company [[pos_k5l6m7]] expand into Asian markets next year.\",\n");
        prompt.append("  \"orderNumber\": 1,\n");
        prompt.append("  \"score\": 1.0,\n");
        prompt.append("  \"questionType\": \"DROPDOWN\",\n");
        prompt.append("  \"content\": {\n");
        prompt.append("    \"data\": [\n");
        prompt.append("      {\"id\": \"opt1\", \"value\": \"plans to\", \"isCorrect\": true, \"positionId\": \"k5l6m7\"},\n");
        prompt.append("      {\"id\": \"opt2\", \"value\": \"is planning\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"},\n");
        prompt.append("      {\"id\": \"opt3\", \"value\": \"will plan\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"},\n");
        prompt.append("      {\"id\": \"opt4\", \"value\": \"planned\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");

        // REARRANGE
        prompt.append("5️⃣ REARRANGE\n");
        prompt.append("IDENTIFICATION: Jumbled words/phrases that need to be arranged into a sentence\n");
        prompt.append("FORMAT REQUIREMENTS:\n");
        prompt.append("- questionText has [[pos_xxxxxx]] for EACH word/phrase\n");
        prompt.append("- Use 5-8 items total\n");
        prompt.append("- All items have isCorrect=true\n");
        prompt.append("- Each item has unique positionId matching its placeholder\n");
        prompt.append("- Order in data array = correct order\n\n");
        prompt.append("EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"[[pos_a1b2c3]] [[pos_d4e5f6]] [[pos_g7h8i9]] [[pos_j1k2l3]] [[pos_m4n5o6]]\",\n");
        prompt.append("  \"orderNumber\": 1,\n");
        prompt.append("  \"score\": 1.0,\n");
        prompt.append("  \"questionType\": \"REARRANGE\",\n");
        prompt.append("  \"content\": {\n");
        prompt.append("    \"data\": [\n");
        prompt.append("      {\"id\": \"item1\", \"value\": \"She\", \"isCorrect\": true, \"positionId\": \"a1b2c3\"},\n");
        prompt.append("      {\"id\": \"item2\", \"value\": \"has been\", \"isCorrect\": true, \"positionId\": \"d4e5f6\"},\n");
        prompt.append("      {\"id\": \"item3\", \"value\": \"studying\", \"isCorrect\": true, \"positionId\": \"g7h8i9\"},\n");
        prompt.append("      {\"id\": \"item4\", \"value\": \"English\", \"isCorrect\": true, \"positionId\": \"j1k2l3\"},\n");
        prompt.append("      {\"id\": \"item5\", \"value\": \"recently\", \"isCorrect\": true, \"positionId\": \"m4n5o6\"}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");

        // DRAG_AND_DROP
        prompt.append("6️⃣ DRAG_AND_DROP\n");
        prompt.append("IDENTIFICATION: Matching items or filling multiple blanks by dragging items\n");
        prompt.append("FORMAT REQUIREMENTS:\n");
        prompt.append("- questionText contains [[pos_xxxxxx]] placeholders for drop zones\n");
        prompt.append("- Each correct answer has positionId matching its placeholder\n");
        prompt.append("- Can include distractors with isCorrect=false and positionId=null\n");
        prompt.append("- Number of correct answers = number of placeholders\n\n");
        prompt.append("EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"[[pos_a1b2c3]] is the capital of [[pos_d4e5f6]].\",\n");
        prompt.append("  \"orderNumber\": 1,\n");
        prompt.append("  \"score\": 1.0,\n");
        prompt.append("  \"questionType\": \"DRAG_AND_DROP\",\n");
        prompt.append("  \"content\": {\n");
        prompt.append("    \"data\": [\n");
        prompt.append("      {\"id\": \"ans1\", \"value\": \"Paris\", \"isCorrect\": true, \"positionId\": \"a1b2c3\"},\n");
        prompt.append("      {\"id\": \"ans2\", \"value\": \"France\", \"isCorrect\": true, \"positionId\": \"d4e5f6\"},\n");
        prompt.append("      {\"id\": \"dist1\", \"value\": \"Berlin\", \"isCorrect\": false, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"dist2\", \"value\": \"Spain\", \"isCorrect\": false, \"positionId\": null}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");

        // MULTIPLE_SELECT
        prompt.append("7️⃣ MULTIPLE_SELECT\n");
        prompt.append("IDENTIFICATION: Question with multiple correct answers to select\n");
        prompt.append("FORMAT REQUIREMENTS:\n");
        prompt.append("- 4-6 options total\n");
        prompt.append("- 2-3 options with isCorrect=true\n");
        prompt.append("- All options have positionId=null\n\n");
        prompt.append("EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"Which of the following are fruits?\",\n");
        prompt.append("  \"orderNumber\": 1,\n");
        prompt.append("  \"score\": 1.0,\n");
        prompt.append("  \"questionType\": \"MULTIPLE_SELECT\",\n");
        prompt.append("  \"content\": {\n");
        prompt.append("    \"data\": [\n");
        prompt.append("      {\"id\": \"opt1\", \"value\": \"Apple\", \"isCorrect\": true, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"opt2\", \"value\": \"Carrot\", \"isCorrect\": false, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"opt3\", \"value\": \"Banana\", \"isCorrect\": true, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"opt4\", \"value\": \"Potato\", \"isCorrect\": false, \"positionId\": null},\n");
        prompt.append("      {\"id\": \"opt5\", \"value\": \"Orange\", \"isCorrect\": true, \"positionId\": null}\n");
        prompt.append("    ]\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");

        // REWRITE
        prompt.append("8️⃣ REWRITE\n");
        prompt.append("IDENTIFICATION: Sentence transformation or rewriting task\n");
        prompt.append("FORMAT REQUIREMENTS:\n");
        prompt.append("- Can have multiple correct answers (all with isCorrect=true)\n");
        prompt.append("- All options have positionId=null\n");
        prompt.append("- No incorrect answers (no isCorrect=false)\n\n");
        prompt.append("EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"Rewrite in passive voice: 'The teacher explained the lesson.'\",\n");
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

        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
    }

    private List<SectionWithQuestionsDto> parseMultipleSectionsResponse(String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(cleanJsonResponse(jsonResponse));
            JsonNode sectionsNode = rootNode.get("sections");

            if (sectionsNode == null || !sectionsNode.isArray()) {
                throw new RuntimeException("Invalid response: missing sections array");
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
                        questions.add(parseQuestion(qNode, ++idx, null));
                    }
                    ensureUniquePositionIds(questions);
                    sections.add(new SectionWithQuestionsDto(section, questions));
                }
            }

            return sections;

        } catch (Exception e) {
            log.error("Error parsing sections: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse sections: " + e.getMessage(), e);
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

        } catch (Exception e) {
            log.error("Failed to generate distractors: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate distractors: " + e.getMessage(), e);
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
            throw new RuntimeException("Failed to parse distractors: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SectionWithQuestionsDto> parseQuestionsFromText(String textContent, String description) {
        if (textContent == null || textContent.trim().isEmpty()) {
            throw new RuntimeException("Text content cannot be empty");
        }

        String prompt = buildParsingPrompt(textContent, description);
        String aiResponse = callOpenAI(prompt);
        List<SectionWithQuestionsDto> sections = parseMultipleSectionsResponse(aiResponse);

        for (SectionWithQuestionsDto section : sections) {
            section.getSection().setId(null);
            for (QuestionDto question : section.getQuestions()) {
                question.setId(null);
            }
        }

        return sections;
    }

    @Override
    public TranslationResponse translate(String text) {
        String traceId = TraceUtil.getTraceId();

        if (text == null || text.trim().isEmpty()) {
            throw new ApiException(Const.VALIDATION.MISSING_FIELD, HttpStatus.BAD_REQUEST.value());
        }

        try {
            String url = String.format("%stranslate?api-version=3.0&from=en&to=vi", translatorEndpoint);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Ocp-Apim-Subscription-Key", translatorKey);
            headers.set("Ocp-Apim-Subscription-Region", translatorRegion);

            List<Map<String, String>> body = List.of(Map.of("text", text));
            HttpEntity<List<Map<String, String>>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.POST, entity, List.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new ApiException(Const.TRANSLATOR.API_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.value());
            }

            Map<String, Object> first = (Map<String, Object>) response.getBody().get(0);
            List<Map<String, Object>> translations = (List<Map<String, Object>>) first.get("translations");
            String translatedText = (String) translations.get(0).get("text");

            return TranslationResponse.builder()
                    .originalText(text)
                    .translatedText(translatedText)
                    .fromLanguage("en")
                    .toLanguage("vi")
                    .build();

        } catch (Exception e) {
            log.error("[{}] Translation failed: {}", traceId, e.getMessage(), e);
            throw new ApiException(Const.TRANSLATOR.TRANSLATION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    public String callOpenAIForFeedback(String prompt) {
        return callOpenAI(prompt);
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
                throw new ApiException("Invalid level: " + level + ". Must be either a number (DB ID) or valid difficulty level (L1-L12, A1-C2, UNIVERSITY)",
                        HttpStatus.BAD_REQUEST.value());
            }
        }
    }

    /**
     * Call Azure OpenAI Vision API for OCR (extract text from image)
     */
    public String callOpenAIVisionForOCR(String prompt, String base64Image) {
        log.info("Azure OpenAI Vision start OCR");

        // Build Azure OpenAI endpoint for vision model
        String url = UriComponentsBuilder
                .fromHttpUrl(endpoint + "/openai/deployments/gpt-5-mini/chat/completions")
                .queryParam("api-version", API_VERSION)
                .toUriString();

        // Build message content with text + image
        List<Map<String, Object>> contentList = new ArrayList<>();

        // Add text prompt
        contentList.add(Map.of("type", "text", "content", prompt));

        // Add image
        contentList.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", "data:image/jpeg;base64," + base64Image)
        ));

        // Build request body
        Map<String, Object> requestBody = Map.of(
                "messages", new Object[]{
                        // System message để bắt buộc JSON response
                        Map.of(
                                "role", "system",
                                "content",
                                "You are a JSON-only API. You MUST respond with ONLY valid JSON. " +
                                        "No markdown, no code blocks, no explanations. " +
                                        "Your entire response must be a single JSON object and nothing else.\n\n" +

                                        // 🔹 Thêm phần định dạng JSON mẫu
                                        "CRITICAL: You MUST respond with ONLY valid JSON in this exact format, no markdown, no extra text:\n\n" +
                                        "{\n" +
                                        "  \"status\": \"SUCCESS\" | \"ILLEGIBLE_HANDWRITING\" | \"NO_TEXT_FOUND\" | \"BLANK_IMAGE\",\n" +
                                        "  \"text\": \"the exact raw text as read from the image, with no corrections or modifications (only if status is SUCCESS)\",\n" +
                                        "}\n\n" +
                                        "IMPORTANT: You must NOT correct, interpret, or modify the text. The 'text' must match exactly what you read in the image."
                        ),
                        // User message with prompt và image
                        Map.of("role", "user", "content", contentList)
                },
                "max_completion_tokens", 16000, // Giảm xuống vì chỉ cần JSON ngắn
                "response_format", Map.of("type", "json_object") // ⭐ CRITICAL: Bắt buộc JSON mode
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                var choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");

                    log.debug("OCR extracted text (first 500 chars): {}",
                            content.length() > 500 ? content.substring(0, 500) : content);

                    return content.trim();
                }
            }
        } catch (Exception e) {
            log.error("Error calling Azure OpenAI Vision for OCR: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Azure OpenAI Vision: " + e.getMessage(), e);
        }

        throw new RuntimeException("No response from Azure OpenAI Vision");
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