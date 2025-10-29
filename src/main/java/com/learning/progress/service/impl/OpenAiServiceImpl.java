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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class OpenAiServiceImpl implements OpenAiService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DailyChallengeRepository dailyChallengeRepository;

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

    /**
     * API 1: Generate GV (Grammar/Vocabulary) questions
     * Each question in a separate section (resourceType = NONE)
     */
    @Override
    public List<SectionWithQuestionsDto> generateGVQuestions(GenerateGVQuestionsRequest request) {
        log.info("Starting GV question generation for challengeId: {}", request.getChallengeId());

        // 1. Validate challenge
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // 2. Build context


        // 3. Generate sections - 1 question per section
        List<SectionWithQuestionsDto> results = new ArrayList<>();
        int sectionOrder = 1;

        for (GenerateGVQuestionsRequest.QuestionTypeConfig config : request.getQuestionTypeConfigs()) {
            String questionType = config.getQuestionType();
            int numberOfQuestions = config.getNumberOfQuestions();

            String contextInfo = buildEnhancedContextInfo(questionType);
            log.info("Generating {} {} questions", numberOfQuestions, questionType);

            // Generate N questions, each in its own section
            for (int i = 0; i < numberOfQuestions; i++) {
                try {
                    // Create section for this single question
                    SectionDto section = new SectionDto();
                    section.setId(null);
                    section.setSectionTitle(null);
                    section.setSectionsContent(null);
                    section.setOrderNumber(sectionOrder++);
                    section.setResourceType("NONE");

                    // Generate 1 question for this section
                    List<QuestionDto> questions = generateGVQuestionForSection(
                            challenge,
                            questionType,
                            request.getDescription(),
                            contextInfo
                    );

                    SectionWithQuestionsDto result = new SectionWithQuestionsDto(section, questions);
                    results.add(result);

                } catch (Exception e) {
                    log.error("Failed to generate {} question {}: {}", questionType, i + 1, e.getMessage(), e);
                    throw new RuntimeException("Failed to generate question: " + e.getMessage(), e);
                }
            }
        }

        log.info("Successfully generated {} sections with {} total questions",
                results.size(), results.size()); // Each section has 1 question

        return results;
    }


    @Override
    public List<SectionWithQuestionsDto> generateContentBasedQuestions(GenerateContentBasedQuestionsRequest request) {
        log.info("Starting content-based question generation for challengeId: {}", request.getChallengeId());

        // 1. Validate challenge
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // 2. Get DC Type
        String dailyChallengeType = challenge.getChallengeType().toString();
        log.info("Daily Challenge Type: {}", dailyChallengeType);

        // 3. Build context

        // 4. Generate questions for each section
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

                // Generate all questions for this section
                List<QuestionDto> allQuestions = new ArrayList<>();
                int questionOrder = 1;

                for (GenerateContentBasedQuestionsRequest.QuestionTypeConfig config : sectionConfig.getQuestionTypeConfigs()) {
                    String questionType = config.getQuestionType();
                    int numberOfQuestions = config.getNumberOfQuestions();
                    String contextInfo = buildEnhancedContextInfo(questionType);

                    log.info("Generating {} {} questions for section", numberOfQuestions, questionType);

                    List<QuestionDto> questions = generateContentBasedQuestionsForSection(
                            challenge,
                            section,
                            questionType,
                            numberOfQuestions,
                            request.getDescription(),
                            contextInfo,
                            dailyChallengeType,
                            questionOrder
                    );

                    allQuestions.addAll(questions);
                    questionOrder += questions.size();
                }

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

        log.info("Successfully generated {} sections with total {} questions",
                results.size(), results.stream().mapToInt(s -> s.getQuestions().size()).sum());

        return results;
    }

    /**
     * Generate 1 GV question (for API 1)
     */
    private List<QuestionDto> generateGVQuestionForSection(
            DailyChallenge challenge,
            String questionType,
            String userDescription,
            String contextInfo) {

        String prompt = buildGVQuestionPrompt(challenge, questionType, userDescription, contextInfo);
        String aiResponse = callOpenAI(prompt);
        List<QuestionDto> questions = parseQuestionsFromResponse(aiResponse);

        // Ensure we only return 1 question
        if (questions.size() > 1) {
            questions = questions.subList(0, 1);
        }

        // Set IDs to null
        for (QuestionDto question : questions) {
            question.setId(null);
            question.setOrderNumber(1); // Always 1 since it's the only question in section
        }

        return questions;
    }

    /**
     * Generate multiple content-based questions (for API 2)
     */
    private List<QuestionDto> generateContentBasedQuestionsForSection(
            DailyChallenge challenge,
            SectionDto section,
            String questionType,
            int numberOfQuestions,
            String userDescription,
            String contextInfo,
            String dailyChallengeType,
            int startingOrderNumber) {

        String prompt = buildContentBasedQuestionPrompt(
                challenge,
                section,
                questionType,
                numberOfQuestions,
                userDescription,
                contextInfo,
                dailyChallengeType
        );

        String aiResponse = callOpenAI(prompt);
        List<QuestionDto> questions = parseQuestionsFromResponse(aiResponse);

        // Set IDs and order numbers
        int order = startingOrderNumber;
        for (QuestionDto question : questions) {
            question.setId(null);
            question.setOrderNumber(order++);
        }

        return questions;
    }

    /**
     * Build prompt for GV questions (API 1)
     */
    private String buildGVQuestionPrompt(
            DailyChallenge challenge,
            String questionType,
            String userDescription,
            String contextInfo) {

        StringBuilder prompt = new StringBuilder();

        // Lấy nội dung class_lesson
        String classLessonContent = challenge.getClassLesson() != null
                ? challenge.getClassLesson().getClassLessonContent()
                : "No lesson content available";

        prompt.append("You are an expert English teacher creating grammar/vocabulary exercises.\n\n");

        // User description
        if (userDescription != null && !userDescription.isBlank()) {
            prompt.append("🔥 USER REQUIREMENTS (ABSOLUTE PRIORITY) 🔥\n");
            prompt.append(userDescription).append("\n");
        }

        // Context
        prompt.append("CONTEXT (Reference Only):\n");
        prompt.append("Lesson Content:\n");
        prompt.append(classLessonContent).append("\n");
        prompt.append(contextInfo).append("\n");

        prompt.append("You can broaden the question slightly beyond the exact lesson sentences,\n");
        prompt.append("as long as it stays strictly within the same theme, grammar pattern, or vocabulary topic.\n");
        prompt.append("Avoid repeating sentences from the lesson word-for-word.\n\n");

        // Task
        prompt.append("TASK:\n");
        prompt.append("Generate EXACTLY 1 question of type: ").append(questionType).append("\n");
        prompt.append("This is a Grammar/Vocabulary question (NONE resource type)\n");
        prompt.append("All questions MUST be based on the lesson content provided above.\n\n");

        // Format
        appendJSONFormat(prompt, questionType);

        // Question type rules
        appendQuestionTypeRules(prompt, questionType);

        // ✅ Thêm yêu cầu không trùng câu hỏi
        prompt.append("\n🚫 DUPLICATION RULES:\n");
        prompt.append("- The generated question must be UNIQUE and not identical or too similar\n");
        prompt.append("  to any existing question from this lesson or previous ones.\n");
        prompt.append("- Do NOT reuse the same sentence structure, wording, or main idea.\n");
        prompt.append("- Encourage creativity while keeping correctness and topic relevance.\n");

        // Requirements
        prompt.append("\n🔥 ABSOLUTE REQUIREMENTS:\n");
        prompt.append("1. Return ONLY valid JSON - no markdown, no explanations\n");
        prompt.append("2. Generate EXACTLY 1 question\n");
        prompt.append("3. Question type: ").append(questionType).append("\n");
        prompt.append("4. For FILL_IN_THE_BLANK: MUST use [[pos_xxxxx]] format with random 6-char IDs\n");
        prompt.append("5. All required fields must be present\n");
        prompt.append("6. Questions MUST be relevant to the lesson content provided\n");

        return prompt.toString();
    }

    /**
     * Build prompt for content-based questions (API 2)
     */
    private String buildContentBasedQuestionPrompt(
            DailyChallenge challenge,
            SectionDto section,
            String questionType,
            int numberOfQuestions,
            String userDescription,
            String contextInfo,
            String dailyChallengeType) {

        StringBuilder prompt = new StringBuilder();

        // Lấy nội dung class_lesson
        String classLessonContent = challenge.getClassLesson() != null
                ? challenge.getClassLesson().getClassLessonContent()
                : "No lesson content available";

        prompt.append("You are an expert English teacher creating comprehension exercises.\n\n");

        // User description
        if (userDescription != null && !userDescription.isBlank()) {
            prompt.append("🔥 USER REQUIREMENTS (ABSOLUTE PRIORITY) 🔥\n");
            prompt.append(userDescription).append("\n");
        }

        // DC Type instructions
        prompt.append("CHALLENGE TYPE: ").append(dailyChallengeType).append("\n");
        appendDCTypeInstructions(prompt, dailyChallengeType);

        // Context
        prompt.append("\nCONTEXT (Reference Only):\n");
        prompt.append("Lesson Content:\n");
        prompt.append(classLessonContent).append("\n");
        prompt.append(contextInfo).append("\n");

        // Section content (CRITICAL for RE/LI)
        prompt.append("\n📖 SECTION CONTENT (Base ALL questions on this):\n");
        prompt.append(section.getSectionsContent()).append("\n");

        prompt.append("You can broaden the question slightly beyond the exact lesson sentences,\n");
        prompt.append("as long as it stays strictly within the same theme, grammar pattern, or vocabulary topic.\n");
        prompt.append("Avoid repeating sentences from the lesson word-for-word.\n\n");

        // Task
        prompt.append("TASK:\n");
        prompt.append("Generate EXACTLY ").append(numberOfQuestions).append(" questions of type: ").append(questionType).append("\n");
        prompt.append("All questions MUST be based on the section content above, with reference to the lesson content for additional context.\n\n");

        // Format
        appendJSONFormat(prompt, questionType);

        // Question type rules
        appendQuestionTypeRules(prompt, questionType);

        // ✅ Thêm yêu cầu không trùng câu hỏi
        prompt.append("\n🚫 DUPLICATION RULES:\n");
        prompt.append("- The generated question must be UNIQUE and not identical or too similar\n");
        prompt.append("  to any existing question from this lesson or previous ones.\n");
        prompt.append("- Do NOT reuse the same sentence structure, wording, or main idea.\n");
        prompt.append("- Encourage creativity while keeping correctness and topic relevance.\n");

        // Requirements
        prompt.append("\n🔥 ABSOLUTE REQUIREMENTS:\n");
        prompt.append("1. Return ONLY valid JSON - no markdown, no explanations\n");
        prompt.append("2. Generate EXACTLY ").append(numberOfQuestions).append(" questions\n");
        prompt.append("3. ALL questions MUST be answerable ONLY by reading the section content\n");
        prompt.append("4. Question type: ").append(questionType).append("\n");
        prompt.append("5. For FILL_IN_THE_BLANK: MUST use [[pos_xxxxx]] format with random 6-char IDs\n");
        prompt.append("6. All required fields must be present\n");
        prompt.append("7. Use lesson content as additional context to ensure relevance\n");

        return prompt.toString();
    }

    /**
     * Append DC Type instructions
     */
    private void appendDCTypeInstructions(StringBuilder prompt, String dcType) {
        switch (dcType) {
            case "RE": // Reading
                prompt.append("📖 READING COMPREHENSION:\n");
                prompt.append("- Base ALL questions on the section content (reading passage)\n");
                prompt.append("- Test comprehension, inference, vocabulary in context\n");
                prompt.append("- Questions should reference specific parts of the passage\n");
                prompt.append("- Ensure questions can ONLY be answered by reading the passage\n");
                break;

            case "LI": // Listening
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

        // ✅ Thêm phần nhấn mạnh cuối cùng để AI không quên
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

        // questionText - REQUIRED
        JsonNode textNode = questionNode.get("questionText");
        if (textNode == null || textNode.isNull()) {
            throw new RuntimeException("Missing questionText for question " + index);
        }
        question.setQuestionText(textNode.asText());

        // orderNumber
        JsonNode orderNode = questionNode.get("orderNumber");
        question.setOrderNumber(orderNode != null ? orderNode.asInt() : index);

        // score
        JsonNode scoreNode = questionNode.get("score");
        question.setScore(scoreNode != null ? scoreNode.asDouble() : 1.0);

        // questionType
        JsonNode typeNode = questionNode.get("questionType");
        question.setQuestionType(typeNode != null ? typeNode.asText() : expectedType);

        // Parse content
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

        // id - REQUIRED
        JsonNode idNode = itemNode.get("id");
        if (idNode == null || idNode.isNull()) {
            throw new RuntimeException("Missing 'id' in data item");
        }
        dataItem.setId(idNode.asText());

        // value - REQUIRED
        JsonNode valueNode = itemNode.get("value");
        if (valueNode == null || valueNode.isNull()) {
            throw new RuntimeException("Missing 'value' in data item");
        }
        dataItem.setValue(valueNode.asText());

        // isCorrect - REQUIRED
        JsonNode correctNode = itemNode.get("isCorrect");
        if (correctNode == null || correctNode.isNull()) {
            throw new RuntimeException("Missing 'isCorrect' in data item");
        }
        dataItem.setCorrect(correctNode.asBoolean());

        // positionId - OPTIONAL
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

            // Apply replacements
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                questionText = questionText.replace(
                        "[[pos_" + entry.getKey() + "]]",
                        "[[pos_" + entry.getValue() + "]]"
                );
            }
            question.setQuestionText(questionText);

            // Update data items
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

        // 1. Validate file
        FileContentExtractor.validateFileNotEmpty(file);
        FileContentExtractor.validateFileSize(file);

        // 2. Extract content from file
        String fileContent = FileContentExtractor.extractContent(file);

        if (fileContent.isEmpty()) {
            throw new RuntimeException("No content extracted from file");
        }

        log.info("Extracted {} characters from file", fileContent.length());

        // 3. Build parsing prompt
        String prompt = buildParsingPrompt(fileContent, description);

        // 4. Call OpenAI to parse and structure
        String aiResponse = callOpenAI(prompt);

        // 5. Parse response into sections
        List<SectionWithQuestionsDto> sections = parseMultipleSectionsResponse(aiResponse);

        // 6. Set all IDs to null (not saved to DB)
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

        // Validate challenge
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // Get fixed words per paragraph from config
        int wordsPerParagraph = wordsPerParagraphDefault;
        String level = challenge.getClassLesson().getClassChapter().getClazz()
                .getSyllabus().getLevel().getLevelName();

        log.info("Generating passage with {} words per paragraph", wordsPerParagraph);

        // Build context and prompt
        String prompt = buildReadingPassagePrompt(
                request.getNumberOfParagraphs(),
                wordsPerParagraph,
                request.getDescription(),
                "",
                level
        );

        // Call AI and parse
        String aiResponse = callOpenAI(prompt);
        GenerateReadingPassageResponse response = parseReadingPassageResponse(aiResponse, level);

        log.info("Successfully generated passage: {} paragraphs, {} words",
                response.getNumberOfParagraphs(), response.getTotalWords());

        return response;
    }

    // 4. THÊM buildReadingPassagePrompt
    private String buildReadingPassagePrompt(
            int numberOfParagraphs,
            int wordsPerParagraph,
            String description,
            String contextInfo,
            String level) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert English teacher creating reading passages.\n\n");

        // User description (if provided)
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

    // 5. THÊM parseReadingPassageResponse
    private GenerateReadingPassageResponse parseReadingPassageResponse(String jsonResponse, String level) {
        try {
            // Clean markdown
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

    /**
     * Parse response containing multiple sections
     */
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
                    // Parse section info
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

                    // Parse questions
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
                            // Continue with other questions
                        }
                    }

                    if (!questions.isEmpty()) {
                        // Ensure unique position IDs within this section
                        ensureUniquePositionIds(questions);

                        SectionWithQuestionsDto sectionWithQuestions = new SectionWithQuestionsDto(section, questions);
                        sections.add(sectionWithQuestions);
                        log.info("Parsed section {} with {} questions", sectionIndex, questions.size());
                    }

                } catch (Exception e) {
                    log.error("Error parsing section {}: {}", sectionIndex, e.getMessage());
                    // Continue with other sections
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
            // Count existing answers (correct + distractors)
            int existingCount = 1; // correct answer
            if (request.getExistingDistractors() != null) {
                existingCount += request.getExistingDistractors().size();
            }

            // Determine how many distractors to generate
            int distractorsToGenerate = existingCount < 4 ? (4 - existingCount) : 1;

            // Build prompt
            String prompt = buildDistractorsPrompt(request, distractorsToGenerate);

            // Call OpenAI
            String aiResponse = callOpenAI(prompt);

            // Parse response
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
            // Clean response if needed
            String cleaned = jsonResponse.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            // Parse JSON array
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

        // 1. Validate input
        if (textContent == null || textContent.trim().isEmpty()) {
            throw new RuntimeException("Text content cannot be empty");
        }

        // 2. Build parsing prompt (reuse existing method)
        String prompt = buildParsingPrompt(textContent, description);

        // 3. Call OpenAI to parse and structure
        String aiResponse = callOpenAI(prompt);

        // 4. Parse response into sections (reuse existing method)
        List<SectionWithQuestionsDto> sections = parseMultipleSectionsResponse(aiResponse);

        // 5. Set all IDs to null (not saved to DB)
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