package com.learning.progress.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import com.learning.progress.dto.ai.ExerciseGenerationRequest;
import com.learning.progress.dto.challenge.section.*;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.DailyChallengeRepository;
import com.learning.progress.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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

    private static final String API_VERSION = "2025-01-01-preview";
    private static final Pattern POSITION_PATTERN = Pattern.compile("\\[\\[pos_([a-z0-9]+)\\]\\]");

    public OpenAiServiceImpl(DailyChallengeRepository dailyChallengeRepository) {
        this.dailyChallengeRepository = dailyChallengeRepository;
    }

    @Override
    public List<SectionWithQuestionsDto> generateExercise(ExerciseGenerationRequest request) {
        log.info("Starting exercise generation for challengeId: {} with {} question types",
                request.getChallengeId(), request.getQuestionTypeConfigs().size());

        // 1. Validate challenge
        DailyChallenge challenge = dailyChallengeRepository.findByIdAndDeletedAtIsNull(request.getChallengeId())
                .orElseThrow(() -> {
                    log.error("DailyChallenge not found: {}", request.getChallengeId());
                    return new ApiException(Const.CHALLENGE.NOT_FOUND, HttpStatus.NOT_FOUND.value());
                });

        // 2. Build enhanced context
        String contextInfo = buildEnhancedContextInfo(challenge);

        // 3. Generate sections (NO DB SAVE - just return DTO)
        List<SectionWithQuestionsDto> sections = new ArrayList<>();
        int orderNumber = 1;

        for (ExerciseGenerationRequest.QuestionTypeConfig config : request.getQuestionTypeConfigs()) {
            log.info("Generating section {} with {} {} questions",
                    orderNumber, config.getNumberOfQuestions(), config.getQuestionType());

            try {
                SectionWithQuestionsDto sectionWithQuestions = generateSectionForQuestionType(
                        challenge,
                        config.getQuestionType(),
                        config.getNumberOfQuestions(),
                        request.getDescription(),
                        contextInfo,
                        orderNumber
                );

                sections.add(sectionWithQuestions);
                orderNumber++;
            } catch (Exception e) {
                log.error("Failed to generate section for type {}: {}", config.getQuestionType(), e.getMessage(), e);
                throw new RuntimeException("Failed to generate " + config.getQuestionType() + " section: " + e.getMessage(), e);
            }
        }

        log.info("Successfully generated {} sections with total {} questions",
                sections.size(), sections.stream().mapToInt(s -> s.getQuestions().size()).sum());

        return sections;
    }

    private String buildEnhancedContextInfo(DailyChallenge challenge) {
        StringBuilder context = new StringBuilder();

        // Syllabus info
        Syllabus syllabus = challenge.getClassLesson().getClassChapter().getClazz().getSyllabus();
        context.append("SYLLABUS INFO:\n");
        context.append("- Name: ").append(syllabus.getSyllabusName()).append("\n");
        context.append("- Level: ").append(syllabus.getLevel().getLevelName()).append("\n");
        if (syllabus.getDescription() != null && !syllabus.getDescription().isBlank()) {
            context.append("- Description: ").append(syllabus.getDescription()).append("\n");
        }
        context.append("\n");

        // Chapter info
        ClassChapter chapter = challenge.getClassLesson().getClassChapter();
        context.append("CHAPTER INFO:\n");
        context.append("- Chapter ").append(chapter.getOrderNumber()).append(": ")
                .append(chapter.getClassChapterName()).append("\n");
        context.append("\n");

        // Lesson info
        ClassLesson lesson = challenge.getClassLesson();
        context.append("LESSON INFO:\n");
        context.append("- Lesson: ").append(lesson.getClassLessonName()).append("\n");

        if (lesson.getClassLessonContent() != null && !lesson.getClassLessonContent().isBlank()) {
            context.append("- Content: ").append(lesson.getClassLessonContent()).append("\n");
        }

        // Learning objectives from Level
        if (lesson.getClassChapter().getClazz().getSyllabus().getLevel().getLearningObjectives() != null
                && !lesson.getClassChapter().getClazz().getSyllabus().getLevel().getLearningObjectives().isBlank()) {
            context.append("- Learning Objective: ")
                    .append(lesson.getClassChapter().getClazz().getSyllabus().getLevel().getLearningObjectives())
                    .append("\n");
        }

        // Description from Level
        if (lesson.getClassChapter().getClazz().getSyllabus().getLevel().getDescription() != null
                && !lesson.getClassChapter().getClazz().getSyllabus().getLevel().getDescription().isBlank()) {
            context.append("- Level Description: ")
                    .append(lesson.getClassChapter().getClazz().getSyllabus().getLevel().getDescription())
                    .append("\n");
        }

        context.append("\n");

        return context.toString();
    }

    private SectionWithQuestionsDto generateSectionForQuestionType(
            DailyChallenge challenge,
            String questionType,
            int numberOfQuestions,
            String userDescription,
            String contextInfo,
            int orderNumber) {

        // 1. Build prompt
        String prompt = buildPrompt(challenge, questionType, numberOfQuestions, userDescription, contextInfo);

        // 2. Call OpenAI
        String aiResponse = callOpenAI(prompt);

        // 3. Parse response (NO DB SAVE - just return DTO with null IDs)
        SectionWithQuestionsDto result = parseResponse(aiResponse, questionType);
        result.getSection().setOrderNumber(orderNumber);

        // Set IDs to null (not saved to DB)
        result.getSection().setId(null);
        for (QuestionDto question : result.getQuestions()) {
            question.setId(null);
        }

        return result;
    }

    private String buildPrompt(DailyChallenge challenge, String questionType, int numberOfQuestions,
                               String userDescription, String contextInfo) {
        StringBuilder prompt = new StringBuilder();

        String syllabusLevel = challenge.getClassLesson().getClassChapter().getClazz()
                .getSyllabus().getLevel().getLevelName();

        prompt.append("You are an expert English teacher creating exercises.\n\n");

        // USER DESCRIPTION = PRIORITY
        prompt.append("═══════════════════════════════════════════════════════\n");
        prompt.append("🔥 USER REQUIREMENTS (ABSOLUTE PRIORITY) 🔥\n");
        prompt.append("═══════════════════════════════════════════════════════\n");
        prompt.append(userDescription).append("\n");
        prompt.append("═══════════════════════════════════════════════════════\n\n");

        prompt.append("⚠️ CRITICAL:\n");
        prompt.append("- Follow user's requirements EXACTLY\n");
        prompt.append("- If user specifies a level, USE THAT LEVEL (ignore syllabus)\n");
        prompt.append("- Context below is REFERENCE ONLY\n\n");

        // Context
        prompt.append("CONTEXT (Reference Only):\n");
        prompt.append(contextInfo).append("\n");

        // Task
        prompt.append("TASK:\n");
        prompt.append("Generate EXACTLY ").append(numberOfQuestions)
                .append(" questions of type: ").append(questionType).append("\n\n");

        // Format
        prompt.append("CRITICAL JSON FORMAT - You MUST return this exact structure:\n");
        prompt.append("{\n");
        prompt.append("  \"section\": {\n");
        prompt.append("    \"sectionTitle\": \"string (required)\",\n");
        prompt.append("    \"sectionsContent\": null,\n");
        prompt.append("    \"orderNumber\": 1,\n");
        prompt.append("    \"resourceType\": \"NONE\"\n");
        prompt.append("  },\n");
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
        prompt.append("            \"isCorrect\": true or false (required),\n");
        prompt.append("            \"positionId\": \"string or null\"\n");
        prompt.append("          }\n");
        prompt.append("        ]\n");
        prompt.append("      }\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");

        // Question type rules
        appendQuestionTypeRules(prompt, questionType);

        // Final reminders
        prompt.append("\n🔥 ABSOLUTE REQUIREMENTS:\n");
        prompt.append("1. Return ONLY valid JSON - no markdown, no explanations, no code blocks\n");
        prompt.append("2. ALL fields marked 'required' MUST be present\n");
        prompt.append("3. Generate EXACTLY ").append(numberOfQuestions).append(" questions\n");
        prompt.append("4. All questions type: ").append(questionType).append("\n");
        prompt.append("5. For FILL_IN_THE_BLANK, DROPDOWN, DRAG_AND_DROP: Use [[pos_xxxxx]]\n");
        prompt.append("6. sectionTitle MUST be a descriptive string, not null\n");
        prompt.append("7. Every question MUST have: questionText, orderNumber, score, questionType, content\n");
        prompt.append("8. Every data item MUST have: id, value, isCorrect\n");

        return prompt.toString();
    }

    private String getQuestionTypeTitle(String questionType) {
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
        prompt.append("SPECIFIC RULES FOR ").append(questionType).append(":\n");

        switch (questionType) {
            case "MULTIPLE_CHOICE":
                prompt.append("- 4 options per question\n");
                prompt.append("- Exactly 1 option with isCorrect=true\n");
                prompt.append("- positionId=null for all options\n");
                prompt.append("Example:\n");
                prompt.append("{\n");
                prompt.append("  \"questionText\": \"She _____ to school every day.\",\n");
                prompt.append("  \"orderNumber\": 1,\n");
                prompt.append("  \"score\": 1.0,\n");
                prompt.append("  \"questionType\": \"MULTIPLE_CHOICE\",\n");
                prompt.append("  \"content\": {\n");
                prompt.append("    \"data\": [\n");
                prompt.append("      {\"id\": \"opt1\", \"value\": \"go\", \"isCorrect\": false, \"positionId\": null},\n");
                prompt.append("      {\"id\": \"opt2\", \"value\": \"goes\", \"isCorrect\": true, \"positionId\": null},\n");
                prompt.append("      {\"id\": \"opt3\", \"value\": \"going\", \"isCorrect\": false, \"positionId\": null},\n");
                prompt.append("      {\"id\": \"opt4\", \"value\": \"went\", \"isCorrect\": false, \"positionId\": null}\n");
                prompt.append("    ]\n");
                prompt.append("  }\n");
                prompt.append("}\n");
                break;

            case "TRUE_OR_FALSE":
                prompt.append("- 2 options (True/False), 1 with isCorrect=true\n");
                prompt.append("- positionId=null\n");
                break;

            case "FILL_IN_THE_BLANK":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- Use [[pos_xxxxx]] in questionText (e.g., [[pos_a7k3m2]])\n");
                prompt.append("- Generate random 6-char IDs using lowercase a-z and 0-9\n");
                prompt.append("- positionId in data must match the xxxxx part\n");
                prompt.append("- Each ID must be UNIQUE\n");
                break;

            case "DROPDOWN":
                prompt.append("⚠️ CRITICAL FORMAT:\n");
                prompt.append("- Use [[pos_xxxxx]] in questionText\n");
                prompt.append("- 3-4 options, 1 with isCorrect=true\n");
                prompt.append("- All options share same positionId\n");
                break;

            case "MULTIPLE_SELECT":
                prompt.append("- 4-6 options, 2-3 with isCorrect=true\n");
                prompt.append("- positionId=null\n");
                break;

            case "DRAG_AND_DROP":
                prompt.append("⚠️ CRITICAL:\n");
                prompt.append("- Use [[pos_xxxxx]] for drop zones\n");
                prompt.append("- Items with positionId = correct placement\n");
                break;

            case "REARRANGE":
                prompt.append("- positionId = correct order: \"1\", \"2\", \"3\"\n");
                prompt.append("- All items isCorrect=true\n");
                break;

            default:
                prompt.append("Follow standard format\n");
        }
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

    private SectionWithQuestionsDto parseResponse(String jsonResponse, String questionType) {
        try {
            log.info("Parsing JSON response for type: {}", questionType);
            log.debug("Raw JSON: {}", jsonResponse);

            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            // Parse section with null-safe checks
            JsonNode sectionNode = rootNode.get("section");
            if (sectionNode == null) {
                log.error("Missing 'section' field in response");
                throw new RuntimeException("Invalid response: missing 'section' field");
            }

            SectionDto section = new SectionDto();

            // sectionTitle - REQUIRED
            JsonNode titleNode = sectionNode.get("sectionTitle");
            if (titleNode == null || titleNode.isNull()) {
                log.warn("Missing sectionTitle, using default");
                section.setSectionTitle(getQuestionTypeTitle(questionType));
            } else {
                section.setSectionTitle(titleNode.asText());
            }

            section.setSectionsContent(null);

            // orderNumber
            JsonNode orderNode = sectionNode.get("orderNumber");
            section.setOrderNumber(orderNode != null ? orderNode.asInt() : 1);

            // resourceType
            JsonNode resourceTypeNode = sectionNode.get("resourceType");
            section.setResourceType(resourceTypeNode != null ? resourceTypeNode.asText() : "NONE");

            // Parse questions
            List<QuestionDto> questions = new ArrayList<>();
            JsonNode questionsNode = rootNode.get("questions");

            if (questionsNode == null || !questionsNode.isArray()) {
                log.error("Missing or invalid 'questions' field");
                throw new RuntimeException("Invalid response: missing or invalid 'questions' array");
            }

            int questionIndex = 0;
            for (JsonNode questionNode : questionsNode) {
                questionIndex++;
                try {
                    QuestionDto question = parseQuestion(questionNode, questionIndex, questionType);
                    questions.add(question);
                } catch (Exception e) {
                    log.error("Error parsing question {}: {}", questionIndex, e.getMessage());
                    throw new RuntimeException("Failed to parse question " + questionIndex + ": " + e.getMessage(), e);
                }
            }

            if (questions.isEmpty()) {
                throw new RuntimeException("No questions were parsed from response");
            }

            log.info("Successfully parsed {} questions", questions.size());

            // Ensure unique position IDs
            ensureUniquePositionIds(questions);

            return new SectionWithQuestionsDto(section, questions);

        } catch (Exception e) {
            log.error("Error parsing response: {}", e.getMessage(), e);
            log.error("Problematic JSON: {}", jsonResponse);
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage(), e);
        }
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

}