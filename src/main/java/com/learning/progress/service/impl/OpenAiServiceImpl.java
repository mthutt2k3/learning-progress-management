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

    // Thread pool for parallel API calls
    private final ExecutorService executorService;
    private static final int BATCH_SIZE = 10; // Number of questions per API call
    private static final int THREAD_POOL_SIZE = 10; // Number of parallel threads

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
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
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
     * API 1: Generate GV (Grammar/Vocabulary) questions - OPTIMIZED WITH BATCHING
     */
    @Override
    public List<SectionWithQuestionsDto> generateGVQuestions(GenerateGVQuestionsRequest request) {
        log.info("Starting OPTIMIZED GV question generation for challengeId: {}", request.getChallengeId());

        // 1. Validate challenge
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // 2. Prepare all question generation tasks
        List<QuestionGenerationTask> allTasks = new ArrayList<>();
        int sectionOrder = 1;

        for (GenerateGVQuestionsRequest.QuestionTypeConfig config : request.getQuestionTypeConfigs()) {
            String questionType = config.getQuestionType();
            int numberOfQuestions = config.getNumberOfQuestions();
            String contextInfo = buildEnhancedContextInfo(questionType);

            log.info("Preparing {} {} questions for parallel generation", numberOfQuestions, questionType);

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

        // 3. Generate questions in parallel batches
        List<SectionWithQuestionsDto> results = generateQuestionsInParallelBatches(allTasks, true);

        log.info("Successfully generated {} sections with {} total questions using PARALLEL BATCHING",
                results.size(), results.size());

        return results;
    }

    @Override
    public List<SectionWithQuestionsDto> generateContentBasedQuestions(GenerateContentBasedQuestionsRequest request) {
        log.info("Starting OPTIMIZED content-based question generation for challengeId: {}", request.getChallengeId());

        // 1. Validate challenge
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        String dailyChallengeType = challenge.getChallengeType().toString();
        log.info("Daily Challenge Type: {}", dailyChallengeType);

        // 2. Generate questions for each section
        List<SectionWithQuestionsDto> results = new ArrayList<>();

        for (GenerateContentBasedQuestionsRequest.SectionWithConfig sectionConfig : request.getSections()) {
            SectionDto section = sectionConfig.getSection();

            log.info("Processing section: {} (ResourceType: {})",
                    section.getSectionTitle(), section.getResourceType());

            try {
                // Validate section has content
                if (section.getSectionsContent() == null || section.getSectionsContent().isBlank()) {
                    throw new IllegalArgumentException("Section content is required for content-based questions");
                }

                // Prepare all question generation tasks for this section
                List<ContentBasedQuestionTask> sectionTasks = new ArrayList<>();
                int questionOrder = 1;

                for (GenerateContentBasedQuestionsRequest.QuestionTypeConfig config : sectionConfig.getQuestionTypeConfigs()) {
                    String questionType = config.getQuestionType();
                    int numberOfQuestions = config.getNumberOfQuestions();
                    String contextInfo = buildEnhancedContextInfo(questionType);

                    log.info("Preparing {} {} questions for section", numberOfQuestions, questionType);

                    // Create tasks for batch processing
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

                // Generate all questions for this section in parallel batches
                List<QuestionDto> allQuestions = generateContentBasedQuestionsInParallelBatches(sectionTasks);

                // Create result with all questions in this section
                SectionWithQuestionsDto result = new SectionWithQuestionsDto(section, allQuestions);
                results.add(result);

                log.info("Generated {} questions for section: {}", allQuestions.size(), section.getSectionTitle());

            } catch (Exception e) {
                log.error("Failed to generate questions for section {}: {}",
                        section.getSectionTitle(), e.getMessage(), e);
                throw new RuntimeException("Failed to generate questions for section "
                        + section.getSectionTitle() + ": " + e.getMessage(), e);
            }
        }

        log.info("Successfully generated {} sections with total {} questions using PARALLEL BATCHING",
                results.size(), results.stream().mapToInt(s -> s.getQuestions().size()).sum());

        return results;
    }

    /**
     * Generate questions in parallel batches - CORE OPTIMIZATION
     */
    private List<SectionWithQuestionsDto> generateQuestionsInParallelBatches(
            List<QuestionGenerationTask> allTasks,
            boolean oneQuestionPerSection) {

        // Split into batches of BATCH_SIZE
        List<List<QuestionGenerationTask>> batches = splitIntoBatches(allTasks, BATCH_SIZE);
        log.info("Split {} tasks into {} batches (batch size: {})", allTasks.size(), batches.size(), BATCH_SIZE);

        // Process batches in parallel using CompletableFuture
        List<CompletableFuture<List<QuestionDto>>> futures = batches.stream()
                .map(batch -> CompletableFuture.supplyAsync(
                        () -> generateBatchOfGVQuestions(batch),
                        executorService
                ))
                .collect(Collectors.toList());

        // Wait for all batches to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allOf.get(5, TimeUnit.MINUTES); // Timeout after 5 minutes
        } catch (Exception e) {
            log.error("Error waiting for parallel batch completion: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate questions in parallel: " + e.getMessage(), e);
        }

        // Collect all generated questions
        List<QuestionDto> allGeneratedQuestions = futures.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        log.error("Error getting batch result: {}", e.getMessage());
                        return Collections.<QuestionDto>emptyList();
                    }
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // Create sections from questions
        List<SectionWithQuestionsDto> results = new ArrayList<>();
        for (int i = 0; i < allGeneratedQuestions.size(); i++) {
            QuestionDto question = allGeneratedQuestions.get(i);
            question.setId(null);
            question.setOrderNumber(1); // Always 1 for single question per section

            SectionDto section = new SectionDto();
            section.setId(null);
            section.setSectionTitle(null);
            section.setSectionsContent(null);
            section.setOrderNumber(i + 1);
            section.setResourceType("NONE");

            results.add(new SectionWithQuestionsDto(section, Collections.singletonList(question)));
        }

        // Ensure unique position IDs across all questions
        List<QuestionDto> allQuestions = results.stream()
                .flatMap(s -> s.getQuestions().stream())
                .collect(Collectors.toList());
        ensureUniquePositionIds(allQuestions);

        return results;
    }

    /**
     * Generate content-based questions in parallel batches
     */
    private List<QuestionDto> generateContentBasedQuestionsInParallelBatches(
            List<ContentBasedQuestionTask> allTasks) {

        // Split into batches
        List<List<ContentBasedQuestionTask>> batches = splitIntoBatches(allTasks, BATCH_SIZE);
        log.info("Split {} content-based tasks into {} batches", allTasks.size(), batches.size());

        // Process batches in parallel
        List<CompletableFuture<List<QuestionDto>>> futures = batches.stream()
                .map(batch -> CompletableFuture.supplyAsync(
                        () -> generateBatchOfContentBasedQuestions(batch),
                        executorService
                ))
                .collect(Collectors.toList());

        // Wait for all batches to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allOf.get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Error waiting for parallel batch completion: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate content-based questions in parallel: " + e.getMessage(), e);
        }

        // Collect all generated questions and maintain order
        List<QuestionDto> allGeneratedQuestions = futures.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        log.error("Error getting batch result: {}", e.getMessage());
                        return Collections.<QuestionDto>emptyList();
                    }
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // Set IDs to null and fix order numbers
        for (int i = 0; i < allGeneratedQuestions.size(); i++) {
            QuestionDto question = allGeneratedQuestions.get(i);
            question.setId(null);
            question.setOrderNumber(i + 1);
        }

        // Ensure unique position IDs
        ensureUniquePositionIds(allGeneratedQuestions);

        return allGeneratedQuestions;
    }

    /**
     * Generate a batch of GV questions in one API call
     */
    private List<QuestionDto> generateBatchOfGVQuestions(List<QuestionGenerationTask> batch) {
        try {
            log.info("Generating batch of {} GV questions", batch.size());

            // All tasks in batch should have same question type
            QuestionGenerationTask firstTask = batch.get(0);
            String prompt = buildBatchGVQuestionPrompt(
                    firstTask.challenge,
                    firstTask.questionType,
                    firstTask.userDescription,
                    firstTask.contextInfo,
                    batch.size()
            );

            String aiResponse = callOpenAI(prompt);
            List<QuestionDto> questions = parseQuestionsFromResponse(aiResponse);

            // Ensure we return exactly the requested number
            if (questions.size() > batch.size()) {
                questions = questions.subList(0, batch.size());
            }

            log.info("Successfully generated batch of {} questions", questions.size());
            return questions;

        } catch (Exception e) {
            log.error("Failed to generate batch: {}", e.getMessage(), e);
            // Return empty list instead of throwing to not break entire process
            return Collections.emptyList();
        }
    }

    /**
     * Generate a batch of content-based questions in one API call
     */
    private List<QuestionDto> generateBatchOfContentBasedQuestions(List<ContentBasedQuestionTask> batch) {
        try {
            log.info("Generating batch of {} content-based questions", batch.size());

            ContentBasedQuestionTask firstTask = batch.get(0);
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

            // Ensure we return exactly the requested number
            if (questions.size() > batch.size()) {
                questions = questions.subList(0, batch.size());
            }

            log.info("Successfully generated batch of {} content-based questions", questions.size());
            return questions;

        } catch (Exception e) {
            log.error("Failed to generate content-based batch: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Split list into batches
     */
    private <T> List<List<T>> splitIntoBatches(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(new ArrayList<>(list.subList(i, end)));
        }
        return batches;
    }

    /**
     * Build prompt for BATCH GV questions
     */
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

        prompt.append("You are an expert English teacher creating grammar/vocabulary exercises.\n\n");

        if (userDescription != null && !userDescription.isBlank()) {
            prompt.append("🔥 USER REQUIREMENTS (ABSOLUTE PRIORITY) 🔥\n");
            prompt.append(userDescription).append("\n");
        }

        prompt.append("CONTEXT (Reference Only):\n");
        prompt.append("Lesson Content:\n");
        prompt.append(classLessonContent).append("\n");
        prompt.append(contextInfo).append("\n");

        prompt.append("You can broaden the question slightly beyond the exact lesson sentences,\n");
        prompt.append("as long as it stays strictly within the same theme, grammar pattern, or vocabulary topic.\n");
        prompt.append("Avoid repeating sentences from the lesson word-for-word.\n\n");

        // IMPORTANT: Request multiple questions
        prompt.append("TASK:\n");
        prompt.append("Generate EXACTLY ").append(numberOfQuestions).append(" DIFFERENT questions of type: ").append(questionType).append("\n");
        prompt.append("These are Grammar/Vocabulary questions (NONE resource type)\n");
        prompt.append("All questions MUST be based on the lesson content provided above.\n");
        prompt.append("Each question MUST be UNIQUE and different from each other.\n\n");

        appendJSONFormat(prompt, questionType);
        appendQuestionTypeRules(prompt, questionType);

        prompt.append("\n🚫 DUPLICATION RULES:\n");
        prompt.append("- Each generated question must be UNIQUE and not identical or too similar to others\n");
        prompt.append("- Do NOT reuse the same sentence structure, wording, or main idea\n");
        prompt.append("- Make sure all ").append(numberOfQuestions).append(" questions are distinctly different\n");
        prompt.append("- Encourage creativity while keeping correctness and topic relevance.\n");

        prompt.append("\n🔥 ABSOLUTE REQUIREMENTS:\n");
        prompt.append("1. Return ONLY valid JSON - no markdown, no explanations\n");
        prompt.append("2. Generate EXACTLY ").append(numberOfQuestions).append(" DIFFERENT questions\n");
        prompt.append("3. Question type: ").append(questionType).append("\n");
        prompt.append("4. For FILL_IN_THE_BLANK: MUST use [[pos_xxxxx]] format with random 6-char IDs\n");
        prompt.append("5. All required fields must be present\n");
        prompt.append("6. Questions MUST be relevant to the lesson content provided\n");
        prompt.append("7. Each question must be UNIQUE - no duplicates or very similar questions\n");

        return prompt.toString();
    }

    /**
     * Build prompt for BATCH content-based questions
     */
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

        prompt.append("You are an expert English teacher creating comprehension exercises.\n\n");

        if (userDescription != null && !userDescription.isBlank()) {
            prompt.append("🔥 USER REQUIREMENTS (ABSOLUTE PRIORITY) 🔥\n");
            prompt.append(userDescription).append("\n");
        }

        prompt.append("CHALLENGE TYPE: ").append(dailyChallengeType).append("\n");
        appendDCTypeInstructions(prompt, dailyChallengeType);

        prompt.append("\nCONTEXT (Reference Only):\n");
        prompt.append("Lesson Content:\n");
        prompt.append(classLessonContent).append("\n");
        prompt.append(contextInfo).append("\n");

        prompt.append("\n📖 SECTION CONTENT (Base ALL questions on this):\n");
        prompt.append(section.getSectionsContent()).append("\n");

        prompt.append("You can broaden the question slightly beyond the exact lesson sentences,\n");
        prompt.append("as long as it stays strictly within the same theme, grammar pattern, or vocabulary topic.\n");
        prompt.append("Avoid repeating sentences from the lesson word-for-word.\n\n");

        prompt.append("TASK:\n");
        prompt.append("Generate EXACTLY ").append(numberOfQuestions).append(" DIFFERENT questions of type: ").append(questionType).append("\n");
        prompt.append("All questions MUST be based on the section content above, with reference to the lesson content for additional context.\n");
        prompt.append("Each question MUST be UNIQUE and different from each other.\n\n");

        appendJSONFormat(prompt, questionType);
        appendQuestionTypeRules(prompt, questionType);

        prompt.append("\n🚫 DUPLICATION RULES:\n");
        prompt.append("- Each generated question must be UNIQUE and not identical or too similar to others\n");
        prompt.append("- Do NOT reuse the same sentence structure, wording, or main idea\n");
        prompt.append("- Make sure all ").append(numberOfQuestions).append(" questions are distinctly different\n");
        prompt.append("- Encourage creativity while keeping correctness and topic relevance.\n");

        prompt.append("\n🔥 ABSOLUTE REQUIREMENTS:\n");
        prompt.append("1. Return ONLY valid JSON - no markdown, no explanations\n");
        prompt.append("2. Generate EXACTLY ").append(numberOfQuestions).append(" DIFFERENT questions\n");
        prompt.append("3. ALL questions MUST be answerable ONLY by reading the section content\n");
        prompt.append("4. Question type: ").append(questionType).append("\n");
        prompt.append("5. For FILL_IN_THE_BLANK: MUST use [[pos_xxxxx]] format with random 6-char IDs\n");
        prompt.append("6. All required fields must be present\n");
        prompt.append("7. Use lesson content as additional context to ensure relevance\n");
        prompt.append("8. Each question must be UNIQUE - no duplicates or very similar questions\n");

        return prompt.toString();
    }

    // Helper classes for task management
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

    // ========== KEEP ALL EXISTING HELPER METHODS BELOW ==========

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
        prompt.append("JSON FORMAT:\n");
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

            // Ensure unique position IDs
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
        prompt.append("SPECIFIC RULES FOR ").append(questionType).append(":\n\n");

        switch (questionType) {

            case "MULTIPLE_CHOICE":
                prompt.append("- 4 options per question.\n");
                prompt.append("- Exactly 1 option with isCorrect=true.\n");
                prompt.append("- positionId=null for all options.\n");
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
                prompt.append("- 2 options: \"True\" and \"False\".\n");
                prompt.append("- Exactly 1 option with isCorrect=true.\n");
                prompt.append("- positionId=null.\n");
                break;

            case "FILL_IN_THE_BLANK":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders (e.g., [[pos_a7k3m2]]).\n");
                prompt.append("- xxxxxx is a random 6-character ID using lowercase a-z and 0-9.\n");
                prompt.append("- Each positionId in data must match its corresponding xxxxxx.\n");
                prompt.append("- Each ID must be UNIQUE.\n");
                prompt.append("- Each blank has 1 correct answer and do not have distractors.\n");
                break;

            case "DROPDOWN":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders.\n");
                prompt.append("- xxxxxx is a random 6-character ID using lowercase a-z and 0-9.\n");
                prompt.append("- Each dropdown has 3–4 options, exactly 1 with isCorrect=true.\n");
                prompt.append("- All options for one dropdown share the same positionId.\n");
                break;

            case "MULTIPLE_SELECT":
                prompt.append("- 4–6 options.\n");
                prompt.append("- 2–3 options with isCorrect=true.\n");
                prompt.append("- positionId=null.\n");
                break;

            case "DRAG_AND_DROP":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders for drop zones.\n");
                prompt.append("- xxxxxx is a random 6-character ID using lowercase a-z and 0-9.\n");
                prompt.append("- Each item in data must have positionId corresponding to its correct drop zone.\n");
                break;

            case "REARRANGE":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- questionText MUST contain [[pos_xxxxxx]] placeholders for each reorder item.\n");
                prompt.append("- Example: \"[[pos_ab12cd]] [[pos_ef34gh]] [[pos_ij56kl]]\" and do not contain any other text in question\n");
                prompt.append("- Each placeholder represents one movable item.\n");
                prompt.append("- xxxxxx is a random 6-character ID using lowercase letters and digits.\n");
                prompt.append("- Each item in data must have positionId matching its placeholder.\n");
                prompt.append("- All items must have isCorrect=true (no false answers).\n");
                prompt.append("- Learners will rearrange items according to the placeholder order.\n");
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
                prompt.append("- Open-ended question that asks learner to rewrite a sentence.\n");
                prompt.append("- Must have ONLY 1 correct answer (isCorrect=true).\n");
                prompt.append("- positionId=null for answer.\n");
                prompt.append("- Answer must be grammatically correct and preserve original meaning.\n");
                prompt.append("Example:\n");
                prompt.append("{\n")
                        .append("  \"questionText\": \"He is too tired to work.\",\n")
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
                prompt.append("Follow the standard format for question structure.\n");
        }

        prompt.append("\nGLOBAL RULES:\n");
        prompt.append("- Use only lowercase letters and numbers for generated IDs.\n");
        prompt.append("- Ensure JSON is valid and formatted properly.\n");
        prompt.append("- For FILL_IN_THE_BLANK, DROPDOWN, DRAG_AND_DROP, REARRANGE → questionText MUST include [[pos_xxxxxx]].\n");
        prompt.append("- positionId must match xxxxxx exactly.\n");
    }


    private String callOpenAI(String prompt) {
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

                    // Clean markdown
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

        prompt.append("You are an expert English teacher creating reading passages.\n\n");

        if (description != null && !description.isBlank()) {
            prompt.append("🔥 USER REQUIREMENTS (ABSOLUTE PRIORITY) 🔥\n");
            prompt.append(description).append("\n");
        }

        prompt.append("CONTEXT:\n");
        prompt.append(contextInfo).append("\n");

        prompt.append("TASK:\n");
        prompt.append("Generate a reading passage with EXACTLY ").append(numberOfParagraphs).append(" paragraph(s)\n");
        prompt.append("Level: ").append(level).append("\n");
        prompt.append("Each paragraph: approximately ").append(wordsPerParagraph).append(" words\n\n");

        prompt.append("REQUIREMENTS:\n");
        prompt.append("- Create engaging educational content appropriate for ").append(level).append(" level\n");
        prompt.append("- Use suitable vocabulary and grammar\n");
        prompt.append("- Each paragraph has clear main idea\n");
        prompt.append("- Logical flow between paragraphs\n\n");

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
        prompt.append(fileContent).append("\n");

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
        prompt.append("   - CRITICAL: Replace blanks with [[pos_xxxxx]] format\n");
        prompt.append("   - Generate random 6-char lowercase IDs for each blank\n");
        prompt.append("   - positionId in data must match the xxxxx part\n");
        prompt.append("   Example input: \"I _____ to school.\" Answer: go\n");
        prompt.append("   Example output: questionText: \"I [[pos_a7k3m2]] to school.\"\n");
        prompt.append("                   data: [{\"value\": \"go\", \"isCorrect\": true, \"positionId\": \"a7k3m2\"}]\n\n");

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
        prompt.append("5. For FILL_IN_THE_BLANK: Use [[pos_xxxxx]] format with random IDs\n");
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