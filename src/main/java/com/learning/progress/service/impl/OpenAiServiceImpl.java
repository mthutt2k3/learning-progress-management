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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
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
     * API 1: Generate GV questions - OPTIMIZED WITH PROPER GROUPING
     */
    @Override
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
                        challenge,
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

        String dailyChallengeType = challenge.getChallengeType().toString();
        log.info("Daily Challenge Type: {}", dailyChallengeType);

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
                                challenge,
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
     * Generate batch of GV questions - ALL SAME TYPE
     */
    private List<QuestionWithOrderDto> generateBatchOfGVQuestions(List<QuestionGenerationTask> batch) {
        try {
            // All tasks in batch have SAME question type (because we grouped first)
            QuestionGenerationTask firstTask = batch.get(0);
            log.info("Generating batch of {} {} questions", batch.size(), firstTask.questionType);

            String prompt = buildBatchGVQuestionPrompt(
                    firstTask.challenge,
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

            log.info("Successfully generated batch of {} questions", result.size());
            return result;

        } catch (Exception e) {
            log.error("Failed to generate batch: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Generate batch of content-based questions - ALL SAME TYPE
     */
    private List<QuestionWithOrderDto> generateBatchOfContentBasedQuestions(List<ContentBasedQuestionTask> batch) {
        try {
            ContentBasedQuestionTask firstTask = batch.get(0);
            log.info("Generating batch of {} {} questions", batch.size(), firstTask.questionType);

            String prompt = buildBatchContentBasedQuestionPrompt(
                    firstTask.challenge,
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

            log.info("Successfully generated batch of {} questions", result.size());
            return result;

        } catch (Exception e) {
            log.error("Failed to generate batch: {}", e.getMessage(), e);
            return Collections.emptyList();
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

    // Helper classes
    private static class QuestionGenerationTask {
        DailyChallenge challenge;
        String questionType;
        String userDescription;
        String contextInfo;
        int sectionOrder;

        QuestionGenerationTask(DailyChallenge challenge, String questionType,
                               String userDescription, String contextInfo, int sectionOrder) {
            this.challenge = challenge;
            this.questionType = questionType;
            this.userDescription = userDescription;
            this.contextInfo = contextInfo;
            this.sectionOrder = sectionOrder;
        }
    }

    private static class ContentBasedQuestionTask {
        DailyChallenge challenge;
        SectionDto section;
        String questionType;
        String userDescription;
        String contextInfo;
        String dailyChallengeType;
        int orderNumber;

        ContentBasedQuestionTask(DailyChallenge challenge, SectionDto section,
                                 String questionType, String userDescription,
                                 String contextInfo, String dailyChallengeType, int orderNumber) {
            this.challenge = challenge;
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
     * ✨ NEW: Determine student level from challenge and user description
     */
    private String determineStudentLevel(DailyChallenge challenge, String userDescription) {
        // Default level from database
        String systemLevel = challenge.getClassLesson() != null
                && challenge.getClassLesson().getClassChapter() != null
                && challenge.getClassLesson().getClassChapter().getClazz() != null
                && challenge.getClassLesson().getClassChapter().getClazz().getSyllabus() != null
                && challenge.getClassLesson().getClassChapter().getClazz().getSyllabus().getLevel() != null
                ? challenge.getClassLesson().getClassChapter().getClazz().getSyllabus().getLevel().getLevelName()
                : "Intermediate";

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
            DailyChallenge challenge,
            String questionType,
            String userDescription,
            String contextInfo,
            int numberOfQuestions) {

        StringBuilder prompt = new StringBuilder();

        String classLessonContent = challenge.getClassLesson() != null
                ? challenge.getClassLesson().getClassLessonContent()
                : "No lesson content available";

        String studentLevel = determineStudentLevel(challenge, userDescription);
        String levelInstructions = getLevelInstructions(studentLevel);

        prompt.append("You are an expert English teacher creating grammar/vocabulary exercises.\n\n");

        // ✨ Level instructions first
        prompt.append(levelInstructions).append("\n");

        // 🔥 User requirements with highest priority
        if (userDescription != null && !userDescription.isBlank()) {
            prompt.append("🔥 USER REQUIREMENTS (HIGHEST PRIORITY) 🔥\n");
            prompt.append(userDescription).append("\n");
            prompt.append("⚠️ CRITICAL: Follow user requirements exactly, but filter out:\n");
            prompt.append("- Content completely unrelated to the lesson theme\n");
            prompt.append("- Sensitive, inappropriate, or offensive content\n");
            prompt.append("- Requests that violate educational standards\n");
            prompt.append("If user requirements are reasonable and related, prioritize them over level defaults.\n\n");
        }

        prompt.append("📚 CONTEXT (Reference Only):\n");
        prompt.append("Lesson Content:\n");
        prompt.append(classLessonContent).append("\n");
        prompt.append(contextInfo).append("\n\n");

        prompt.append("🎯 CONTENT DIVERSITY REQUIREMENTS:\n");
        prompt.append("- Create VARIED and DIVERSE questions within the lesson theme\n");
        prompt.append("- Each question should explore DIFFERENT aspects, vocabulary, or grammar points\n");
        prompt.append("- Use different sentence structures, contexts, and situations\n");
        prompt.append("- Avoid repetitive patterns or similar examples\n");
        prompt.append("- Be creative while staying relevant to the lesson topic\n");
        prompt.append("- Questions can expand beyond exact lesson sentences, but must stay within the same theme/grammar/vocabulary domain\n\n");

        prompt.append("📝 TASK:\n");
        prompt.append("Generate EXACTLY ").append(numberOfQuestions).append(" DIFFERENT questions of type: ").append(questionType).append("\n");
        prompt.append("Question type: Grammar/Vocabulary (NONE resource type)\n");
        prompt.append("All questions MUST be based on the lesson content but with diverse variations.\n");
        prompt.append("Each question MUST be UNIQUE and explore different aspects.\n\n");

        appendJSONFormat(prompt, questionType);
        appendQuestionTypeRules(prompt, questionType);

        prompt.append("\n🚫 DUPLICATION PREVENTION:\n");
        prompt.append("- Each question must be COMPLETELY DIFFERENT from others\n");
        prompt.append("- Vary vocabulary, grammar structures, contexts, and sentence patterns\n");
        prompt.append("- Do NOT reuse similar ideas, wordings, or examples\n");
        prompt.append("- Ensure all ").append(numberOfQuestions).append(" questions are distinctly unique\n");
        prompt.append("- Maximize creativity and diversity while maintaining topic relevance\n\n");

        prompt.append("🔥 ABSOLUTE REQUIREMENTS:\n");
        prompt.append("1. Return ONLY valid JSON - no markdown, no explanations\n");
        prompt.append("2. Generate EXACTLY ").append(numberOfQuestions).append(" DIFFERENT questions\n");
        prompt.append("3. Question type: ").append(questionType).append("\n");
        prompt.append("4. For FILL_IN_THE_BLANK: Use natural format like \"I ...(be) a student.\" and [[pos_xxxxx]] placeholders\n");
        prompt.append("5. All required fields must be present and accurate\n");
        prompt.append("6. Questions MUST be relevant to lesson content but DIVERSE\n");
        prompt.append("7. Respect the student level: ").append(studentLevel).append("\n");
        prompt.append("8. Prioritize user requirements if provided, unless inappropriate\n");
        prompt.append("9. Each question explores a DIFFERENT aspect within the topic\n");

        return prompt.toString();
    }

    private String buildBatchContentBasedQuestionPrompt(
            DailyChallenge challenge,
            SectionDto section,
            String questionType,
            int numberOfQuestions,
            String userDescription,
            String contextInfo,
            String dailyChallengeType) {

        StringBuilder prompt = new StringBuilder();

        String classLessonContent = challenge.getClassLesson() != null
                ? challenge.getClassLesson().getClassLessonContent()
                : "No lesson content available";

        String studentLevel = determineStudentLevel(challenge, userDescription);
        String levelInstructions = getLevelInstructions(studentLevel);

        prompt.append("You are an expert English teacher creating comprehension exercises.\n\n");

        // ✨ Level instructions first
        prompt.append(levelInstructions).append("\n");

        // 🔥 User requirements with highest priority
        if (userDescription != null && !userDescription.isBlank()) {
            prompt.append("🔥 USER REQUIREMENTS (HIGHEST PRIORITY) 🔥\n");
            prompt.append(userDescription).append("\n");
            prompt.append("⚠️ CRITICAL: Follow user requirements exactly, but filter out:\n");
            prompt.append("- Content completely unrelated to the section content\n");
            prompt.append("- Sensitive, inappropriate, or offensive content\n");
            prompt.append("- Requests that violate educational standards\n");
            prompt.append("If user requirements are reasonable and related, prioritize them over level defaults.\n\n");
        }

        prompt.append("📚 CHALLENGE TYPE: ").append(dailyChallengeType).append("\n");
        appendDCTypeInstructions(prompt, dailyChallengeType);

        prompt.append("\n📖 CONTEXT (Reference Only):\n");
        prompt.append("Lesson Content:\n");
        prompt.append(classLessonContent).append("\n");
        prompt.append(contextInfo).append("\n\n");

        prompt.append("📄 SECTION CONTENT (Base ALL questions on this):\n");
        prompt.append(section.getSectionsContent()).append("\n\n");

        prompt.append("🎯 CONTENT DIVERSITY REQUIREMENTS:\n");
        prompt.append("- Create VARIED and DIVERSE questions from the section content\n");
        prompt.append("- Each question should focus on DIFFERENT parts or aspects of the content\n");
        prompt.append("- Test different comprehension skills: detail, main idea, inference, vocabulary\n");
        prompt.append("- Use different sentence structures and question formats\n");
        prompt.append("- Avoid asking similar questions about the same information\n");
        prompt.append("- Cover different paragraphs or sections of the content\n");
        prompt.append("- Questions can expand interpretation but must be answerable from the content\n\n");

        prompt.append("📝 TASK:\n");
        prompt.append("Generate EXACTLY ").append(numberOfQuestions).append(" DIFFERENT questions of type: ").append(questionType).append("\n");
        prompt.append("All questions MUST be based on the section content above.\n");
        prompt.append("Each question MUST be UNIQUE and test different aspects.\n\n");

        appendJSONFormat(prompt, questionType);
        appendQuestionTypeRules(prompt, questionType);

        prompt.append("\n🚫 DUPLICATION PREVENTION:\n");
        prompt.append("- Each question must test DIFFERENT information or aspects\n");
        prompt.append("- Vary the focus: some on details, some on main ideas, some on inference\n");
        prompt.append("- Do NOT ask multiple questions about the same sentence or idea\n");
        prompt.append("- Ensure all ").append(numberOfQuestions).append(" questions are distinctly unique\n");
        prompt.append("- Maximize diversity while ensuring all are answerable from content\n\n");

        prompt.append("🔥 ABSOLUTE REQUIREMENTS:\n");
        prompt.append("1. Return ONLY valid JSON - no markdown, no explanations\n");
        prompt.append("2. Generate EXACTLY ").append(numberOfQuestions).append(" DIFFERENT questions\n");
        prompt.append("3. ALL questions MUST be answerable ONLY by reading the section content\n");
        prompt.append("4. Question type: ").append(questionType).append("\n");
        prompt.append("5. For FILL_IN_THE_BLANK: Use natural format like \"I ...(be) a student.\" and [[pos_xxxxx]] placeholders\n");
        prompt.append("6. All required fields must be present and accurate\n");
        prompt.append("7. Respect the student level: ").append(studentLevel).append("\n");
        prompt.append("8. Prioritize user requirements if provided, unless inappropriate\n");
        prompt.append("9. Each question tests a DIFFERENT aspect of the content\n");
        prompt.append("10. Use lesson content as additional context for relevance\n");

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

            JsonNode rootNode = objectMapper.readTree(jsonResponse);
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
        prompt.append("📚 SPECIFIC RULES FOR ").append(questionType).append(":\n\n");

        switch (questionType) {

            case "MULTIPLE_CHOICE":
                prompt.append("- 4 options per question\n");
                prompt.append("- Exactly 1 option with isCorrect=true\n");
                prompt.append("- positionId=null for all options\n");
                prompt.append("- Make distractors plausible but clearly wrong\n");
                prompt.append("- Vary difficulty across questions\n");
                prompt.append("Example:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"She _____ to school every day.\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"MULTIPLE_CHOICE\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"opt1\", \"value\": \"go\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt2\", \"value\": \"goes\", \"isCorrect\": true, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt3\", \"value\": \"going\", \"isCorrect\": false, \"positionId\": null},\n")
                        .append("      {\"id\": \"opt4\", \"value\": \"went\", \"isCorrect\": false, \"positionId\": null}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n");
                break;

            case "TRUE_OR_FALSE":
                prompt.append("- 2 options: \"True\" and \"False\"\n");
                prompt.append("- Exactly 1 option with isCorrect=true\n");
                prompt.append("- positionId=null\n");
                prompt.append("- Statement should be clear and unambiguous\n");
                break;

            case "FILL_IN_THE_BLANK":
                prompt.append("⚠️ CRITICAL FORMAT - NATURAL ENGLISH STYLE:\n");
                prompt.append("- Use NATURAL fill-in-the-blank format like standard English textbooks\n");
                prompt.append("- Format: \"I ...(verb) a student.\" or \"She ...(be) happy.\"\n");
                prompt.append("- The hint in parentheses (like 'be', 'verb', 'adjective') guides the student\n");
                prompt.append("- Then use [[pos_xxxxxx]] placeholder: \"I [[pos_a7k3m2]](be) a student.\"\n");
                prompt.append("- xxxxxx is a random 6-character ID using lowercase a-z and 0-9\n");
                prompt.append("- Each positionId in data must match its corresponding xxxxxx\n");
                prompt.append("- Each blank has 1 correct answer (no distractors)\n");
                prompt.append("- The hint helps students know what type of word to fill\n\n");
                prompt.append("Example 1:\n");
                prompt.append("  questionText: \"I [[pos_a7k3m2]](be) a student.\"\n");
                prompt.append("  data: [{\"value\": \"am\", \"isCorrect\": true, \"positionId\": \"a7k3m2\"}]\n\n");
                prompt.append("Example 2:\n");
                prompt.append("  questionText: \"She [[pos_b8n4p1]](go) to school every day.\"\n");
                prompt.append("  data: [{\"value\": \"goes\", \"isCorrect\": true, \"positionId\": \"b8n4p1\"}]\n\n");
                prompt.append("Example 3 (multiple blanks):\n");
                prompt.append("  questionText: \"I [[pos_x1y2z3]](be) [[pos_a4b5c6]](study) English.\"\n");
                prompt.append("  data: [\n");
                prompt.append("    {\"value\": \"am\", \"isCorrect\": true, \"positionId\": \"x1y2z3\"},\n");
                prompt.append("    {\"value\": \"studying\", \"isCorrect\": true, \"positionId\": \"a4b5c6\"}\n");
                prompt.append("  ]\n");
                break;

            case "DROPDOWN":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders\n");
                prompt.append("- xxxxxx is a random 6-character ID using lowercase a-z and 0-9\n");
                prompt.append("- Each dropdown has 3–4 options, exactly 1 with isCorrect=true\n");
                prompt.append("- All options for one dropdown share the same positionId\n");
                prompt.append("- Options should be grammatically similar but contextually different\n");
                break;

            case "MULTIPLE_SELECT":
                prompt.append("- 4–6 options total\n");
                prompt.append("- 2–3 options with isCorrect=true\n");
                prompt.append("- positionId=null\n");
                prompt.append("- Clearly indicate \"Select all correct answers\"\n");
                break;

            case "DRAG_AND_DROP":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders for drop zones\n");
                prompt.append("- xxxxxx is a random 6-character ID using lowercase a-z and 0-9\n");
                prompt.append("- Each item in data must have positionId corresponding to its correct drop zone\n");
                prompt.append("- Each draggable item corresponds to exactly one drop zone\n");
                prompt.append("- Items should be logically related to their zones\n");
                break;

            case "REARRANGE":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders for each item\n");
                prompt.append("- Example: \"[[pos_ab12cd]] [[pos_ef34gh]] [[pos_ij56kl]]\"\n");
                prompt.append("- NO other text in questionText except placeholders\n");
                prompt.append("- Each placeholder represents one movable word/phrase\n");
                prompt.append("- xxxxxx is random 6-char ID (lowercase letters + digits)\n");
                prompt.append("- Each item must have positionId matching its placeholder\n");
                prompt.append("- All items have isCorrect=true (no false answers)\n");
                prompt.append("- Create logical sentences when arranged correctly\n");
                prompt.append("Example:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"[[pos_a1b2c3]] [[pos_d4e5f6]] [[pos_g7h8i9]]\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"REARRANGE\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"item1\", \"value\": \"She\", \"isCorrect\": true, \"positionId\": \"a1b2c3\"},\n")
                        .append("      {\"id\": \"item2\", \"value\": \"is\", \"isCorrect\": true, \"positionId\": \"d4e5f6\"},\n")
                        .append("      {\"id\": \"item3\", \"value\": \"running\", \"isCorrect\": true, \"positionId\": \"g7h8i9\"}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n");
                break;

            case "REWRITE":
                prompt.append("- Open-ended rewriting exercise\n");
                prompt.append("- Must have ONLY 1 correct answer (isCorrect=true)\n");
                prompt.append("- positionId=null\n");
                prompt.append("- Answer must be grammatically correct and preserve meaning\n");
                prompt.append("- Specify what to change (e.g., 'Rewrite using passive voice')\n");
                prompt.append("Example:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"Rewrite using 'so...that': He is too tired to work.\",\n")
                        .append("  \"orderNumber\": 1,\n")
                        .append("  \"score\": 1.0,\n")
                        .append("  \"questionType\": \"REWRITE\",\n")
                        .append("  \"content\": {\n")
                        .append("    \"data\": [\n")
                        .append("      {\"id\": \"ans1\", \"value\": \"He is so tired that he can't work.\", \"isCorrect\": true, \"positionId\": null}\n")
                        .append("    ]\n")
                        .append("  }\n")
                        .append("}\n");
                break;

            default:
                prompt.append("Follow standard question format with all required fields.\n");
        }

        prompt.append("\n✅ GLOBAL VALIDATION RULES:\n");
        prompt.append("- Use ONLY lowercase letters (a-z) and numbers (0-9) for position IDs\n");
        prompt.append("- Ensure JSON is valid and properly formatted\n");
        prompt.append("- For FILL_IN_THE_BLANK: Use natural format with hints like \"...(be)\" or \"...(verb)\"\n");
        prompt.append("- For position-based types: questionText MUST include [[pos_xxxxxx]] placeholders\n");
        prompt.append("- positionId must match xxxxxx exactly\n");
        prompt.append("- Each ID must be UNIQUE across all positions\n");
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
                        Map.of("role", "system", "content", "You are an expert English teacher. You MUST return ONLY valid JSON without any markdown formatting, code blocks, or explanations. The JSON must be parseable directly."),
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

                    content = content.trim();
                    if (content.startsWith("```json")) {
                        content = content.substring(7);
                    }
                    if (content.startsWith("```")) {
                        content = content.substring(3);
                    }
                    if (content.endsWith("```")) {
                        content = content.substring(0, content.length() - 3);
                    }

                    content = content.trim();
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
        question.setScore(scoreNode != null ? scoreNode.asDouble() : 1.0);

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
    public GenerateReadingPassageResponse generateReadingPassage(GenerateReadingPassageRequest request) {
        log.info("Generating reading passage for challengeId: {} with {} paragraphs",
                request.getChallengeId(), request.getNumberOfParagraphs());

        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        int wordsPerParagraph = wordsPerParagraphDefault;
        String level = challenge.getClassLesson().getClassChapter().getClazz()
                .getSyllabus().getLevel().getLevelName();

        log.info("Generating passage with {} words per paragraph", wordsPerParagraph);

        String prompt = buildReadingPassagePrompt(
                request.getNumberOfParagraphs(),
                wordsPerParagraph,
                request.getDescription(),
                "",
                level
        );

        String aiResponse = callOpenAI(prompt);
        GenerateReadingPassageResponse response = parseReadingPassageResponse(aiResponse, level);

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
}