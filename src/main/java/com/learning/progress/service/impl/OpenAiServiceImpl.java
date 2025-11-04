package com.learning.progress.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import com.learning.progress.dto.ai.*;
import com.learning.progress.dto.challenge.section.*;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.DailyChallengeRepository;
import com.learning.progress.repository.SubmissionQuestionRepository;
import com.learning.progress.service.OpenAiService;
import com.learning.progress.util.FileContentExtractor;
import com.learning.progress.util.TraceUtil;
import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
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
    private final SubmissionQuestionRepository submissionQuestionRepository;

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

    @Value("${azure.speech.key}")
    private String speechKey;

    @Value("${azure.speech.region}")
    private String speechRegion;

    // Changed: create RestTemplate in init() so we can configure timeouts and reuse it
    private RestTemplate restTemplate;

    private static final String API_VERSION = "2025-04-01-preview";
    private static final Pattern POSITION_PATTERN = Pattern.compile("\\[\\[pos_([a-z0-9]+)\\]\\]");

    // Centralized system role reused in chat requests
    private static final String SYSTEM_ROLE_JSON_INSTRUCTION =
            "You are an expert English teacher. Return ONLY valid JSON (no markdown, no comments, no extra text). " +
            "Do NOT include trailing commas or non-standard JSON syntax.";

    public OpenAiServiceImpl(DailyChallengeRepository dailyChallengeRepository, SubmissionQuestionRepository submissionQuestionRepository) {
        this.dailyChallengeRepository = dailyChallengeRepository;
        this.submissionQuestionRepository = submissionQuestionRepository;
    }

    @PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(Math.max(1, threadPoolSize));
        log.info("Initialized thread pool with size: {}", threadPoolSize);

        // Configure RestTemplate with sensible timeouts to avoid long hangs
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int connectTimeoutMs = 5000;
        int readTimeoutMs = 300000; // 5 minutes for long AI calls
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

        DailyChallenge challenge = dailyChallengeRepository.findByIdWithFullHierarchy(request.getChallengeId())
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
                        sectionOrder++,
                        request.getAge()
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
                                questionOrder++,
                                request.getAge()
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

        // Set defaults if not found
        if (context.classLessonContent == null) {
            context.classLessonContent = "No lesson content available";
        }
        if (context.classChapterName == null) {
            context.classChapterName = "No chapter name available";
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
        String classChapterName;
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
                        batch.size(),
                        firstTask.age
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
                        firstTask.dailyChallengeType,
                        firstTask.age
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
        Integer age;

        QuestionGenerationTask(ChallengeContext context, String questionType,
                               String userDescription, String contextInfo, int sectionOrder, Integer age) {
            this.context = context;
            this.questionType = questionType;
            this.userDescription = userDescription;
            this.contextInfo = contextInfo;
            this.sectionOrder = sectionOrder;
            this.age = age;
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
        Integer age;

        ContentBasedQuestionTask(ChallengeContext context, SectionDto section,
                                 String questionType, String userDescription,
                                 String contextInfo, String dailyChallengeType, int orderNumber,
                                 Integer age) {
            this.context = context;
            this.section = section;
            this.questionType = questionType;
            this.userDescription = userDescription;
            this.contextInfo = contextInfo;
            this.dailyChallengeType = dailyChallengeType;
            this.orderNumber = orderNumber;
            this.age = age;
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

    private String getAgeBasedLevelInstructions(Integer age) {
        if (age == null) {
            age = 12; // default to middle level
        }

        if (age >= 6 && age <= 8) {
            // 6-8 tuổi: Pre-A1 Level (Little Explorers)
            return """
        📊 STUDENT AGE: 6-8 YEARS OLD (Pre-A1 / Little Explorers Level)
        
        🎯 COGNITIVE & LANGUAGE DEVELOPMENT:
        - Attention span: 5-10 minutes
        - Concrete thinking, need visual/physical examples
        - Learning through play, songs, and repetition
        - Beginning literacy in native language
        
        📚 VOCABULARY & TOPICS:
        - Range: 100-300 common words
        - Topics: family, animals, colors, numbers 1-20, toys, food, body parts, classroom objects
        - Use only high-frequency everyday words
        - Examples: cat, dog, red, blue, apple, mom, dad, one, two, happy, sad
        
        ✍️ GRAMMAR & STRUCTURES:
        - Present simple: "I am happy", "This is a cat"
        - Have got: "I have got a toy"
        - Basic plurals: cat → cats
        - Simple questions: "What is this?", "How old are you?"
        - Imperatives: "Stand up", "Sit down"
        - DO NOT use: past tense, future tense, continuous forms, conditionals
        
        📝 SENTENCE COMPLEXITY:
        - Length: 3-5 words maximum
        - Structure: Subject + Verb + Object/Complement
        - Examples: "I like apples.", "This is my dog.", "She is happy."
        - Avoid complex or embedded clauses
        
        🎨 CONTENT REQUIREMENTS:
        - Very short texts (20-50 words for reading)
        - Use simple, clear images or context
        - Lots of repetition and patterns
        - Fun, engaging, game-like activities
        - Clear instructions with visual cues
        
        ❌ AVOID:
        - Abstract concepts
        - Long sentences or paragraphs
        - Complex grammar or vocabulary
        - Topics outside daily life experience
        """;

        } else if (age >= 9 && age <= 10) {
            // 9-10 tuổi: A1 Level (Starters)
            return """
        📊 STUDENT AGE: 9-10 YEARS OLD (A1 / Cambridge Starters Level)
        
        🎯 COGNITIVE & LANGUAGE DEVELOPMENT:
        - Attention span: 10-15 minutes
        - Developing abstract thinking
        - Can follow simple multi-step instructions
        - Improving reading and writing skills
        
        📚 VOCABULARY & TOPICS:
        - Range: 300-500 words
        - Topics: school, home, hobbies, weather, clothes, sports, daily routines
        - Common adjectives: big, small, new, old, fast, slow
        - Basic prepositions: in, on, under, next to
        - Examples: pencil, notebook, sunny, rainy, shirt, pants, football, breakfast
        
        ✍️ GRAMMAR & STRUCTURES:
        - Present simple: "I go to school every day"
        - Present continuous: "She is playing now"
        - Can/can't: "I can swim"
        - There is/are: "There is a book on the table"
        - Possessive adjectives: my, your, his, her
        - Simple past (be/have/go only): "I was happy", "She had a toy"
        - DO NOT use: perfect tenses, passive voice, complex conditionals
        
        📝 SENTENCE COMPLEXITY:
        - Length: 5-8 words
        - Can use simple conjunctions: and, but
        - Examples: "I like apples and oranges.", "My brother plays football, but I like swimming."
        
        🎨 CONTENT REQUIREMENTS:
        - Short texts (50-100 words for reading)
        - Clear context and simple storylines
        - Familiar, concrete situations
        - Picture support helpful
        - Mix of recognition and production tasks
        
        ❌ AVOID:
        - Idioms and phrasal verbs
        - Complex time expressions
        - Formal or academic language
        """;

        } else if (age >= 11 && age <= 12) {
            // 11-12 tuổi: A1-A2 Level (Movers/Flyers)
            return """
        📊 STUDENT AGE: 11-12 YEARS OLD (A1-A2 / Cambridge Movers-Flyers Level)
        
        🎯 COGNITIVE & LANGUAGE DEVELOPMENT:
        - Attention span: 15-20 minutes
        - Can think abstractly and hypothetically
        - Developing critical thinking skills
        - Can self-correct and monitor language use
        
        📚 VOCABULARY & TOPICS:
        - Range: 600-1000 words
        - Topics: travel, technology, environment, health, friendship, school subjects
        - Descriptive adjectives: beautiful, expensive, dangerous, important
        - Common phrasal verbs: get up, turn on, take off
        - Connectors: because, so, when, before, after
        
        ✍️ GRAMMAR & STRUCTURES:
        - All present tenses: simple, continuous, perfect (basic)
        - Past simple: regular and common irregular verbs
        - Future: will, going to
        - Comparatives and superlatives
        - Basic modals: must, should, could, might
        - Some/any, much/many, a lot of
        - DO NOT use: passive voice extensively, complex conditionals (2nd/3rd)
        
        📝 SENTENCE COMPLEXITY:
        - Length: 8-12 words
        - Can use multiple clauses with connectors
        - Examples: "I went to the park because it was sunny.", "If it rains tomorrow, we will stay at home."
        
        🎨 CONTENT REQUIREMENTS:
        - Medium texts (100-200 words for reading)
        - Simple narratives and descriptions
        - Personal experiences and opinions
        - Can follow short dialogues
        - Tasks require basic inference
        
        ❌ AVOID:
        - Advanced idioms
        - Complex academic vocabulary
        - Highly formal or literary language
        """;

        } else if (age >= 13 && age <= 14) {
            // 13-14 tuổi: A2-B1 Level (KET/PET)
            return """
        📊 STUDENT AGE: 13-14 YEARS OLD (A2-B1 / KET-PET Level)
        
        🎯 COGNITIVE & LANGUAGE DEVELOPMENT:
        - Attention span: 20-30 minutes
        - Abstract thinking well developed
        - Can analyze, synthesize, and evaluate
        - Developing personal opinions and arguments
        - Can monitor and self-correct effectively
        
        📚 VOCABULARY & TOPICS:
        - Range: 1500-2500 words
        - Topics: education, careers, social issues, culture, media, science (basic)
        - Academic vocabulary: analyze, describe, explain, compare
        - Phrasal verbs: look after, find out, give up, carry on
        - Collocations: make a decision, take an exam, do homework
        
        ✍️ GRAMMAR & STRUCTURES:
        - All tenses including perfect continuous
        - Passive voice: present and past simple
        - First and second conditionals
        - Reported speech (basic)
        - Relative clauses: who, which, that
        - Modals for deduction: must be, might be, can't be
        - Used to, be/get used to
        
        📝 SENTENCE COMPLEXITY:
        - Length: 12-18 words
        - Multiple clauses and complex sentences
        - Linking words: although, however, therefore, in addition
        - Examples: "Although it was raining heavily, we decided to go to the beach because we had already made plans."
        
        🎨 CONTENT REQUIREMENTS:
        - Longer texts (200-350 words for reading)
        - Can follow arguments and explanations
        - Express and justify opinions
        - Understand main ideas and specific details
        - Tasks require inference and interpretation
        
        ❌ AVOID:
        - Highly specialized technical vocabulary
        - Complex literary devices
        - Very advanced idiomatic expressions
        """;

        } else if (age >= 15 && age <= 16) {
            // 15-16 tuổi: B1-B2 Level (PET/FCE)
            return """
        📊 STUDENT AGE: 15-16 YEARS OLD (B1-B2 / PET-FCE Level)
        
        🎯 COGNITIVE & LANGUAGE DEVELOPMENT:
        - Attention span: 30-45 minutes
        - Fully developed abstract and critical thinking
        - Can engage in complex discussions and debates
        - Developing academic skills and exam techniques
        - Can produce well-structured extended texts
        
        📚 VOCABULARY & TOPICS:
        - Range: 2500-4000 words
        - Topics: global issues, economics, politics, psychology, literature, advanced science
        - Academic vocabulary: investigate, demonstrate, hypothesis, significant, crucial
        - Advanced phrasal verbs: come across, put up with, run out of
        - Idiomatic expressions: piece of cake, hit the nail on the head
        - Formal and informal register distinction
        
        ✍️ GRAMMAR & STRUCTURES:
        - All tenses including future perfect
        - Passive voice: all forms
        - All conditionals including third conditional and mixed
        - Advanced modals: ought to, would rather, had better
        - Reported speech: all forms including questions and commands
        - Wish/if only structures
        - Inversion for emphasis
        
        📝 SENTENCE COMPLEXITY:
        - Length: 15-25 words
        - Complex and compound-complex sentences
        - Advanced linking: despite, whereas, nevertheless, consequently
        - Examples: "Despite having studied for weeks, she found the exam challenging, particularly the section on grammar, which required not only knowledge but also quick thinking."
        
        🎨 CONTENT REQUIREMENTS:
        - Extended texts (350-600 words for reading)
        - Complex arguments and abstract ideas
        - Multiple perspectives and nuances
        - Inference, implication, and author's attitude
        - Sophisticated task types
        - Can understand implicit meaning
        
        ❌ AVOID:
        - Extremely specialized jargon
        - Archaic or very literary language (unless teaching literature)
        """;

        } else if (age >= 17 && age <= 18) {
            // 17-18 tuổi: B2-C1 Level (FCE/CAE)
            return """
        📊 STUDENT AGE: 17-18 YEARS OLD (B2-C1 / FCE-CAE Level)
        
        🎯 COGNITIVE & LANGUAGE DEVELOPMENT:
        - Attention span: 45-60 minutes+
        - Mature critical and analytical thinking
        - Can handle university-level academic content
        - Sophisticated argumentation and reasoning skills
        - Near-native discourse management
        
        📚 VOCABULARY & TOPICS:
        - Range: 4000-6000+ words
        - Topics: any academic or professional topic, complex social issues, philosophy, advanced sciences
        - Advanced academic vocabulary: methodology, paradigm, correlation, implicit
        - Sophisticated collocations: reach a consensus, pose a threat, exert influence
        - Full range of idioms and expressions
        - Nuanced vocabulary: distinctions between similar words
        
        ✍️ GRAMMAR & STRUCTURES:
        - All grammar structures including advanced/rare forms
        - Complex passive constructions
        - Advanced participle clauses
        - Cleft sentences for emphasis
        - Subjunctive mood
        - Advanced modal combinations
        - Sophisticated discourse markers
        
        📝 SENTENCE COMPLEXITY:
        - Length: 20-30+ words
        - Highly complex sentence structures
        - Sophisticated cohesion and coherence
        - Examples: "Having extensively researched the implications of climate change on marine ecosystems, scientists have concluded that, unless immediate action is taken, irreversible damage will occur, potentially affecting not only biodiversity but also human livelihoods."
        
        🎨 CONTENT REQUIREMENTS:
        - Long, complex texts (600-1000+ words)
        - Abstract and theoretical concepts
        - Subtle distinctions and implications
        - Evaluation of complex arguments
        - Understanding of text organization and purpose
        - Can appreciate stylistic devices
        
        ✅ CAN INCLUDE:
        - Academic writing conventions
        - Critical analysis and evaluation
        - Complex rhetorical devices
        - Sophisticated register management
        """;

        }else {
            return getAgeBasedLevelInstructions(12);
        }
    }


    private String buildBatchGVQuestionPrompt(
            ChallengeContext context,
            String questionType,
            String userDescription,
            String contextInfo,
            int numberOfQuestions,
            Integer age) {

        StringBuilder prompt = new StringBuilder();

        // ====================== THÊM SYSTEM ROLE MỚI ======================
        prompt.append("You are an experienced English teacher working at a reputable English language center for students aged 6 to 18.\n");

        String ageInstructions = getAgeBasedLevelInstructions(age);
        prompt.append(ageInstructions).append("\n\n");

        String studentLevel = context.studentLevel;
        prompt.append("📌 CURRENT STUDENT: Age ").append(age != null ? age : "not specified")
                .append(" | Level: ").append(studentLevel).append("\n\n");

        prompt.append("You are responsible for creating professional, age-appropriate, lesson-aligned English test questions for different proficiency levels (Little Explorers → Advanced).\n\n");
        prompt.append("Always analyze the lesson content and chapter topic carefully before writing questions.\n");
        prompt.append("Your questions must directly test the grammar, vocabulary, and language skills actually taught in the current lesson, not random English knowledge.\n\n");
        prompt.append("If no lesson content or chapter name is provided, you must ignore it — do not create unrelated questions.\n\n");
        prompt.append("Each question must:\n");
        prompt.append("- Match the student’s level (for example, \"Little Explorers\" = young learners beginner level).\n");
        prompt.append("- Be written in natural, clear, age-appropriate English.\n");
        prompt.append("- Have plausible distractors and one clear correct answer.\n");
        prompt.append("- Follow the Vietnamese National High School (THPT Quốc Gia) style for clarity and fairness.\n");
        prompt.append("- When generating drag-and-drop questions, strictly follow the JSON format and placeholder rules provided by the user.\n\n");
        prompt.append("Create PROFESSIONAL, ACADEMIC-STANDARD questions that test real English proficiency.\n\n");

        prompt.append("EXAM STANDARDS - VIETNAMESE NATIONAL HIGH SCHOOL EXAM FORMAT:\n");
        prompt.append("- Questions MUST be clear, unambiguous, and professionally written\n");
        prompt.append("- Test REAL language skills, not trick questions or rote memorization\n");
        prompt.append("- Use NATURAL, AUTHENTIC English that native speakers would use\n");
        prompt.append("- Distractors must be PLAUSIBLE but clearly distinguishable by competent students\n");
        prompt.append("- Each question should have a clear linguistic focus (grammar point, vocabulary, collocation)\n");
        prompt.append("- Progressive difficulty: start easier, gradually increase complexity\n");
        prompt.append("- Contextual questions preferred over isolated grammar drills\n\n");

        // Lesson content
        prompt.append("LESSON CONTENT (Extract key teaching points from this):\n");
        prompt.append(context.classLessonContent).append("\n\n");
        prompt.append(context.classChapterName).append("\n\n");

        prompt.append("LESSON ALIGNMENT RULES:\n");
        prompt.append("- Extract the MAIN grammar/vocabulary points being taught in the lesson\n");
        prompt.append("- Create questions that TEST UNDERSTANDING of these points\n");
        prompt.append("- Use vocabulary and topics from the lesson, but in NEW contexts/situations\n");
        prompt.append("- Questions should feel like authentic use of the language, not just lesson repetition\n");
        prompt.append("- Example: If lesson teaches 'present perfect', create questions that require students to\n");
        prompt.append("  distinguish between present perfect and other tenses in realistic contexts\n\n");

        // User requirements
        if (userDescription != null && !userDescription.isBlank()) {
            prompt.append("ADDITIONAL REQUIREMENTS:\n");
            prompt.append(userDescription).append("\n");
            prompt.append("Apply these requirements while maintaining exam-standard quality.\n\n");
        }

        prompt.append("TASK:\n");
        prompt.append("Generate EXACTLY ").append(numberOfQuestions).append(" HIGH-QUALITY ").append(questionType).append(" questions\n");
        prompt.append("Level: ").append(studentLevel).append("\n\n");

        // Special instructions based on question type
        if ("MULTIPLE_CHOICE".equals(questionType)) {
            prompt.append("MULTIPLE CHOICE - THPT QG STANDARD:\n");
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
            prompt.append("FILL IN THE BLANK - THPT QG STANDARD:\n");
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
            prompt.append("REARRANGE - THPT QG STANDARD:\n");
            prompt.append("CRITICAL: Create COMPLETE, MEANINGFUL sentences\n\n");
            prompt.append("REQUIREMENTS:\n");
            prompt.append("Must be a FULL sentence with complete meaning\n");
            prompt.append("Include subject + verb + complete thought\n");
            prompt.append("Use 5-8 words/phrases for optimal challenge\n");
            prompt.append("When arranged correctly = grammatically perfect sentence\n\n");
            prompt.append("EXAMPLES OF COMPLETE SENTENCES:\n");
            prompt.append("\"She has been studying English recently\" (6 words)\n");
            prompt.append("\"My brother plays football every weekend\" (5 words)\n");
            prompt.append("\"The teacher explained the lesson very clearly\" (6 words)\n");
            prompt.append("\"If I had known, I would have helped\" (7 words)\n\n");
            prompt.append("AVOID INCOMPLETE SENTENCES:\n");
            prompt.append("\"The teacher the explains\" (missing object)\n");
            prompt.append("\"Students are learning new\" (incomplete thought)\n");
            prompt.append("\"Because he was late\" (fragment, not complete)\n");
            prompt.append("\"She is\" (too short, incomplete)\n\n");
            prompt.append("Before finalizing: Ask yourself \"Is this a complete sentence I could say in conversation?\"\n");
            prompt.append("If NO, add missing words to make it complete!\n\n");
        }

        prompt.append("QUALITY CHECKLIST (VERIFY EACH QUESTION):\n");
        prompt.append("Clear and unambiguous question stem\n");
        prompt.append("Tests a specific language point from the lesson\n");
        prompt.append("Uses natural, authentic English\n");
        prompt.append("One clearly correct answer\n");
        prompt.append("Plausible distractors (for multiple choice)\n");
        prompt.append("Appropriate difficulty for level: ").append(studentLevel).append("\n");
        prompt.append("No cultural bias or obscure references\n");
        prompt.append("Professional formatting and language\n\n");

        appendJSONFormat(prompt, questionType);
        appendQuestionTypeRules(prompt, questionType);

        prompt.append("\nAVOID THESE COMMON MISTAKES:\n");
        prompt.append("Questions with multiple correct answers\n");
        prompt.append("Obviously wrong distractors (e.g., wrong word class)\n");
        prompt.append("Unnatural or awkward English\n");
        prompt.append("Questions testing obscure vocabulary not in lesson\n");
        prompt.append("Trick questions designed to confuse rather than test understanding\n");
        prompt.append("Copying exact sentences from lesson without adaptation\n");
        prompt.append("Grammar exercises without context\n\n");

        prompt.append("CRITICAL REQUIREMENTS:\n");
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
            prompt.append("11. FOR REARRANGE: VERIFY SENTENCE IS COMPLETE!\n");
            prompt.append("    - Count words: minimum 5, optimal 5-8\n");
            prompt.append("    - Check: Has subject? Has verb? Complete thought?\n");
            prompt.append("    - Read aloud: Does it make complete sense?\n");
            prompt.append("    - Example COMPLETE: \"She has been studying English recently\" \n");
            prompt.append("    - Example INCOMPLETE: \"The teacher the explains\" \n");
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
            String dailyChallengeType,
            Integer age) {

        StringBuilder prompt = new StringBuilder();

        // ====================== THÊM SYSTEM ROLE MỚI ======================
        prompt.append("You are an experienced English teacher working at a reputable English language center for students aged 6 to 18.\n");

        String ageInstructions = getAgeBasedLevelInstructions(age);
        prompt.append(ageInstructions).append("\n\n");

        String studentLevel = context.studentLevel;
        prompt.append("📌 CURRENT STUDENT: Age ").append(age != null ? age : "not specified")
                .append(" | Level: ").append(studentLevel).append("\n\n");

        prompt.append("You are responsible for creating professional, age-appropriate, lesson-aligned English test questions for different proficiency levels (Little Explorers → Advanced).\n\n");
        prompt.append("Always analyze the lesson content and chapter topic carefully before writing questions.\n");
        prompt.append("Your questions must directly test the grammar, vocabulary, and language skills actually taught in the current lesson, not random English knowledge.\n\n");
        prompt.append("If no lesson content or chapter name is provided, you must ignore it — do not create unrelated questions.\n\n");
        prompt.append("Each question must:\n");
        prompt.append("- Match the student’s level (for example, \"Little Explorers\" = young learners beginner level).\n");
        prompt.append("- Be written in natural, clear, age-appropriate English.\n");
        prompt.append("- Have plausible distractors and one clear correct answer.\n");
        prompt.append("- Follow the Vietnamese National High School (THPT Quốc Gia) style for clarity and fairness.\n");
        prompt.append("- When generating drag-and-drop questions, strictly follow the JSON format and placeholder rules provided by the user.\n\n");
        prompt.append("Create PROFESSIONAL reading comprehension questions that test genuine understanding.\n\n");

        prompt.append("THPT QG READING COMPREHENSION STANDARDS:\n");
        prompt.append("- Questions test DIFFERENT comprehension skills (main idea, detail, inference, vocabulary)\n");
        prompt.append("- Each question has ONE clearly correct answer based on the passage\n");
        prompt.append("- Distractors are plausible but definitively wrong\n");
        prompt.append("- Questions require CAREFUL reading, not just keyword matching\n");
        prompt.append("- Test understanding at various levels: literal, inferential, evaluative\n");
        prompt.append("- Progressive difficulty from easier to more challenging\n");
        prompt.append("- Professional, academic language\n\n");

        prompt.append("CHALLENGE TYPE: ").append(dailyChallengeType).append("\n");
        appendDCTypeInstructions(prompt, dailyChallengeType);

        prompt.append("PASSAGE TO CREATE QUESTIONS FROM:\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        prompt.append(section.getSectionsContent()).append("\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // User requirements
        if (userDescription != null && !userDescription.isBlank()) {
            prompt.append("ADDITIONAL REQUIREMENTS:\n");
            prompt.append(userDescription).append("\n");
            prompt.append("Apply while maintaining THPT QG standards.\n\n");
        }

        prompt.append("TASK:\n");
        prompt.append("Generate EXACTLY ").append(numberOfQuestions).append(" ").append(questionType).append(" questions about the passage\n");
        prompt.append("Level: ").append(studentLevel).append("\n\n");

        prompt.append("QUESTION TYPE DISTRIBUTION (Follow this breakdown):\n");
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
            prompt.append("READING COMPREHENSION MC - THPT QG FORMAT:\n\n");
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
            prompt.append("Use information from passage but in wrong context\n");
            prompt.append("Use extreme language (always, never) for false options\n");
            prompt.append("Use partial truths (statement is partly true but incomplete)\n");
            prompt.append("Reference content from different paragraph/section\n");
            prompt.append("Make plausible if reader didn't read carefully\n\n");
        } else if ("REARRANGE".equals(questionType)) {
            prompt.append("REARRANGE - READING-BASED:\n");
            prompt.append("CRITICAL: Create COMPLETE, MEANINGFUL sentences from passage vocabulary\n\n");
            prompt.append("REQUIREMENTS:\n");
            prompt.append("Must be a FULL sentence with complete meaning\n");
            prompt.append("Use vocabulary and structures from the passage\n");
            prompt.append("Include subject + verb + complete thought\n");
            prompt.append("Use 5-8 words/phrases for optimal challenge\n");
            prompt.append("Test understanding of passage content through sentence construction\n\n");
            prompt.append("EXAMPLES:\n");
            prompt.append("\"The author argues that climate change is urgent\" (7 words)\n");
            prompt.append("\"Scientists have discovered a new treatment method\" (6 words)\n");
            prompt.append("\"Many students prefer online learning nowadays\" (5 words)\n\n");
            prompt.append("AVOID:\n");
            prompt.append("\"The passage the mentions\" (incomplete)\n");
            prompt.append("\"According to author\" (missing verb and object)\n");
            prompt.append("\"Was very important\" (missing subject)\n\n");
        }

        prompt.append("CRITICAL RULES FOR PASSAGE-BASED QUESTIONS:\n");
        prompt.append("1. ALL answers must be FINDABLE in the passage\n");
        prompt.append("2. Do NOT require outside knowledge not in the passage\n");
        prompt.append("3. Quote or paraphrase from passage when appropriate\n");
        prompt.append("4. Each question tests DIFFERENT part or aspect of passage\n");
        prompt.append("5. Cover different paragraphs/sections of the passage\n");
        prompt.append("6. Avoid questions with answers in first paragraph only\n");
        prompt.append("7. Questions should encourage full reading, not just skimming\n");
        prompt.append("8. Use passage vocabulary in questions naturally\n\n");

        prompt.append("QUALITY CHECKLIST:\n");
        prompt.append("Question clearly worded and unambiguous\n");
        prompt.append("Answer is definitively in the passage\n");
        prompt.append("Three plausible but wrong distractors\n");
        prompt.append("Tests comprehension, not memory tricks\n");
        prompt.append("Appropriate difficulty for level: ").append(studentLevel).append("\n");
        prompt.append("Professional, academic language\n");
        prompt.append("Different question type (main idea/detail/inference/vocab)\n\n");

        appendJSONFormat(prompt, questionType);
        appendQuestionTypeRules(prompt, questionType);

        prompt.append("\nAVOID:\n");
        prompt.append("Questions answerable without reading passage\n");
        prompt.append("Questions requiring outside knowledge\n");
        prompt.append("Multiple questions about the same detail\n");
        prompt.append("Answers based on common sense rather than passage\n");
        prompt.append("Trick questions with multiple valid interpretations\n");
        prompt.append("Copying exact phrases from passage in distractors\n\n");

        prompt.append("CRITICAL REQUIREMENTS:\n");
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
            prompt.append("11. FOR REARRANGE: VERIFY SENTENCE IS COMPLETE!\n");
            prompt.append("    - Minimum 5 words, optimal 5-8 words\n");
            prompt.append("    - Must have: subject + verb + complete meaning\n");
            prompt.append("    - Test yourself: \"Can this stand alone as a sentence?\"\n");
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

                    // Clean the response
                    content = cleanJsonResponse(content);

                    log.debug("OpenAI response (cleaned, first 1000 chars): {}", content.length() > 1000 ? content.substring(0, 1000) : content);
                    return content;
                }
            }
        } catch (Exception e) {
            log.error("Error calling OpenAI: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call OpenAI: " + e.getMessage(), e);
        }

        throw new RuntimeException("No response from OpenAI");
    }

    /**
     * Robust JSON cleaning:
     * - Strip code fences and common assistant commentary
     * - Remove JavaScript-style comments
     * - Remove trailing commas
     * - Try to fix common quote problems
     */
    private String cleanJsonResponse(String content) {
        if (content == null) return "";

        String s = content.trim();

        // Remove code fences
        s = s.replaceAll("(?s)^```(?:json)?\\s*", "");
        s = s.replaceAll("(?s)\\s*```\\s*$", "");

        // Remove leading / trailing assistant annotations like "Here's the JSON:"
        s = s.replaceFirst("(?i)^\\s*here('?s)?\\s*the\\s*json[:\\s]*", "");
        s = s.replaceFirst("(?i)^\\s*response[:\\s]*", "");

        // Strip single-line and multi-line comments
        s = s.replaceAll("(?m)//.*?$", "");
        s = s.replaceAll("(?s)/\\*.*?\\*/", "");

        // Replace common trailing commas
        s = s.replaceAll(",\\s*}", "}");
        s = s.replaceAll(",\\s*\\]", "]");

        // Normalize smart quotes to straight quotes
        s = s.replace("“", "\"").replace("”", "\"").replace("‘", "'").replace("’", "'");

        // If string starts and ends with a quoted JSON string (e.g., wrapped JSON), try to unquote
        if ((s.startsWith("\"{") && s.endsWith("}\"")) || (s.startsWith("'{" ) && s.endsWith("}'"))) {
            s = s.substring(1, s.length() - 1).replace("\\\"", "\"");
        }

        return s.trim();
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
                context.studentLevel,
                request.getAge()
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
            String level,
            Integer age) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert English teacher creating reading passages.\n\n");

        String ageInstructions = getAgeBasedLevelInstructions(age);
        prompt.append(ageInstructions).append("\n\n");

        prompt.append("📌 CURRENT STUDENT: Age ").append(age != null ? age : "not specified")
                .append(" | Level: ").append(level).append("\n\n");

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
        prompt.append("   - Example: \"1. Question? A. option1 B. option2 ✓ C. option3\"\n\n");

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

    // Thêm vào OpenAiServiceImpl.java

    @Override
    @Transactional(readOnly = true)
    public GradingWritingResponse gradeWriting(GradingWritingRequest request) {
        log.info("Starting AI grading for submissionQuestionId: {}", request.getSubmissionQuestionId());

        // 1. Load submission question
        SubmissionQuestion submissionQuestion = submissionQuestionRepository
                .findById(request.getSubmissionQuestionId())
                .orElseThrow(() -> new ApiException("Submission question not found", HttpStatus.NOT_FOUND.value()));

        // 2. Extract student's writing from submission_content_json
        String studentWriting = extractWritingFromSubmission(submissionQuestion.getSubmissionContentJson());

        if (studentWriting == null || studentWriting.trim().isEmpty()) {
            throw new ApiException("No writing content found in submission", HttpStatus.BAD_REQUEST.value());
        }

        // 3. Load question and challenge context
        Question question = submissionQuestion.getQuestion();
        ChallengeSection section = question.getSection();
        DailyChallenge challenge = section.getChallenge();

        // Eager load context
        ChallengeContext context = eagerLoadChallengeContext(challenge);

        // 4. Build grading prompt
        String prompt = buildWritingGradingPrompt(
                context,
                question.getQuestionText(),
                studentWriting
        );

        // 5. Call OpenAI
        String aiResponse = callOpenAI(prompt);

        // 6. Parse response (validate comments against actual student text)
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
            ChallengeContext context,
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

        prompt.append("Context: Chapter: ").append(context.classChapterName)
                .append(" | Level: ").append(context.studentLevel).append("\n\n");

        prompt.append("TASK: Read the writing below and produce a JSON object containing:\n");
        prompt.append(" - overallFeedback: 100-200 words in Vietnamese summarizing strengths, key weaknesses, and a 2-3 step study plan.\n");
        prompt.append(" - suggestedScore: numeric (0.0 - 10.0).\n");
        prompt.append(" - comments: 7-12 items, prioritized by impact on communication. Each comment must include:\n");
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

        prompt.append("OUTPUT (exact JSON only, no extra text). Note: ALL textual values must be in Vietnamese:\n");
        prompt.append("{\n");
        prompt.append("  \"overallFeedback\": \"Tóm tắt ngắn gọn bằng tiếng Việt: ...\",\n");
        prompt.append("  \"suggestedScore\": 7.5,\n");
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


    // Improved parsing: validate indices, filter trivial comments, prioritize by severity, keep 7-12 best
    private GradingWritingResponse parseGradingResponse(String jsonResponse, String studentWriting) {
        try {
            String cleaned = cleanJsonResponse(jsonResponse);
            JsonNode root = objectMapper.readTree(cleaned);

            String overallFeedback = root.hasNonNull("overallFeedback") ? root.get("overallFeedback").asText().trim() : "";
            double suggestedScore = 0.0;
            if (root.hasNonNull("suggestedScore")) {
                suggestedScore = root.get("suggestedScore").asDouble(0.0);
            }
            // clamp
            if (Double.isNaN(suggestedScore) || suggestedScore < 0) suggestedScore = 0.0;
            if (suggestedScore > 10) suggestedScore = 10.0;

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

                        String severity = commentNode.hasNonNull("severity") ? commentNode.get("severity").asText().toLowerCase() : "suggestion";
                        String category = commentNode.hasNonNull("category") ? commentNode.get("category").asText().toLowerCase() : "other";
                        String correction = commentNode.hasNonNull("correction") ? commentNode.get("correction").asText() : "";

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

                        // attach additional info via comment string if model didn't provide fields (keeps compatibility)
                        comments.add(wc);

                    } catch (Exception ex) {
                        log.debug("Skipping malformed comment node: {}", ex.getMessage());
                    }
                }
            }

            // Prioritize comments: we don't have explicit severity stored on WritingComment, but we keep order returned by AI.
            // Keep between 7 and 12 comments, prefer earlier ones (AI asked to prioritize)
            int minKeep = 7;
            int maxKeep = 12;
            if (comments.size() < minKeep) {
                // if AI returned fewer, keep all
            } else if (comments.size() > maxKeep) {
                comments = comments.subList(0, maxKeep);
            }

            return GradingWritingResponse.builder()
                    .overallFeedback(overallFeedback)
                    .suggestedScore(suggestedScore)
                    .comments(comments)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse grading response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse AI grading response: " + e.getMessage(), e);
        }
    }

    @Override
    public PronunciationAssessmentResponse assessPronunciation(PronunciationAssessmentRequest request) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Starting pronunciation assessment for file: {}",
                traceId, request.getAudioFile().getOriginalFilename());

        try {
            // 1. Validate audio file
            validateAudioFile(request.getAudioFile());

            // 2. Save audio file temporarily
            File tempAudioFile = saveTempAudioFile(request.getAudioFile());

            try {
                // 3. Create Speech SDK configuration
                SpeechConfig speechConfig = SpeechConfig.fromSubscription(speechKey, speechRegion);
                speechConfig.setSpeechRecognitionLanguage("en-US");

                // 4. Create audio config from file
                AudioConfig audioConfig = AudioConfig.fromWavFileInput(tempAudioFile.getAbsolutePath());

                // 5. Create pronunciation assessment config
                PronunciationAssessmentConfig pronConfig = new PronunciationAssessmentConfig(
                        request.getReferenceText(),
                        mapGradingSystem(request.getGradingSystem()),
                        mapGranularity(request.getGranularity()),
                        request.getEnableMiscue()
                );

                if (request.getEnableProsody()) {
                    pronConfig.enableProsodyAssessment();
                }

                // 6. Create recognizer
                SpeechRecognizer recognizer = new SpeechRecognizer(speechConfig, audioConfig);
                pronConfig.applyTo(recognizer);

                // 7. Perform recognition
                SpeechRecognitionResult result = recognizer.recognizeOnceAsync().get();

                // 8. Process results
                if (result.getReason() == ResultReason.RecognizedSpeech) {
                    PronunciationAssessmentResponse response = processRecognitionResult(
                            result,
                            request.getReferenceText(),
                            request.getEnableMiscue()
                    );

                    log.info("[{}] Pronunciation assessment completed. Score: {}",
                            traceId, response.getPronunciationScore());

                    return response;

                } else if (result.getReason() == ResultReason.NoMatch) {
                    log.error("[{}] No speech could be recognized", traceId);
                    throw new ApiException("No speech could be recognized from the audio file",
                            HttpStatus.BAD_REQUEST.value());

                } else if (result.getReason() == ResultReason.Canceled) {
                    CancellationDetails cancellation = CancellationDetails.fromResult(result);
                    log.error("[{}] Speech recognition canceled. Reason: {}, Error: {}",
                            traceId, cancellation.getReason(), cancellation.getErrorDetails());
                    throw new ApiException("Speech recognition failed: " + cancellation.getErrorDetails(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value());
                }

                throw new ApiException("Unexpected recognition result",
                        HttpStatus.INTERNAL_SERVER_ERROR.value());

            } finally {
                // Cleanup temp file
                if (tempAudioFile.exists()) {
                    tempAudioFile.delete();
                }
            }

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] Pronunciation assessment failed: {}", traceId, e.getMessage(), e);
            throw new ApiException("Failed to assess pronunciation: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * Validate audio file format and size
     */
    private void validateAudioFile(MultipartFile audioFile) {
        // Check file size (max 10MB)
        long maxSize = 50 * 1024 * 1024; // 10MB
        if (audioFile.getSize() > maxSize) {
            throw new ApiException("Audio file size exceeds 10MB limit", HttpStatus.BAD_REQUEST.value());
        }

        // Check file format (WAV only for Speech SDK)
        String filename = audioFile.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".wav")) {
            throw new ApiException("Only WAV audio format is supported", HttpStatus.BAD_REQUEST.value());
        }
    }

    /**
     * Save uploaded file to temp directory
     */
    private File saveTempAudioFile(MultipartFile audioFile) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        String filename = "pronunciation_" + UUID.randomUUID() + ".wav";
        File tempFile = new File(tempDir, filename);

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(audioFile.getBytes());
        }

        log.debug("Saved temp audio file: {}", tempFile.getAbsolutePath());
        return tempFile;
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
     * Process recognition result and extract scores
     */
    private PronunciationAssessmentResponse processRecognitionResult(
            SpeechRecognitionResult result,
            String referenceText,
            boolean enableMiscue) throws Exception {

        String recognizedText = result.getText();

        // Get pronunciation assessment result
        PronunciationAssessmentResult pronResult = PronunciationAssessmentResult.fromResult(result);

        // Extract overall scores
        double accuracyScore = pronResult.getAccuracyScore();
        double fluencyScore = pronResult.getFluencyScore();
        double completenessScore = pronResult.getCompletenessScore();
        double pronunciationScore = pronResult.getPronunciationScore();
        Double prosodyScore = null;

        try {
            prosodyScore = pronResult.getProsodyScore();
        } catch (Exception e) {
            // Prosody might not be available
            log.debug("Prosody score not available");
        }

        // Parse JSON for word-level details
        String jsonResult = result.getProperties().getProperty(PropertyId.SpeechServiceResponse_JsonResult);
        List<PronunciationAssessmentResponse.WordAssessment> wordAssessments =
                parseWordAssessments(jsonResult, enableMiscue, referenceText);

        // Generate feedback
        String feedback = generateFeedback(
                pronunciationScore,
                accuracyScore,
                fluencyScore,
                completenessScore,
                prosodyScore,
                wordAssessments
        );

        return PronunciationAssessmentResponse.builder()
                .pronunciationScore(pronunciationScore)
                .accuracyScore(accuracyScore)
                .fluencyScore(fluencyScore)
                .completenessScore(completenessScore)
                .prosodyScore(prosodyScore)
                .recognizedText(recognizedText)
                .referenceText(referenceText)
                .words(wordAssessments)
                .feedback(feedback)
                .build();
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
     * Generate Vietnamese feedback based on scores
     */
    private String generateFeedback(
            double pronunciationScore,
            double accuracyScore,
            double fluencyScore,
            double completenessScore,
            Double prosodyScore,
            List<PronunciationAssessmentResponse.WordAssessment> words) {

        StringBuilder feedback = new StringBuilder();

        // Overall assessment
        feedback.append("📊 **Đánh giá tổng quan:**\n");
        feedback.append(String.format("- Điểm phát âm tổng thể: **%.1f/100**\n", pronunciationScore));

        if (pronunciationScore >= 80) {
            feedback.append("✅ Xuất sắc! Phát âm của bạn rất tốt.\n\n");
        } else if (pronunciationScore >= 60) {
            feedback.append("👍 Tốt! Phát âm của bạn ở mức khá, cần cải thiện thêm một số điểm.\n\n");
        } else if (pronunciationScore >= 40) {
            feedback.append("📝 Trung bình. Bạn cần luyện tập thêm để cải thiện phát âm.\n\n");
        } else {
            feedback.append("💪 Cần cố gắng hơn. Hãy luyện tập thường xuyên để cải thiện phát âm.\n\n");
        }

        // Detailed scores
        feedback.append("📈 **Chi tiết điểm số:**\n");
        feedback.append(String.format("- Độ chính xác (Accuracy): %.1f/100\n", accuracyScore));
        feedback.append(String.format("- Độ trôi chảy (Fluency): %.1f/100\n", fluencyScore));
        feedback.append(String.format("- Độ hoàn chỉnh (Completeness): %.1f/100\n", completenessScore));
        if (prosodyScore != null) {
            feedback.append(String.format("- Ngữ điệu (Prosody): %.1f/100\n", prosodyScore));
        }
        feedback.append("\n");

        // Word-level errors
        long errorCount = words.stream()
                .filter(w -> !"None".equals(w.getErrorType()))
                .count();

        if (errorCount > 0) {
            feedback.append(String.format("⚠️ **Lỗi phát hiện được:** %d từ\n", errorCount));

            long mispronunciations = words.stream()
                    .filter(w -> "Mispronunciation".equals(w.getErrorType()))
                    .count();
            long omissions = words.stream()
                    .filter(w -> "Omission".equals(w.getErrorType()))
                    .count();
            long insertions = words.stream()
                    .filter(w -> "Insertion".equals(w.getErrorType()))
                    .count();

            if (mispronunciations > 0) {
                feedback.append(String.format("- Phát âm sai: %d từ\n", mispronunciations));
            }
            if (omissions > 0) {
                feedback.append(String.format("- Thiếu: %d từ\n", omissions));
            }
            if (insertions > 0) {
                feedback.append(String.format("- Thừa: %d từ\n", insertions));
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

        return feedback.toString();
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

