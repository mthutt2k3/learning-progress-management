package com.learning.progress.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import com.learning.progress.dto.ai.*;
import com.learning.progress.dto.challenge.section.*;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.DailyChallengeRepository;
import com.learning.progress.service.OpenAiService;
import com.learning.progress.util.FileContentExtractor;
import com.learning.progress.util.TraceUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
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

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String API_VERSION = "2025-01-01-preview";
    private static final Pattern POSITION_PATTERN = Pattern.compile("\\[\\[pos_([a-z0-9]+)\\]\\]");

    public OpenAiServiceImpl(DailyChallengeRepository dailyChallengeRepository) {
        this.dailyChallengeRepository = dailyChallengeRepository;
    }

    @PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        log.info("Initialized thread pool with size: {}", threadPoolSize);
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
     * API 1: Generate GV questions - FIXED WITH EAGER LOADING
     */
    @Override
    @Transactional(readOnly = true)
    public List<SectionWithQuestionsDto> generateGVQuestions(GenerateGVQuestionsRequest request) {

        int totalQuestions = request.getQuestionTypeConfigs().stream()
                .mapToInt(GenerateGVQuestionsRequest.QuestionTypeConfig::getNumberOfQuestions)
                .sum();

        if (totalQuestions > maxQuestion) {
            log.error("Total questions exceeds limit: {} > 50", totalQuestions);
            throw new ApiException("Total number of questions cannot exceed " + maxQuestion + ". Requested: " + totalQuestions,
                    HttpStatus.BAD_REQUEST.value());
        }

        log.info("Total questions to generate: {}", totalQuestions);

        log.info("Starting OPTIMIZED GV question generation for challengeId: {}", request.getChallengeId());

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // ✅ FIX: Eager load all lazy relationships BEFORE async execution
        ChallengeContext context = eagerLoadChallengeContext(challenge);
        log.info("Eager loaded challenge context - Level: {}, Lesson: {}",
                context.studentLevel, context.classLessonContent != null ? "loaded" : "null");

        // Prepare all tasks
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
                        sectionOrder++
                ));
            }
        }

        // ✅ FIX: Group by question type FIRST, then batch
        Map<String, List<QuestionGenerationTask>> tasksByType = allTasks.stream()
                .collect(Collectors.groupingBy(task -> task.questionType));

        log.info("Grouped tasks into {} question types", tasksByType.size());

        // Generate questions for each type in parallel
        List<CompletableFuture<List<QuestionWithOrderDto>>> futures = new ArrayList<>();

        for (Map.Entry<String, List<QuestionGenerationTask>> entry : tasksByType.entrySet()) {
            String questionType = entry.getKey();
            List<QuestionGenerationTask> tasksForType = entry.getValue();

            log.info("Processing {} tasks for question type: {}", tasksForType.size(), questionType);

            // Split into batches within this question type
            List<List<QuestionGenerationTask>> batches = splitIntoBatches(tasksForType, batchSize);

            for (List<QuestionGenerationTask> batch : batches) {
                CompletableFuture<List<QuestionWithOrderDto>> future = CompletableFuture.supplyAsync(
                        () -> generateBatchOfGVQuestions(batch),
                        executorService
                );
                futures.add(future);
            }
        }

        // Wait for all
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allOf.get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Error waiting for parallel batch completion: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate questions in parallel: " + e.getMessage(), e);
        }

        // Collect all questions
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

        // Sort by original section order
        allGeneratedQuestions.sort(Comparator.comparingInt(q -> q.originalSectionOrder));

        // Create sections
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

        // Ensure unique position IDs
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
        log.info("Starting OPTIMIZED content-based question generation for challengeId: {}", request.getChallengeId());

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

        // ✅ FIX: Eager load all lazy relationships BEFORE async execution
        ChallengeContext context = eagerLoadChallengeContext(challenge);
        String dailyChallengeType = challenge.getChallengeType().toString();
        log.info("Daily Challenge Type: {}, Level: {}", dailyChallengeType, context.studentLevel);

        List<SectionWithQuestionsDto> results = new ArrayList<>();

        for (GenerateContentBasedQuestionsRequest.SectionWithConfig sectionConfig : request.getSections()) {
            SectionDto section = sectionConfig.getSection();

            log.info("Processing section: {} (ResourceType: {})",
                    section.getSectionTitle(), section.getResourceType());

            try {
                if (section.getSectionsContent() == null || section.getSectionsContent().isBlank()) {
                    throw new IllegalArgumentException("Section content is required");
                }

                // Prepare tasks
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
                                section,
                                questionType,
                                request.getDescription(),
                                contextInfo,
                                dailyChallengeType,
                                questionOrder++
                        ));
                    }
                }

                // ✅ FIX: Group by question type FIRST
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

                // Sort by original order
                allGeneratedQuestions.sort(Comparator.comparingInt(q -> q.originalSectionOrder));

                List<QuestionDto> allQuestions = new ArrayList<>();
                for (int i = 0; i < allGeneratedQuestions.size(); i++) {
                    QuestionDto question = allGeneratedQuestions.get(i).question;
                    question.setId(null);
                    question.setOrderNumber(i + 1);
                    allQuestions.add(question);
                }

                ensureUniquePositionIds(allQuestions);

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
     * ✅ NEW: Eager load all lazy relationships to prevent LazyInitializationException in async threads
     */
    private ChallengeContext eagerLoadChallengeContext(DailyChallenge challenge) {
        ChallengeContext context = new ChallengeContext();

        // Load lesson content
        if (challenge.getClassLesson() != null) {
            context.classLessonContent = challenge.getClassLesson().getClassLessonContent();

            // Navigate through the entity graph and force initialization
            if (challenge.getClassLesson().getClassChapter() != null) {
                Hibernate.initialize(challenge.getClassLesson().getClassChapter());

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

        // Set defaults if not found
        if (context.classLessonContent == null) {
            context.classLessonContent = "No lesson content available";
        }
        if (context.studentLevel == null) {
            context.studentLevel = "Intermediate";
        }

        log.debug("Loaded challenge context - Level: {}, ContentLength: {}",
                context.studentLevel, context.classLessonContent.length());

        return context;
    }

    /**
     * ✅ NEW: Immutable context object containing all needed data (no lazy proxies)
     */
    private static class ChallengeContext {
        String classLessonContent;
        String studentLevel;
    }

    /**
     * Generate batch of GV questions - ALL SAME TYPE
     */
    private List<QuestionWithOrderDto> generateBatchOfGVQuestions(List<QuestionGenerationTask> batch) {
        int maxRetries = 2;
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                attempt++;
                // All tasks in batch have SAME question type (because we grouped first)
                QuestionGenerationTask firstTask = batch.get(0);
                log.info("Generating batch of {} {} questions (attempt {}/{})",
                        batch.size(), firstTask.questionType, attempt, maxRetries);

                String prompt = buildBatchGVQuestionPrompt(
                        firstTask.context,
                        firstTask.questionType,
                        firstTask.userDescription,
                        firstTask.contextInfo,
                        batch.size()
                );

                String aiResponse = callOpenAI(prompt);
                List<QuestionDto> questions = parseQuestionsFromResponse(aiResponse);

                if (questions.size() > batch.size()) {
                    questions = questions.subList(0, batch.size());
                }

                // Wrap with original order
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
                        Thread.sleep(1000); // Wait 1 second before retry
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

    /**
     * Generate batch of content-based questions - ALL SAME TYPE
     */
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
                        firstTask.dailyChallengeType
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
                        Thread.sleep(1000); // Wait 1 second before retry
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

    // Helper classes - UPDATED to use ChallengeContext instead of DailyChallenge
    private static class QuestionGenerationTask {
        ChallengeContext context;
        String questionType;
        String userDescription;
        String contextInfo;
        int sectionOrder;

        QuestionGenerationTask(ChallengeContext context, String questionType,
                               String userDescription, String contextInfo, int sectionOrder) {
            this.context = context;
            this.questionType = questionType;
            this.userDescription = userDescription;
            this.contextInfo = contextInfo;
            this.sectionOrder = sectionOrder;
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

        ContentBasedQuestionTask(ChallengeContext context, SectionDto section,
                                 String questionType, String userDescription,
                                 String contextInfo, String dailyChallengeType, int orderNumber) {
            this.context = context;
            this.section = section;
            this.questionType = questionType;
            this.userDescription = userDescription;
            this.contextInfo = contextInfo;
            this.dailyChallengeType = dailyChallengeType;
            this.orderNumber = orderNumber;
        }
    }

    // Wrapper to maintain order
    private static class QuestionWithOrderDto {
        QuestionDto question;
        int originalSectionOrder;

        QuestionWithOrderDto(QuestionDto question, int originalSectionOrder) {
            this.question = question;
            this.originalSectionOrder = originalSectionOrder;
        }
    }

    /**
     * ✨ UPDATED: Determine student level from context and user description
     */
    private String determineStudentLevel(ChallengeContext context, String userDescription) {
        // Default level from context
        String systemLevel = context.studentLevel;

        // Check if user specified level in description
        if (userDescription != null && !userDescription.isBlank()) {
            String descLower = userDescription.toLowerCase();

            // Priority: User's explicit level specification
            if (descLower.contains("beginner") || descLower.contains("basic") || descLower.contains("elementary")) {
                return "Beginner";
            } else if (descLower.contains("pre-intermediate") || descLower.contains("pre intermediate")) {
                return "Pre-Intermediate";
            } else if (descLower.contains("intermediate") && !descLower.contains("pre") && !descLower.contains("upper")) {
                return "Intermediate";
            } else if (descLower.contains("upper-intermediate") || descLower.contains("upper intermediate")) {
                return "Upper-Intermediate";
            } else if (descLower.contains("advanced") || descLower.contains("proficient")) {
                return "Advanced";
            }
        }

        // Use system level if no user override
        return systemLevel;
    }

    /**
     * ✨ NEW: Get level-specific instructions for AI
     */
    private String getLevelInstructions(String level) {
        switch (level.toLowerCase()) {
            case "beginner":
            case "elementary":
            case "basic":
                return """
                📊 STUDENT LEVEL: BEGINNER (A1-A2)
                - Use simple, common vocabulary (500-1000 most frequent words)
                - Use present simple, present continuous, simple past tenses primarily
                - Short sentences (8-12 words average)
                - Clear, straightforward grammar structures
                - Avoid idioms, phrasal verbs, or complex expressions
                - Focus on everyday topics: family, food, daily routines, hobbies
                """;

            case "pre-intermediate":
                return """
                📊 STUDENT LEVEL: PRE-INTERMEDIATE (A2-B1)
                - Expand vocabulary to 1500-2000 words
                - Introduce past continuous, present perfect, future forms
                - Medium-length sentences (10-15 words average)
                - Basic conjunctions and linking words (because, although, when)
                - Simple phrasal verbs and common expressions
                - Topics: travel, shopping, health, work, education
                """;

            case "intermediate":
                return """
                📊 STUDENT LEVEL: INTERMEDIATE (B1-B2)
                - Vocabulary range of 2500-3500 words
                - All major tenses including conditionals, passive voice
                - Varied sentence structures (12-18 words average)
                - Common idioms and phrasal verbs
                - More abstract topics: environment, technology, culture, opinions
                - Require inference and deeper comprehension
                """;

            case "upper-intermediate":
            case "upper intermediate":
                return """
                📊 STUDENT LEVEL: UPPER-INTERMEDIATE (B2-C1)
                - Rich vocabulary (4000-5000 words) including less common terms
                - All advanced grammar: mixed conditionals, subjunctive, reported speech
                - Complex sentence structures with multiple clauses
                - Idiomatic expressions, colloquialisms, nuanced meanings
                - Abstract and specialized topics: philosophy, science, business, social issues
                - Require critical thinking and analysis
                """;

            case "advanced":
            case "proficient":
                return """
                📊 STUDENT LEVEL: ADVANCED (C1-C2)
                - Extensive vocabulary (6000+ words) including specialized terminology
                - Sophisticated grammar with subtle distinctions
                - Complex, varied sentence structures
                - Advanced idioms, metaphors, literary devices
                - Challenging topics requiring deep analysis and evaluation
                - Native-like comprehension and expression expected
                """;

            default:
                return """
                📊 STUDENT LEVEL: INTERMEDIATE (B1-B2) - DEFAULT
                - Balanced vocabulary and grammar complexity
                - Clear but not overly simplified language
                - Topics suitable for general English learners
                """;
        }
    }

    private String buildBatchGVQuestionPrompt(
            ChallengeContext context,
            String questionType,
            String userDescription,
            String contextInfo,
            int numberOfQuestions) {

        StringBuilder prompt = new StringBuilder();

        String studentLevel = determineStudentLevel(context, userDescription);
        String levelInstructions = getLevelInstructions(studentLevel);

        prompt.append("You are an expert English test creator for Vietnamese National High School Examination (THPT Quốc Gia).\n");
        prompt.append("Create PROFESSIONAL, ACADEMIC-STANDARD questions that test real English proficiency.\n\n");

        prompt.append("🎓 EXAM STANDARDS - VIETNAMESE NATIONAL HIGH SCHOOL EXAM FORMAT:\n");
        prompt.append("- Questions MUST be clear, unambiguous, and professionally written\n");
        prompt.append("- Test REAL language skills, not trick questions or rote memorization\n");
        prompt.append("- Use NATURAL, AUTHENTIC English that native speakers would use\n");
        prompt.append("- Distractors must be PLAUSIBLE but clearly distinguishable by competent students\n");
        prompt.append("- Each question should have a clear linguistic focus (grammar point, vocabulary, collocation)\n");
        prompt.append("- Progressive difficulty: start easier, gradually increase complexity\n");
        prompt.append("- Contextual questions preferred over isolated grammar drills\n\n");

        // ✨ Level instructions
        prompt.append(levelInstructions).append("\n");

        // 📚 Lesson content
        prompt.append("📚 LESSON CONTENT (Extract key teaching points from this):\n");
        prompt.append(context.classLessonContent).append("\n\n");

        prompt.append("⚠️ LESSON ALIGNMENT RULES:\n");
        prompt.append("- Extract the MAIN grammar/vocabulary points being taught in the lesson\n");
        prompt.append("- Create questions that TEST UNDERSTANDING of these points\n");
        prompt.append("- Use vocabulary and topics from the lesson, but in NEW contexts/situations\n");
        prompt.append("- Questions should feel like authentic use of the language, not just lesson repetition\n");
        prompt.append("- Example: If lesson teaches 'present perfect', create questions that require students to\n");
        prompt.append("  distinguish between present perfect and other tenses in realistic contexts\n\n");

        // 🔥 User requirements
        if (userDescription != null && !userDescription.isBlank()) {
            prompt.append("🔥 ADDITIONAL REQUIREMENTS:\n");
            prompt.append(userDescription).append("\n");
            prompt.append("Apply these requirements while maintaining exam-standard quality.\n\n");
        }

        prompt.append("📝 TASK:\n");
        prompt.append("Generate EXACTLY ").append(numberOfQuestions).append(" HIGH-QUALITY ").append(questionType).append(" questions\n");
        prompt.append("Level: ").append(studentLevel).append("\n\n");

        // Special instructions based on question type
        if ("MULTIPLE_CHOICE".equals(questionType)) {
            prompt.append("📋 MULTIPLE CHOICE - THPT QG STANDARD:\n");
            prompt.append("Structure: [Situation/Context (optional)] + Question stem + 4 options (A, B, C, D)\n\n");
            prompt.append("Example Format:\n");
            prompt.append("Maria has been learning English _____ she was ten years old.\n");
            prompt.append("A. since    B. for    C. when    D. while\n\n");
            prompt.append("Or with context:\n");
            prompt.append("Sarah is planning her summer vacation. She _____ to Japan next month.\n");
            prompt.append("A. goes    B. is going    C. went    D. has gone\n\n");
            prompt.append("DISTRACTOR RULES:\n");
            prompt.append("- Each distractor must be a POSSIBLE answer in SOME context\n");
            prompt.append("- Avoid obviously wrong answers (different word class, nonsense)\n");
            prompt.append("- Test genuine understanding, not memorization\n");
            prompt.append("- For grammar: distractors should be other forms/tenses that students might confuse\n");
            prompt.append("- For vocabulary: distractors should be semantically related or collocational alternatives\n\n");
        } else if ("FILL_IN_THE_BLANK".equals(questionType)) {
            prompt.append("📋 FILL IN THE BLANK - THPT QG STANDARD:\n");
            prompt.append("Use natural English sentences with blanks testing specific points.\n");
            prompt.append("Format: \"She [[pos_a1b2c3]](go) to school every day.\"\n\n");
            prompt.append("Example:\n");
            prompt.append("If I [[pos_x1y2z3]](know) her phone number, I would call her right now.\n");
            prompt.append("Answer: knew (testing second conditional)\n\n");
            prompt.append("QUALITY STANDARDS:\n");
            prompt.append("- Context should make the correct answer clear to competent students\n");
            prompt.append("- Test one clear grammar/vocabulary point per blank\n");
            prompt.append("- Avoid ambiguous sentences with multiple possible answers\n");
            prompt.append("- Hint in parentheses should guide but not give away the answer\n\n");
        } else if ("REARRANGE".equals(questionType)) {
            prompt.append("📋 REARRANGE - THPT QG STANDARD:\n");
            prompt.append("⚠️ CRITICAL: Create COMPLETE, MEANINGFUL sentences\n\n");
            prompt.append("REQUIREMENTS:\n");
            prompt.append("✓ Must be a FULL sentence with complete meaning\n");
            prompt.append("✓ Include subject + verb + complete thought\n");
            prompt.append("✓ Use 5-8 words/phrases for optimal challenge\n");
            prompt.append("✓ When arranged correctly = grammatically perfect sentence\n\n");
            prompt.append("EXAMPLES OF COMPLETE SENTENCES:\n");
            prompt.append("✅ \"She has been studying English recently\" (6 words)\n");
            prompt.append("✅ \"My brother plays football every weekend\" (5 words)\n");
            prompt.append("✅ \"The teacher explained the lesson very clearly\" (6 words)\n");
            prompt.append("✅ \"If I had known, I would have helped\" (7 words)\n\n");
            prompt.append("AVOID INCOMPLETE SENTENCES:\n");
            prompt.append("❌ \"The teacher the explains\" (missing object)\n");
            prompt.append("❌ \"Students are learning new\" (incomplete thought)\n");
            prompt.append("❌ \"Because he was late\" (fragment, not complete)\n");
            prompt.append("❌ \"She is\" (too short, incomplete)\n\n");
            prompt.append("Before finalizing: Ask yourself \"Is this a complete sentence I could say in conversation?\"\n");
            prompt.append("If NO, add missing words to make it complete!\n\n");
        }

        prompt.append("🎯 QUALITY CHECKLIST (VERIFY EACH QUESTION):\n");
        prompt.append("✓ Clear and unambiguous question stem\n");
        prompt.append("✓ Tests a specific language point from the lesson\n");
        prompt.append("✓ Uses natural, authentic English\n");
        prompt.append("✓ One clearly correct answer\n");
        prompt.append("✓ Plausible distractors (for multiple choice)\n");
        prompt.append("✓ Appropriate difficulty for level: ").append(studentLevel).append("\n");
        prompt.append("✓ No cultural bias or obscure references\n");
        prompt.append("✓ Professional formatting and language\n\n");

        appendJSONFormat(prompt, questionType);
        appendQuestionTypeRules(prompt, questionType);

        prompt.append("\n🚫 AVOID THESE COMMON MISTAKES:\n");
        prompt.append("✗ Questions with multiple correct answers\n");
        prompt.append("✗ Obviously wrong distractors (e.g., wrong word class)\n");
        prompt.append("✗ Unnatural or awkward English\n");
        prompt.append("✗ Questions testing obscure vocabulary not in lesson\n");
        prompt.append("✗ Trick questions designed to confuse rather than test understanding\n");
        prompt.append("✗ Copying exact sentences from lesson without adaptation\n");
        prompt.append("✗ Grammar exercises without context\n\n");

        prompt.append("🔥 CRITICAL REQUIREMENTS:\n");
        prompt.append("1. Return ONLY valid JSON - no markdown, no comments, no explanations\n");
        prompt.append("2. Generate EXACTLY ").append(numberOfQuestions).append(" questions\n");
        prompt.append("3. Each question MUST meet THPT QG professional standards\n");
        prompt.append("4. Questions test UNDERSTANDING, not just memorization\n");
        prompt.append("5. All distractors must be plausible and well-crafted\n");
        prompt.append("6. Use natural, contextual English\n");
        prompt.append("7. Maintain academic tone and clarity\n");
        prompt.append("8. Each question must be UNIQUE and test different aspects\n");
        prompt.append("9. For FILL_IN_THE_BLANK: Use format \"text [[pos_xxxxx]](hint) text\"\n");
        prompt.append("10. Double-check: one correct answer, three good distractors\n");
        if ("REARRANGE".equals(questionType)) {
            prompt.append("11. ⚠️ FOR REARRANGE: VERIFY SENTENCE IS COMPLETE!\n");
            prompt.append("    - Count words: minimum 5, optimal 5-8\n");
            prompt.append("    - Check: Has subject? Has verb? Complete thought?\n");
            prompt.append("    - Read aloud: Does it make complete sense?\n");
            prompt.append("    - Example COMPLETE: \"She has been studying English recently\" ✅\n");
            prompt.append("    - Example INCOMPLETE: \"The teacher the explains\" ❌\n");
        }
        prompt.append("\n\n");

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
            String dailyChallengeType) {

        StringBuilder prompt = new StringBuilder();

        String studentLevel = determineStudentLevel(context, userDescription);
        String levelInstructions = getLevelInstructions(studentLevel);

        prompt.append("You are an expert English test creator for Vietnamese National High School Examination (THPT Quốc Gia).\n");
        prompt.append("Create PROFESSIONAL reading comprehension questions that test genuine understanding.\n\n");

        prompt.append("🎓 THPT QG READING COMPREHENSION STANDARDS:\n");
        prompt.append("- Questions test DIFFERENT comprehension skills (main idea, detail, inference, vocabulary)\n");
        prompt.append("- Each question has ONE clearly correct answer based on the passage\n");
        prompt.append("- Distractors are plausible but definitively wrong\n");
        prompt.append("- Questions require CAREFUL reading, not just keyword matching\n");
        prompt.append("- Test understanding at various levels: literal, inferential, evaluative\n");
        prompt.append("- Progressive difficulty from easier to more challenging\n");
        prompt.append("- Professional, academic language\n\n");

        prompt.append("📚 CHALLENGE TYPE: ").append(dailyChallengeType).append("\n");
        appendDCTypeInstructions(prompt, dailyChallengeType);

        // ✨ Level instructions
        prompt.append("\n").append(levelInstructions).append("\n");

        prompt.append("📖 PASSAGE TO CREATE QUESTIONS FROM:\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        prompt.append(section.getSectionsContent()).append("\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // User requirements
        if (userDescription != null && !userDescription.isBlank()) {
            prompt.append("🔥 ADDITIONAL REQUIREMENTS:\n");
            prompt.append(userDescription).append("\n");
            prompt.append("Apply while maintaining THPT QG standards.\n\n");
        }

        prompt.append("📝 TASK:\n");
        prompt.append("Generate EXACTLY ").append(numberOfQuestions).append(" ").append(questionType).append(" questions about the passage\n");
        prompt.append("Level: ").append(studentLevel).append("\n\n");

        prompt.append("🎯 QUESTION TYPE DISTRIBUTION (Follow this breakdown):\n");
        if (numberOfQuestions >= 5) {
            prompt.append("- 1-2 questions: Main idea / Purpose (What is the passage mainly about?)\n");
            prompt.append("- 2-3 questions: Specific details (According to the passage... / The author mentions...)\n");
            prompt.append("- 1-2 questions: Inference (It can be inferred that... / The passage suggests...)\n");
            prompt.append("- 1 question: Vocabulary in context (The word \"X\" in paragraph Y is closest in meaning to...)\n");
        } else {
            prompt.append("- Mix of: main idea, details, and inference questions\n");
        }
        prompt.append("\n");

        if ("MULTIPLE_CHOICE".equals(questionType)) {
            prompt.append("📋 READING COMPREHENSION MC - THPT QG FORMAT:\n\n");
            prompt.append("Example 1 (Main Idea):\n");
            prompt.append("What is the main idea of the passage?\n");
            prompt.append("A. [Too specific - only one detail]\n");
            prompt.append("B. [Correct - covers main theme]\n");
            prompt.append("C. [Too broad - beyond passage scope]\n");
            prompt.append("D. [Mentioned but not main point]\n\n");

            prompt.append("Example 2 (Detail):\n");
            prompt.append("According to the passage, what happened in 1990?\n");
            prompt.append("A. [Information from different year]\n");
            prompt.append("B. [Correct - stated in passage]\n");
            prompt.append("C. [Related but not stated]\n");
            prompt.append("D. [Contradicts passage]\n\n");

            prompt.append("Example 3 (Inference):\n");
            prompt.append("What can be inferred about the author's opinion?\n");
            prompt.append("A. [Opposite of what's suggested]\n");
            prompt.append("B. [Correct - logically follows from passage]\n");
            prompt.append("C. [Not supported by passage]\n");
            prompt.append("D. [Beyond what can be inferred]\n\n");

            prompt.append("Example 4 (Vocabulary):\n");
            prompt.append("The word \"elaborate\" in paragraph 2 is closest in meaning to:\n");
            prompt.append("A. simple\n");
            prompt.append("B. detailed [CORRECT]\n");
            prompt.append("C. expensive\n");
            prompt.append("D. beautiful\n\n");

            prompt.append("DISTRACTOR PRINCIPLES FOR READING:\n");
            prompt.append("✓ Use information from passage but in wrong context\n");
            prompt.append("✓ Use extreme language (always, never) for false options\n");
            prompt.append("✓ Use partial truths (statement is partly true but incomplete)\n");
            prompt.append("✓ Reference content from different paragraph/section\n");
            prompt.append("✓ Make plausible if reader didn't read carefully\n\n");
        } else if ("REARRANGE".equals(questionType)) {
            prompt.append("📋 REARRANGE - READING-BASED:\n");
            prompt.append("⚠️ CRITICAL: Create COMPLETE, MEANINGFUL sentences from passage vocabulary\n\n");
            prompt.append("REQUIREMENTS:\n");
            prompt.append("✓ Must be a FULL sentence with complete meaning\n");
            prompt.append("✓ Use vocabulary and structures from the passage\n");
            prompt.append("✓ Include subject + verb + complete thought\n");
            prompt.append("✓ Use 5-8 words/phrases for optimal challenge\n");
            prompt.append("✓ Test understanding of passage content through sentence construction\n\n");
            prompt.append("EXAMPLES:\n");
            prompt.append("✅ \"The author argues that climate change is urgent\" (7 words)\n");
            prompt.append("✅ \"Scientists have discovered a new treatment method\" (6 words)\n");
            prompt.append("✅ \"Many students prefer online learning nowadays\" (5 words)\n\n");
            prompt.append("AVOID:\n");
            prompt.append("❌ \"The passage the mentions\" (incomplete)\n");
            prompt.append("❌ \"According to author\" (missing verb and object)\n");
            prompt.append("❌ \"Was very important\" (missing subject)\n\n");
        }

        prompt.append("⚠️ CRITICAL RULES FOR PASSAGE-BASED QUESTIONS:\n");
        prompt.append("1. ALL answers must be FINDABLE in the passage\n");
        prompt.append("2. Do NOT require outside knowledge not in the passage\n");
        prompt.append("3. Quote or paraphrase from passage when appropriate\n");
        prompt.append("4. Each question tests DIFFERENT part or aspect of passage\n");
        prompt.append("5. Cover different paragraphs/sections of the passage\n");
        prompt.append("6. Avoid questions with answers in first paragraph only\n");
        prompt.append("7. Questions should encourage full reading, not just skimming\n");
        prompt.append("8. Use passage vocabulary in questions naturally\n\n");

        prompt.append("🎯 QUALITY CHECKLIST:\n");
        prompt.append("✓ Question clearly worded and unambiguous\n");
        prompt.append("✓ Answer is definitively in the passage\n");
        prompt.append("✓ Three plausible but wrong distractors\n");
        prompt.append("✓ Tests comprehension, not memory tricks\n");
        prompt.append("✓ Appropriate difficulty for level: ").append(studentLevel).append("\n");
        prompt.append("✓ Professional, academic language\n");
        prompt.append("✓ Different question type (main idea/detail/inference/vocab)\n\n");

        appendJSONFormat(prompt, questionType);
        appendQuestionTypeRules(prompt, questionType);

        prompt.append("\n🚫 AVOID:\n");
        prompt.append("✗ Questions answerable without reading passage\n");
        prompt.append("✗ Questions requiring outside knowledge\n");
        prompt.append("✗ Multiple questions about the same detail\n");
        prompt.append("✗ Answers based on common sense rather than passage\n");
        prompt.append("✗ Trick questions with multiple valid interpretations\n");
        prompt.append("✗ Copying exact phrases from passage in distractors\n\n");

        prompt.append("🔥 CRITICAL REQUIREMENTS:\n");
        prompt.append("1. Return ONLY valid JSON - no markdown, no comments\n");
        prompt.append("2. Generate EXACTLY ").append(numberOfQuestions).append(" questions\n");
        prompt.append("3. ALL questions answerable from passage ONLY\n");
        prompt.append("4. Mix different comprehension skills\n");
        prompt.append("5. One clear correct answer per question\n");
        prompt.append("6. Three well-crafted distractors per question\n");
        prompt.append("7. THPT QG professional standard\n");
        prompt.append("8. Cover different parts of the passage\n");
        prompt.append("9. Progressive difficulty\n");
        prompt.append("10. Test genuine understanding\n");
        if ("REARRANGE".equals(questionType)) {
            prompt.append("11. ⚠️ FOR REARRANGE: VERIFY SENTENCE IS COMPLETE!\n");
            prompt.append("    - Minimum 5 words, optimal 5-8 words\n");
            prompt.append("    - Must have: subject + verb + complete meaning\n");
            prompt.append("    - Test yourself: \"Can this stand alone as a sentence?\"\n");
            prompt.append("    - Use vocabulary from the passage\n");
        }
        prompt.append("\n\n");

        prompt.append("Generate professional exam-quality questions now:\n");

        return prompt.toString();
    }

    // ========== KEEP ALL EXISTING HELPER METHODS ==========

    private void appendDCTypeInstructions(StringBuilder prompt, String dcType) {
        switch (dcType) {
            case "RE":
                prompt.append("📖 READING COMPREHENSION:\n");
                prompt.append("- Base ALL questions on the section content (reading passage)\n");
                prompt.append("- Test comprehension, inference, vocabulary in context\n");
                prompt.append("- Questions should reference specific parts of the passage\n");
                prompt.append("- Ensure questions can ONLY be answered by reading the passage\n");
                break;

            case "LI":
                prompt.append("🎧 LISTENING COMPREHENSION:\n");
                prompt.append("- Base ALL questions on the section content (transcript)\n");
                prompt.append("- Test listening comprehension and understanding\n");
                prompt.append("- Questions should reference specific information from the transcript\n");
                prompt.append("- Ensure questions can ONLY be answered by understanding the transcript\n");
                break;

            default:
                prompt.append("- Generate questions based on the section content\n");
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

    private List<QuestionDto> parseQuestionsFromResponse(String jsonResponse) {
        try {
            log.debug("Parsing questions from response");

            // Additional cleaning attempt before parsing
            String cleaned = cleanJsonResponse(jsonResponse);

            // Log first 500 chars for debugging if parsing fails
            if (cleaned.length() > 500) {
                log.debug("JSON preview (first 500 chars): {}", cleaned.substring(0, 500));
            } else {
                log.debug("Full JSON: {}", cleaned);
            }

            JsonNode rootNode;
            try {
                rootNode = objectMapper.readTree(cleaned);
            } catch (Exception parseEx) {
                log.error("Failed to parse JSON. Full response: {}", cleaned);
                throw new RuntimeException("Invalid JSON format from AI: " + parseEx.getMessage(), parseEx);
            }

            JsonNode questionsNode = rootNode.get("questions");

            if (questionsNode == null || !questionsNode.isArray()) {
//                log.error("Invalid response structure. Root keys: {}",
//                        rootNode.fieldNames() != null ?
//                                String.join(", ", () -> rootNode.fieldNames()) : "none");
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
                    log.debug("Problematic question node: {}", questionNode.toString());
                }
            }

            if (questions.isEmpty()) {
                throw new RuntimeException("No questions were successfully parsed from " + questionsNode.size() + " attempts");
            }

            ensureUniquePositionIds(questions);

            log.info("Successfully parsed {} out of {} questions", questions.size(), questionsNode.size());

            return questions;

        } catch (Exception e) {
            log.error("Error parsing questions response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse questions: " + e.getMessage(), e);
        }
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
            case "WRITING": return "Writing Exercise";
            default: return questionType + " Exercise";
        }
    }

    private void appendQuestionTypeRules(StringBuilder prompt, String questionType) {
        prompt.append("📚 DETAILED SPECIFICATIONS FOR ").append(questionType).append(":\n\n");

        switch (questionType) {

            case "MULTIPLE_CHOICE":
                prompt.append("FORMAT: Clear question stem + 4 options (A, B, C, D)\n");
                prompt.append("REQUIREMENTS:\n");
                prompt.append("- Exactly 4 options per question\n");
                prompt.append("- Exactly 1 option with isCorrect=true\n");
                prompt.append("- positionId=null for all options\n");
                prompt.append("- Options should be similar length (avoid obvious length cues)\n");
                prompt.append("- All options must be grammatically parallel\n");
                prompt.append("- Randomize position of correct answer (don't always use B or C)\n\n");

                prompt.append("DISTRACTOR QUALITY STANDARDS:\n");
                prompt.append("✓ Plausible - could be correct if student misunderstands\n");
                prompt.append("✓ Common errors - represent typical mistakes students make\n");
                prompt.append("✓ Related to topic - not random unrelated words\n");
                prompt.append("✓ Same grammatical form as correct answer\n");
                prompt.append("✓ Test understanding, not memory or tricks\n\n");

                prompt.append("PROFESSIONAL EXAMPLES:\n\n");
                prompt.append("Example 1 (Grammar - Verb Tense):\n");
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

                prompt.append("Example 2 (Vocabulary - Collocation):\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"The company decided to _____ a new marketing strategy to increase sales.\",\n")
                        .append("  \"orderNumber\": 2,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"MULTIPLE_CHOICE\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"opt1\", \"value\": \"make\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt2\", \"value\": \"implement\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt3\", \"value\": \"do\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt4\", \"value\": \"construct\", \"isCorrect\": false, \"positionId\": null}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");

                prompt.append("Example 3 (Grammar - Preposition):\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"She is very good _____ playing the piano and often performs at concerts.\",\n")
                        .append("  \"orderNumber\": 3,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"MULTIPLE_CHOICE\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"opt1\", \"value\": \"at\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt2\", \"value\": \"in\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt3\", \"value\": \"on\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt4\", \"value\": \"for\", \"isCorrect\": false, \"positionId\": null}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");
                break;

            case "TRUE_OR_FALSE":
                prompt.append("REQUIREMENTS:\n");
                prompt.append("- 2 options: \"True\" and \"False\"\n");
                prompt.append("- Exactly 1 option with isCorrect=true\n");
                prompt.append("- positionId=null for both\n");
                prompt.append("- Statement should be clear and unambiguous\n");
                prompt.append("- Based on passage content (for reading) or clear grammar rule\n\n");

                prompt.append("PROFESSIONAL EXAMPLE:\n");
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
                prompt.append("⚠️ CRITICAL FORMAT - NATURAL ENGLISH TEXTBOOK STYLE:\n\n");
                prompt.append("FORMAT: \"Text [[pos_xxxxxx]](hint) more text.\"\n");
                prompt.append("- The placeholder [[pos_xxxxxx]] shows WHERE to fill\n");
                prompt.append("- The (hint) guides what TYPE of word/form is needed\n");
                prompt.append("- xxxxxx is a random 6-character ID (lowercase a-z and 0-9)\n");
                prompt.append("- positionId in data MUST match the xxxxxx\n");
                prompt.append("- Each blank has ONLY 1 correct answer\n");
                prompt.append("- No distractors - just the correct answer\n\n");

                prompt.append("HINT TYPES:\n");
                prompt.append("- (verb): any verb form\n");
                prompt.append("- (be): form of 'be' verb\n");
                prompt.append("- (go): specific verb with conjugation\n");
                prompt.append("- (adjective): adjective form\n");
                prompt.append("- (noun): noun form\n");
                prompt.append("- (preposition): preposition\n\n");

                prompt.append("PROFESSIONAL EXAMPLES:\n\n");
                prompt.append("Example 1 (Single blank - Grammar):\n");
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

                prompt.append("Example 2 (Multiple blanks - Contextual):\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"She [[pos_x1y2z3]](be) very tired because she [[pos_a4b5c6]](work) all day yesterday.\",\n")
                        .append("  \"orderNumber\": 2,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"FILL_IN_THE_BLANK\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"ans1\", \"value\": \"was\", \"isCorrect\": true, \"positionId\": \"x1y2z3\"},\n")
                        .append("      {\"id\": \"ans2\", \"value\": \"had been working\", \"isCorrect\": true, \"positionId\": \"a4b5c6\"}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");

                prompt.append("Example 3 (Vocabulary in context):\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"The government plans to [[pos_m8n9p0]](implement) new policies to reduce pollution in urban areas.\",\n")
                        .append("  \"orderNumber\": 3,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"FILL_IN_THE_BLANK\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"ans1\", \"value\": \"implement\", \"isCorrect\": true, \"positionId\": \"m8n9p0\"}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");

                prompt.append("⚠️ CRITICAL RULES:\n");
                prompt.append("- Context must make correct answer clear to competent students\n");
                prompt.append("- Avoid sentences with multiple possible correct answers\n");
                prompt.append("- Test ONE clear grammar/vocabulary point per blank\n");
                prompt.append("- Each positionId must be UNIQUE (never reuse)\n");
                break;

            case "DROPDOWN":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders\n");
                prompt.append("- xxxxxx is a random 6-character ID using lowercase a-z and 0-9\n");
                prompt.append("- Each dropdown has 3–4 options, exactly 1 with isCorrect=true\n");
                prompt.append("- All options for one dropdown share the same positionId\n");
                prompt.append("- Options should be grammatically similar but contextually different\n\n");

                prompt.append("PROFESSIONAL EXAMPLE:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"The company [[pos_k5l6m7]] expand into Asian markets next year.\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"DROPDOWN\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"opt1\", \"value\": \"plans to\", \"isCorrect\": true, \"positionId\": \"k5l6m7\"},\n")
                        .append("      {\"id\": \"opt2\", \"value\": \"is planning\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"},\n")
                        .append("      {\"id\": \"opt3\", \"value\": \"will plan\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"},\n")
                        .append("      {\"id\": \"opt4\", \"value\": \"plan\", \"isCorrect\": false, \"positionId\": \"k5l6m7\"}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");
                break;

            case "MULTIPLE_SELECT":
                prompt.append("REQUIREMENTS:\n");
                prompt.append("- 4–6 options total\n");
                prompt.append("- 2–3 options with isCorrect=true\n");
                prompt.append("- positionId=null for all\n");
                prompt.append("- Clearly indicate in question: \"Select all correct answers\"\n");
                prompt.append("- Each correct answer should be independently correct\n\n");

                prompt.append("PROFESSIONAL EXAMPLE:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"According to the passage, which of the following are benefits of renewable energy? (Select all correct answers)\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"MULTIPLE_SELECT\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"opt1\", \"value\": \"Reduces carbon emissions\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt2\", \"value\": \"Decreases dependence on fossil fuels\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt3\", \"value\": \"Increases air pollution\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt4\", \"value\": \"Creates sustainable jobs\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt5\", \"value\": \"Depletes natural resources\", \"isCorrect\": false, \"positionId\": null}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");
                break;

            case "DRAG_AND_DROP":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders for drop zones and do not have any ()\n");
                prompt.append("- xxxxxx is a random 6-character ID using lowercase a-z and 0-9\n");
                prompt.append("- Each item in data must have positionId corresponding to its correct drop zone\n");
                prompt.append("- Each draggable item corresponds to exactly one drop zone\n");
                prompt.append("- Items should be logically related to their zones\n");
                break;

            case "REARRANGE":
                prompt.append("⚠️ CRITICAL FORMAT - SENTENCE REARRANGEMENT:\n\n");
                prompt.append("CONCEPT: Students drag and drop words/phrases to form a COMPLETE, MEANINGFUL sentence\n\n");
                prompt.append("FORMAT RULES:\n");
                prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders for EACH word/phrase\n");
                prompt.append("- Number of placeholders = number of words/phrases in complete sentence\n");
                prompt.append("- NO other text in questionText except placeholders\n");
                prompt.append("- Each placeholder represents ONE movable word/phrase\n");
                prompt.append("- xxxxxx is random 6-char ID (lowercase a-z and 0-9)\n");
                prompt.append("- Each item must have positionId matching its placeholder\n");
                prompt.append("- All items have isCorrect=true (no false answers)\n");
                prompt.append("- When arranged in correct order, forms a GRAMMATICALLY CORRECT and MEANINGFUL sentence\n\n");

                prompt.append("⚠️ SENTENCE QUALITY REQUIREMENTS:\n");
                prompt.append("✓ Must be a COMPLETE sentence (subject + verb + complete thought)\n");
                prompt.append("✓ Must have CLEAR meaning (not fragments or incomplete ideas)\n");
                prompt.append("✓ Must be grammatically correct when arranged properly\n");
                prompt.append("✓ Use 5-8 words/phrases for optimal difficulty\n");
                prompt.append("✓ Test sentence structure, word order, grammar understanding\n");
                prompt.append("✓ Should be somewhat challenging to rearrange (not too obvious)\n\n");

                prompt.append("PROFESSIONAL EXAMPLES:\n\n");

                prompt.append("Example 1 (Present Perfect - 6 words):\n");
                prompt.append("Correct sentence: \"She has been studying English recently\"\n");
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

                prompt.append("Example 2 (Conditional - 7 words):\n");
                prompt.append("Correct sentence: \"If I had known, I would have helped you\"\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"[[pos_x1y2z3]] [[pos_a4b5c6]] [[pos_d7e8f9]] [[pos_g0h1i2]] [[pos_j3k4l5]] [[pos_m6n7o8]] [[pos_p9q0r1]]\",\n")
                        .append("  \"orderNumber\": 2,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"REARRANGE\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"item1\", \"value\": \"If\", \"isCorrect\": true, \"positionId\": \"x1y2z3\"},\n")
                        .append("      {\"id\": \"item2\", \"value\": \"I\", \"isCorrect\": true, \"positionId\": \"a4b5c6\"},\n")
                        .append("      {\"id\": \"item3\", \"value\": \"had known,\", \"isCorrect\": true, \"positionId\": \"d7e8f9\"},\n")
                        .append("      {\"id\": \"item4\", \"value\": \"I\", \"isCorrect\": true, \"positionId\": \"g0h1i2\"},\n")
                        .append("      {\"id\": \"item5\", \"value\": \"would have\", \"isCorrect\": true, \"positionId\": \"j3k4l5\"},\n")
                        .append("      {\"id\": \"item6\", \"value\": \"helped\", \"isCorrect\": true, \"positionId\": \"m6n7o8\"},\n")
                        .append("      {\"id\": \"item7\", \"value\": \"you\", \"isCorrect\": true, \"positionId\": \"p9q0r1\"}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");

                prompt.append("Example 3 (Simple sentence - 5 words):\n");
                prompt.append("Correct sentence: \"My brother plays football every weekend\"\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"[[pos_s2t3u4]] [[pos_v5w6x7]] [[pos_y8z9a0]] [[pos_b1c2d3]] [[pos_e4f5g6]]\",\n")
                        .append("  \"orderNumber\": 3,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"REARRANGE\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"item1\", \"value\": \"My brother\", \"isCorrect\": true, \"positionId\": \"s2t3u4\"},\n")
                        .append("      {\"id\": \"item2\", \"value\": \"plays\", \"isCorrect\": true, \"positionId\": \"v5w6x7\"},\n")
                        .append("      {\"id\": \"item3\", \"value\": \"football\", \"isCorrect\": true, \"positionId\": \"y8z9a0\"},\n")
                        .append("      {\"id\": \"item4\", \"value\": \"every\", \"isCorrect\": true, \"positionId\": \"b1c2d3\"},\n")
                        .append("      {\"id\": \"item5\", \"value\": \"weekend\", \"isCorrect\": true, \"positionId\": \"e4f5g6\"}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");

                prompt.append("⚠️ COMMON MISTAKES TO AVOID:\n");
                prompt.append("✗ Incomplete sentences: \"The teacher the explains\" ❌\n");
                prompt.append("✗ Missing key words: \"Students are learning new\" ❌\n");
                prompt.append("✗ Fragment phrases: \"Because he was late\" ❌\n");
                prompt.append("✗ Too few words (less than 5): Not challenging enough\n");
                prompt.append("✗ Too many words (more than 10): Too confusing\n\n");

                prompt.append("✅ CORRECT APPROACH:\n");
                prompt.append("✓ \"The teacher explains the lesson clearly\" ✅ (complete, meaningful)\n");
                prompt.append("✓ \"Students are learning new vocabulary today\" ✅ (complete, meaningful)\n");
                prompt.append("✓ \"She has been working here since 2020\" ✅ (complete, meaningful)\n\n");

                prompt.append("🎯 SENTENCE CREATION TIPS:\n");
                prompt.append("- Think: \"Could this stand alone as a complete sentence?\"\n");
                prompt.append("- Include subject, verb, and complete the thought\n");
                prompt.append("- Add time expressions, objects, or complements as needed\n");
                prompt.append("- Make it interesting and relevant to lesson content\n");
                prompt.append("- Test different grammatical structures (tenses, voices, conditionals)\n\n");
                break;

            case "REWRITE":
                prompt.append("REQUIREMENTS:\n");
                prompt.append("- Open-ended rewriting exercise\n");
                prompt.append("- Must have ONLY 1 correct answer (isCorrect=true)\n");
                prompt.append("- positionId=null\n");
                prompt.append("- Answer must be grammatically correct and preserve meaning\n");
                prompt.append("- Specify clearly what transformation to make\n\n");

                prompt.append("PROFESSIONAL EXAMPLE:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"Rewrite the following sentence using 'so...that': The weather was too cold for us to go swimming.\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"REWRITE\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"ans1\", \"value\": \"The weather was so cold that we couldn't go swimming.\", \"isCorrect\": true, \"positionId\": null}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n\n");
                break;

            default:
                prompt.append("Follow standard question format with all required fields.\n");
        }

        prompt.append("\n✅ GLOBAL VALIDATION CHECKLIST:\n");
        prompt.append("□ Use ONLY lowercase letters (a-z) and numbers (0-9) for position IDs\n");
        prompt.append("□ Valid JSON with proper formatting (no trailing commas)\n");
        prompt.append("□ For FILL_IN_THE_BLANK: Natural format \"text [[pos_xxxxx]](hint) text\"\n");
        prompt.append("□ For position-based types: questionText includes [[pos_xxxxxx]] placeholders\n");
        prompt.append("□ positionId matches xxxxxx exactly\n");
        prompt.append("□ Each position ID is UNIQUE across entire question set\n");
        prompt.append("□ Professional language and clear instructions\n");
        prompt.append("□ One definitively correct answer per question\n");
        prompt.append("□ Well-crafted, plausible distractors (for MC/Dropdown)\n");
    }

    @Override
    public String callOpenAI(String prompt) {
        String url = UriComponentsBuilder
                .fromHttpUrl(endpoint + "/openai/deployments/gpt-4o-mini/chat/completions")
                .queryParam("api-version", API_VERSION)
                .toUriString();

        RestTemplate restTemplate = new RestTemplate();

        Map<String, Object> requestBody = Map.of(
                "messages", new Object[]{
                        Map.of("role", "system", "content", "You are an expert English teacher. You MUST return ONLY valid JSON without any markdown formatting, code blocks, comments, or explanations. The JSON must be parseable directly. Do NOT include trailing commas or any non-standard JSON syntax."),
                        Map.of("role", "user", "content", prompt)
                },
                "max_tokens", 4000,
                "temperature", 0.7,
                "top_p", 0.9
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

                    // Clean the response
                    content = cleanJsonResponse(content);

                    log.debug("OpenAI response (cleaned): {}", content);
                    return content;
                }
            }
        } catch (Exception e) {
            log.error("Error calling OpenAI", e);
            throw new RuntimeException("Failed to call OpenAI: " + e.getMessage(), e);
        }

        throw new RuntimeException("No response from OpenAI");
    }

    /**
     * ✅ NEW: Comprehensive JSON cleaning to handle various malformed responses
     */
    private String cleanJsonResponse(String content) {
        if (content == null) {
            return "";
        }

        content = content.trim();

        // Remove markdown code blocks
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }

        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }

        content = content.trim();

        content = content.replaceAll("//.*?\\n", "\n"); // Single-line comments
        content = content.replaceAll("/\\*.*?\\*/", ""); // Multi-line comments

        content = content.replaceAll(",\\s*}", "}");
        content = content.replaceAll(",\\s*]", "]");

        content = content.replace("'", "'");
        content = content.replace("'", "'");

        return content.trim();
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
        int dataIndex = 0;
        for (JsonNode itemNode : dataNode) {
            dataIndex++;
            try {
                DataItem dataItem = parseDataItem(itemNode);
                dataItems.add(dataItem);
            } catch (Exception e) {
                log.error("Error parsing data item {} in question {}: {}", dataIndex, index, e.getMessage());
                throw new RuntimeException("Failed to parse data item " + dataIndex + " in question " + index, e);
            }
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
                    log.info("Replaced duplicate '{}' with '{}'", oldId, newId);
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

        log.info("Successfully parsed {} sections with total {} questions from file",
                sections.size(), sections.stream().mapToInt(s -> s.getQuestions().size()).sum());

        return sections;
    }

    @Override
    @Transactional(readOnly = true)
    public GenerateReadingPassageResponse generateReadingPassage(GenerateReadingPassageRequest request) {
        log.info("Generating reading passage for challengeId: {} with {} paragraphs",
                request.getChallengeId(), request.getNumberOfParagraphs());

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // ✅ FIX: Eager load for reading passage generation
        ChallengeContext context = eagerLoadChallengeContext(challenge);

        int wordsPerParagraph = wordsPerParagraphDefault;
        log.info("Generating passage with {} words per paragraph", wordsPerParagraph);

        String prompt = buildReadingPassagePrompt(
                request.getNumberOfParagraphs(),
                wordsPerParagraph,
                request.getDescription(),
                "",
                context.studentLevel
        );

        String aiResponse = callOpenAI(prompt);
        GenerateReadingPassageResponse response = parseReadingPassageResponse(aiResponse, context.studentLevel);

        log.info("Successfully generated passage: {} paragraphs, {} words",
                response.getNumberOfParagraphs(), response.getTotalWords());

        return response;
    }

    private String buildReadingPassagePrompt(
            int numberOfParagraphs,
            int wordsPerParagraph,
            String description,
            String contextInfo,
            String level) {

        StringBuilder prompt = new StringBuilder();

        String levelInstructions = getLevelInstructions(level);

        prompt.append("You are an expert English teacher creating reading passages.\n\n");

        prompt.append(levelInstructions).append("\n");

        if (description != null && !description.isBlank()) {
            prompt.append("🔥 USER REQUIREMENTS (ABSOLUTE PRIORITY) 🔥\n");
            prompt.append(description).append("\n\n");
        }

        prompt.append("CONTEXT:\n");
        prompt.append(contextInfo).append("\n");

        prompt.append("TASK:\n");
        prompt.append("Generate a reading passage with EXACTLY ").append(numberOfParagraphs).append(" paragraph(s)\n");
        prompt.append("Level: ").append(level).append("\n");
        prompt.append("Each paragraph: approximately ").append(wordsPerParagraph).append(" words\n\n");

        prompt.append("REQUIREMENTS:\n");
        prompt.append("- Create engaging educational content appropriate for ").append(level).append(" level\n");
        prompt.append("- Use suitable vocabulary and grammar for this level\n");
        prompt.append("- Each paragraph has clear main idea\n");
        prompt.append("- Logical flow between paragraphs\n");
        prompt.append("- Content should be interesting and educational\n\n");

        prompt.append("JSON FORMAT:\n");
        prompt.append("{\n");
        prompt.append("  \"passage\": \"Full text with paragraphs separated by \\n\\n\",\n");
        prompt.append("  \"numberOfParagraphs\": ").append(numberOfParagraphs).append(",\n");
        prompt.append("  \"totalWords\": <actual count>\n");
        prompt.append("}\n\n");

        prompt.append("Return ONLY valid JSON - no markdown, no explanations.\n");

        return prompt.toString();
    }

    private GenerateReadingPassageResponse parseReadingPassageResponse(String jsonResponse, String level) {
        try {
            String clean = jsonResponse.trim()
                    .replaceFirst("^```json\\s*", "")
                    .replaceFirst("^```\\s*", "")
                    .replaceFirst("```\\s*$", "")
                    .trim();

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

        prompt.append("You are an expert at parsing and structuring educational content.\n\n");

        prompt.append("TASK:\n");
        prompt.append("The user has provided a file containing EXISTING questions with answers.\n");
        prompt.append("Your job is to:\n");
        prompt.append("1. PARSE the questions and answers from the text\n");
        prompt.append("2. IDENTIFY the question type (MULTIPLE_CHOICE, TRUE_OR_FALSE, FILL_IN_THE_BLANK, etc.)\n");
        prompt.append("3. STRUCTURE them into the required JSON format\n");
        prompt.append("4. Group similar question types into sections\n\n");

        if (description != null && !description.isBlank()) {
            prompt.append("ADDITIONAL INSTRUCTIONS:\n");
            prompt.append(description).append("\n\n");
        }

        prompt.append("FILE CONTENT:\n");
        prompt.append(fileContent).append("\n\n");

        prompt.append("CRITICAL JSON FORMAT:\n");
        prompt.append("{\n");
        prompt.append("  \"sections\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"section\": {\n");
        prompt.append("        \"sectionTitle\": \"Question Type Name\",\n");
        prompt.append("        \"sectionsContent\": null,\n");
        prompt.append("        \"orderNumber\": 1,\n");
        prompt.append("        \"resourceType\": \"NONE\"\n");
        prompt.append("      },\n");
        prompt.append("      \"questions\": [\n");
        prompt.append("        {\n");
        prompt.append("          \"questionText\": \"extracted question text\",\n");
        prompt.append("          \"orderNumber\": 1,\n");
        prompt.append("          \"score\": 1.0,\n");
        prompt.append("          \"questionType\": \"MULTIPLE_CHOICE|TRUE_OR_FALSE|FILL_IN_THE_BLANK|etc\",\n");
        prompt.append("          \"content\": {\n");
        prompt.append("            \"data\": [\n");
        prompt.append("              {\n");
        prompt.append("                \"id\": \"opt1\",\n");
        prompt.append("                \"value\": \"option text\",\n");
        prompt.append("                \"isCorrect\": true or false,\n");
        prompt.append("                \"positionId\": \"null or position ID\"\n");
        prompt.append("              }\n");
        prompt.append("            ]\n");
        prompt.append("          }\n");
        prompt.append("        }\n");
        prompt.append("      ]\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");

        prompt.append("QUESTION TYPE DETECTION RULES:\n\n");

        prompt.append("1. MULTIPLE_CHOICE:\n");
        prompt.append("   - Has multiple options (A, B, C, D or 1, 2, 3, 4)\n");
        prompt.append("   - One option marked as correct (✓, *, correct, answer, etc.)\n");
        prompt.append("   - positionId: null for all options\n");
        prompt.append("   Example: \"1. Question? A. option1 B. option2 ✓ C. option3\"\n\n");

        prompt.append("2. TRUE_OR_FALSE:\n");
        prompt.append("   - Question asks True or False\n");
        prompt.append("   - Answer is True or False\n");
        prompt.append("   - Create 2 options: True and False, mark correct one\n");
        prompt.append("   - positionId: null\n\n");

        prompt.append("3. FILL_IN_THE_BLANK:\n");
        prompt.append("   - Question has blanks (___, ....., [blank], etc.)\n");
        prompt.append("   - Answer(s) provided separately\n");
        prompt.append("   - CRITICAL: Convert to natural format: \"I [[pos_xxxxx]](be) a student.\"\n");
        prompt.append("   - Generate random 6-char lowercase IDs for each blank\n");
        prompt.append("   - positionId in data must match the xxxxx part\n");
        prompt.append("   - Include hint in parentheses (be, verb, adjective, etc.)\n");
        prompt.append("   Example input: \"I _____ a student.\" Answer: am (verb 'be')\n");
        prompt.append("   Example output: questionText: \"I [[pos_a7k3m2]](be) a student.\"\n");
        prompt.append("                   data: [{\"value\": \"am\", \"isCorrect\": true, \"positionId\": \"a7k3m2\"}]\n\n");

        prompt.append("4. DROPDOWN:\n");
        prompt.append("   - Similar to FILL_IN_THE_BLANK but with multiple options per blank\n");
        prompt.append("   - Use [[pos_xxxxx]] format\n");
        prompt.append("   - Multiple options share same positionId\n\n");

        prompt.append("5. MULTIPLE_SELECT:\n");
        prompt.append("   - Multiple correct answers\n");
        prompt.append("   - Usually stated \"Select all that apply\" or multiple ✓ marks\n");
        prompt.append("   - positionId: null\n\n");

        prompt.append("GROUPING RULES:\n");
        prompt.append("- Group questions of the SAME TYPE into one section\n");
        prompt.append("- Create separate sections for different question types\n");
        prompt.append("- Section title should describe the question type (e.g., \"Multiple Choice Questions\", \"Fill in the Blank\")\n\n");

        prompt.append("🔥 CRITICAL REQUIREMENTS:\n");
        prompt.append("1. Return ONLY valid JSON - no markdown, no explanations\n");
        prompt.append("2. EXTRACT all questions from the file - don't skip any\n");
        prompt.append("3. PRESERVE the original question text and answers\n");
        prompt.append("4. IDENTIFY correct answers from markers like: ✓, *, (correct), Answer:, etc.\n");
        prompt.append("5. For FILL_IN_THE_BLANK: Use natural format with hints like \"...(be)\"\n");
        prompt.append("6. Each position ID must be UNIQUE across all questions\n");
        prompt.append("7. Group by question type into sections\n");
        prompt.append("8. Maintain question order within each type\n\n");

        prompt.append("Return ONLY the JSON object, no additional text.\n");

        return prompt.toString();
    }

    private List<SectionWithQuestionsDto> parseMultipleSectionsResponse(String jsonResponse) {
        try {
            log.info("Parsing multiple sections from response");
            log.debug("Raw JSON: {}", jsonResponse);

            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode sectionsNode = rootNode.get("sections");

            if (sectionsNode == null || !sectionsNode.isArray()) {
                log.error("Missing or invalid 'sections' field");
                throw new RuntimeException("Invalid response: missing or invalid 'sections' array");
            }

            List<SectionWithQuestionsDto> sections = new ArrayList<>();
            int sectionIndex = 0;

            for (JsonNode sectionNode : sectionsNode) {
                sectionIndex++;
                try {
                    JsonNode sectionInfoNode = sectionNode.get("section");
                    if (sectionInfoNode == null) {
                        log.error("Missing 'section' field in section {}", sectionIndex);
                        continue;
                    }

                    SectionDto section = new SectionDto();

                    JsonNode titleNode = sectionInfoNode.get("sectionTitle");
                    section.setSectionTitle(titleNode != null ? titleNode.asText() : null);

                    section.setSectionsContent(null);

                    JsonNode orderNode = sectionInfoNode.get("orderNumber");
                    section.setOrderNumber(orderNode != null ? orderNode.asInt() : sectionIndex);

                    JsonNode resourceTypeNode = sectionInfoNode.get("resourceType");
                    section.setResourceType(resourceTypeNode != null ? resourceTypeNode.asText() : "NONE");

                    JsonNode questionsNode = sectionNode.get("questions");
                    if (questionsNode == null || !questionsNode.isArray()) {
                        log.warn("No questions found in section {}", sectionIndex);
                        continue;
                    }

                    List<QuestionDto> questions = new ArrayList<>();
                    int questionIndex = 0;

                    for (JsonNode questionNode : questionsNode) {
                        questionIndex++;
                        try {
                            QuestionDto question = parseQuestion(questionNode, questionIndex, null);
                            questions.add(question);
                        } catch (Exception e) {
                            log.error("Error parsing question {} in section {}: {}",
                                    questionIndex, sectionIndex, e.getMessage());
                        }
                    }

                    if (!questions.isEmpty()) {
                        ensureUniquePositionIds(questions);

                        SectionWithQuestionsDto sectionWithQuestions = new SectionWithQuestionsDto(section, questions);
                        sections.add(sectionWithQuestions);
                        log.info("Parsed section {} with {} questions", sectionIndex, questions.size());
                    }

                } catch (Exception e) {
                    log.error("Error parsing section {}: {}", sectionIndex, e.getMessage());
                }
            }

            if (sections.isEmpty()) {
                throw new RuntimeException("No sections were successfully parsed");
            }

            return sections;

        } catch (Exception e) {
            log.error("Error parsing multiple sections response: {}", e.getMessage(), e);
            log.error("Problematic JSON: {}", jsonResponse);
            throw new RuntimeException("Failed to parse response: " + e.getMessage(), e);
        }
    }

    @Override
    public GenerateDistractorsResponse generateDistractors(GenerateDistractorsRequest request) {
        log.info("Generating distractors for question");

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

        prompt.append("Generate ").append(numberOfDistractors).append(" wrong answer(s) for this multiple choice question.\n\n");

        prompt.append("Question: ").append(request.getQuestionText()).append("\n");
        prompt.append("Correct answer: ").append(request.getCorrectAnswer()).append("\n");

        if (request.getExistingDistractors() != null && !request.getExistingDistractors().isEmpty()) {
            prompt.append("Existing wrong answers: ");
            prompt.append(String.join(", ", request.getExistingDistractors()));
            prompt.append("\n");
        }

        prompt.append("\nRules:\n");
        prompt.append("- Make wrong answers plausible but clearly incorrect\n");
        prompt.append("- Keep similar length and format as correct answer\n");
        prompt.append("- Don't repeat existing answers\n");
        prompt.append("- Return ONLY a JSON array of strings\n\n");

        prompt.append("Example output: [\"wrong answer 1\", \"wrong answer 2\"]\n\n");
        prompt.append("Return ONLY the JSON array, no explanation:");

        return prompt.toString();
    }

    private List<String> parseDistractorsResponse(String jsonResponse) {
        try {
            String cleaned = jsonResponse.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            JsonNode rootNode = objectMapper.readTree(cleaned);

            List<String> distractors = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    distractors.add(node.asText());
                }
            }

            return distractors;

        } catch (Exception e) {
            log.error("Error parsing distractors response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse distractors: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SectionWithQuestionsDto> parseQuestionsFromText(
            String textContent,
            String description) {

        log.info("Starting to parse questions from text input (length: {})", textContent.length());

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

        log.info("Successfully parsed {} sections with total {} questions from text",
                sections.size(), sections.stream().mapToInt(s -> s.getQuestions().size()).sum());

        return sections;
    }

    @Override
    public TranslationResponse translate(String text) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Translation request for text: {}", traceId, text);

        // Validate input text
        if (text == null || text.trim().isEmpty()) {
            log.error("[{}] Text is required for translation", traceId);
            throw new ApiException(Const.VALIDATION.MISSING_FIELD, HttpStatus.BAD_REQUEST.value());
        }

        try {
            // Build API URL
            String url = String.format(
                    "%stranslate?api-version=3.0&from=%s&to=%s",
                    translatorEndpoint, "en", "vi"
            );
            log.debug("[{}] Translator API URL: {}", traceId, url);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Ocp-Apim-Subscription-Key", translatorKey);
            headers.set("Ocp-Apim-Subscription-Region", translatorRegion);

            // Prepare request body
            List<Map<String, String>> body = List.of(Map.of("text", text));
            HttpEntity<List<Map<String, String>>> entity = new HttpEntity<>(body, headers);

            log.debug("[{}] Sending request to Azure Translator API", traceId);

            // Call Azure Translator API
            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    List.class
            );

            // Check response status
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("[{}] Invalid response from Azure Translator API. Status: {}",
                        traceId, response.getStatusCode());
                throw new ApiException(Const.TRANSLATOR.API_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.value());
            }

            // Extract translation result
            Map<String, Object> first = (Map<String, Object>) response.getBody().get(0);
            List<Map<String, Object>> translations = (List<Map<String, Object>>) first.get("translations");
            String translatedText = (String) translations.get(0).get("text");

            log.info("[{}] Translation successful. Original: '{}', Translated: '{}'",
                    traceId, text, translatedText);

            // Build response
            return TranslationResponse.builder()
                    .originalText(text)
                    .translatedText(translatedText)
                    .fromLanguage("en")
                    .toLanguage("vi")
                    .build();

        } catch (RestClientException e) {
            log.error("[{}] Error calling Translator API: {}", traceId, e.getMessage(), e);
            throw new ApiException(Const.TRANSLATOR.API_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.value());

        } catch (ApiException e) {
            throw e;

        } catch (Exception e) {
            log.error("[{}] Translation failed: {}", traceId, e.getMessage(), e);
            throw new ApiException(Const.TRANSLATOR.TRANSLATION_FAILED, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }
}