package com.learning.progress.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.dto.ai.ContentAssessmentResult;
import com.learning.progress.dto.ai.*;
import com.learning.progress.entity.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.SubmissionQuestionRepository;
import com.learning.progress.service.AiFeedbackService;
import com.learning.progress.service.OpenAiService;
import com.learning.progress.util.TraceUtil;
import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriComponentsBuilder;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AiFeedbackServiceImpl implements AiFeedbackService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SubmissionQuestionRepository submissionQuestionRepository;

    private RestTemplate restTemplate;

    @Value("${azure.openai.endpoint}")
    private String endpoint;

    @Value("${azure.openai.api-key}")
    private String apiKey;

    private static final String API_VERSION = "2025-04-01-preview";

    private static final String SYSTEM_ROLE_JSON_INSTRUCTION =
            "You are an expert English teacher. Return ONLY valid JSON (no markdown, no comments, no extra text). " +
                    "Do NOT include trailing commas or non-standard JSON syntax.";


    @Value("${azure.speech.key}")
    private String speechKey;

    @Value("${azure.speech.region}")
    private String speechRegion;

    private final OpenAiService openAiService;

    public AiFeedbackServiceImpl(OpenAiService openAiService, SubmissionQuestionRepository submissionQuestionRepository, RestTemplate restTemplate) {
        this.submissionQuestionRepository = submissionQuestionRepository;
        this.openAiService = openAiService;
        this.restTemplate = restTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public GradingWritingResponse gradeWriting(GradingWritingRequest request) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Starting AI grading for submissionQuestionId: {}", traceId, request.getSubmissionQuestionId());

        // 1. Load submission question
        SubmissionQuestion submissionQuestion = submissionQuestionRepository
                .findById(request.getSubmissionQuestionId())
                .orElseThrow(() -> new ApiException("Submission question not found", HttpStatus.NOT_FOUND.value()));

        // 2. Extract ALL content (text + images)
        List<String> rawContents = extractAllWritingContent(submissionQuestion.getSubmissionContentJson());
        if (rawContents.isEmpty()) {
            throw new ApiException("No writing content found in submission", HttpStatus.BAD_REQUEST.value());
        }

        // 3. Separate images and text
        List<String> imageUrls = new ArrayList<>();
        List<String> textContents = new ArrayList<>();

        for (String content : rawContents) {
            if (isImageUrl(content)) {
                imageUrls.add(content);
            } else if (content != null && !content.trim().isEmpty()) {
                textContents.add(content);
            }
        }

        // 4. Process all images with OCR
        if (!imageUrls.isEmpty()) {
            log.info("[{}] Detected {} images, performing OCR on all...", traceId, imageUrls.size());
            String extractedText = extractAllImagesText(imageUrls, traceId);
            if (extractedText != null && !extractedText.trim().isEmpty()) {
                textContents.add(extractedText);
            }
        }

        // 5. Combine all text
        String studentWriting = String.join("\n\n", textContents);
        if (studentWriting.trim().isEmpty()) {
            throw new ApiException("No text could be extracted from submission", HttpStatus.BAD_REQUEST.value());
        }

        log.info("[{}] Successfully extracted text (from {} images + {} text blocks): {}",
                traceId, imageUrls.size(), textContents.size() - (imageUrls.isEmpty() ? 0 : 1), studentWriting);

        // 6. Load context
        Question question = submissionQuestion.getQuestion();
        ChallengeSection section = question.getSection();
        DailyChallenge challenge = section.getChallenge();
        OpenAiServiceImpl.ChallengeContext context = eagerLoadChallengeContext(challenge);

        // 7. Build prompt
        String prompt = buildWritingGradingPrompt(context, question.getQuestionText(), studentWriting);

        // 8. Call OpenAI with retry
        String aiResponse = null;
        int maxRetries = 3;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("[{}] Calling OpenAI (attempt {}/{}) ...", traceId, attempt, maxRetries);
                aiResponse = openAiService.callOpenAI(prompt);
                break;
            } catch (Exception e) {
                lastException = e;
                log.warn("[{}] OpenAI call failed on attempt {}/{}: {}", traceId, attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        long backoff = 1000L * attempt;
                        log.info("[{}] Retrying after {} ms...", traceId, backoff);
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ApiException("OpenAI call interrupted", HttpStatus.INTERNAL_SERVER_ERROR.value());
                    }
                } else {
                    log.error("[{}] All {} retry attempts failed.", traceId, maxRetries);
                }
            }
        }

        if (aiResponse == null) {
            throw new ApiException("Failed to get AI response after " + maxRetries + " attempts: "
                    + (lastException != null ? lastException.getMessage() : "unknown error"),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        // 9. Parse response
        GradingWritingResponse result = parseGradingResponse(aiResponse, studentWriting);
        log.info("[{}] Successfully graded writing. Overall score: {}", traceId, result.getSuggestedScore());

        return result;
    }

    public OpenAiServiceImpl.ChallengeContext eagerLoadChallengeContext(DailyChallenge challenge) {
        OpenAiServiceImpl.ChallengeContext context = new OpenAiServiceImpl.ChallengeContext();

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

    /**
     * ✅ NEW: Extract ALL writing content (both text and image URLs)
     */
    private List<String> extractAllWritingContent(Map<String, Object> submissionContentJson) {
        List<String> allContent = new ArrayList<>();

        try {
            Object dataObj = submissionContentJson.get("data");
            if (dataObj instanceof List<?> dataList) {
                for (Object item : dataList) {
                    if (item instanceof Map<?, ?> itemMap) {
                        Object value = itemMap.get("value");
                        if (value != null && !value.toString().trim().isEmpty()) {
                            allContent.add(value.toString().trim());
                        }
                    }
                }
            }

            return allContent;

        } catch (Exception e) {
            log.error("Failed to extract writing content: {}", e.getMessage());
            throw new ApiException("Invalid submission content format: " + e.getMessage(),
                    HttpStatus.BAD_REQUEST.value());
        }
    }

    /**
     * ✅ NEW: Extract text from multiple images
     */
    private String extractAllImagesText(List<String> imageUrls, String traceId) {
        List<String> extractedTexts = new ArrayList<>();

        for (int i = 0; i < imageUrls.size(); i++) {
            String imageUrl = imageUrls.get(i);
            log.info("[{}] Processing image {}/{}: {}", traceId, i + 1, imageUrls.size(), imageUrl);

            try {
                String extractedText = extractTextFromImageUrl(imageUrl, traceId);
                if (extractedText != null && !extractedText.trim().isEmpty()) {
                    extractedTexts.add(extractedText);
                    log.info("[{}] Successfully extracted text from image {}/{}", traceId, i + 1, imageUrls.size());
                }
            } catch (Exception e) {
                log.error("[{}] Failed to extract text from image {}/{}: {}",
                        traceId, i + 1, imageUrls.size(), e.getMessage());
                // Continue with other images instead of failing completely
            }
        }

        // Join all extracted texts with double newline
        String combinedText = String.join("\n\n", extractedTexts);
        log.info("[{}] Combined text from {} images: {}", traceId, extractedTexts.size(), combinedText);

        return combinedText;
    }

    /**
     * Check if the content is an image URL
     */
    private boolean isImageUrl(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        String lowerContent = content.toLowerCase().trim();

        // Check if it's a URL
        if (!lowerContent.startsWith("http://") && !lowerContent.startsWith("https://")) {
            return false;
        }

        // Check if it's an image file extension or blob storage pattern
        return lowerContent.contains(".jpg")
                || lowerContent.contains(".jpeg")
                || lowerContent.contains(".png")
                || lowerContent.contains(".gif")
                || lowerContent.contains(".webp")
                || lowerContent.contains(".bmp")
                || (lowerContent.contains("blob.core.windows.net") && !lowerContent.contains(".webm") && !lowerContent.contains(".mp3") && !lowerContent.contains(".mp4"));
    }

    /**
     * Extract text from image URL using OCR
     */
    private String extractTextFromImageUrl(String imageUrl, String traceId) {
        File imageFile = null;

        try {
            // 1. Download image
            log.info("[{}] Downloading image from URL...", traceId);
            imageFile = downloadImageFromUrl(imageUrl);

            // 2. Extract text using OpenAI Vision
            log.info("[{}] Extracting text from image using OCR...", traceId);

            return extractTextFromImage(imageFile);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] Failed to extract text from image URL: {}", traceId, e.getMessage(), e);
            throw new ApiException("Failed to extract text from image: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        } finally {
            // Cleanup temp file
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
    private File downloadImageFromUrl(String imageUrl) {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");

            // Determine file extension from URL
            String extension = ".jpg"; // default
            String lowerUrl = imageUrl.toLowerCase();
            if (lowerUrl.contains(".png")) extension = ".png";
            else if (lowerUrl.contains(".jpeg")) extension = ".jpeg";
            else if (lowerUrl.contains(".gif")) extension = ".gif";
            else if (lowerUrl.contains(".webp")) extension = ".webp";
            else if (lowerUrl.contains(".bmp")) extension = ".bmp";

            String filename = "handwriting_" + UUID.randomUUID() + extension;
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
            throw new ApiException("Failed to download image from URL: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * Extract text from handwritten image using OpenAI Vision API
     * Returns JSON response with status and extracted text
     */
    private String extractTextFromImage(File imageFile) {
        try {
            // Convert image to base64
            String base64Image = convertImageToBase64(imageFile);

            // Build OCR prompt
            String prompt = buildOCRPrompt();

            // Call OpenAI Vision API with retry logic
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
                            long backoff = 1000L * attempt;
                            Thread.sleep(backoff);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new ApiException("OCR retry interrupted",
                                    HttpStatus.INTERNAL_SERVER_ERROR.value());
                        }
                    }
                }
            }

            if (extractedText == null) {
                throw new ApiException("Failed to extract text after " + maxRetries + " attempts: "
                        + (lastException != null ? lastException.getMessage() : "unknown error"),
                        HttpStatus.INTERNAL_SERVER_ERROR.value());
            }

            // Parse JSON response from AI
            return parseOCRResponse(extractedText.trim());

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to extract text from image: {}", e.getMessage(), e);
            throw new ApiException("Failed to extract text from image: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
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
            throw new ApiException("Failed to call Azure OpenAI Vision: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        throw new ApiException("No response from Azure OpenAI Vision",
                HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    /**
     * Parse OCR JSON response and handle different status codes
     */
    private String parseOCRResponse(String jsonResponse) {
        try {
            String cleaned = cleanJsonResponse(jsonResponse);
            JsonNode root = objectMapper.readTree(cleaned);

            String status = root.hasNonNull("status") ? root.get("status").asText() : "UNKNOWN";

            switch (status) {
                case "SUCCESS":
                    // Trả về text nguyên văn, giữ nguyên mọi lỗi chính tả, ngữ pháp
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
                    throw new ApiException("Lỗi OCR: Trạng thái không xác định - " + status, HttpStatus.INTERNAL_SERVER_ERROR.value());
            }

        } catch (ApiException e) {
            // Re-throw ApiException as is
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse OCR response: {}", e.getMessage(), e);
            throw new ApiException("Không thể xử lý kết quả OCR: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
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

    /**
     * Build prompt for OCR extraction - instructs AI to return JSON
     */
    private String buildOCRPrompt() {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert OCR system specialized in reading handwritten English text.\n\n");

        prompt.append("CRITICAL: You MUST respond with ONLY valid JSON in this exact format, no markdown, no extra text:\n\n");
        prompt.append("{\n");
        prompt.append("  \"status\": \"SUCCESS\" | \"ILLEGIBLE_HANDWRITING\" | \"NO_TEXT_FOUND\" | \"BLANK_IMAGE\",\n");
        prompt.append("  \"text\": \"extracted text here (only if status is SUCCESS)\"\n");
        prompt.append("}\n\n");

        prompt.append("TASK: Extract ALL text from the handwritten image with high accuracy.\n\n");

        prompt.append("DECISION RULES:\n");
        prompt.append("1. If the image is blank or contains no content:\n");
        prompt.append("   → Return: {\"status\": \"BLANK_IMAGE\"}\n\n");

        prompt.append("2. If the image has no text or is not a handwriting sample:\n");
        prompt.append("   → Return: {\"status\": \"NO_TEXT_FOUND\"}\n\n");

        prompt.append("3. If the handwriting is too messy, unclear, or illegible (you can't confidently read at least 70% of the text):\n");
        prompt.append("   → Return: {\"status\": \"ILLEGIBLE_HANDWRITING\"}\n\n");

        prompt.append("4. If the handwriting is readable (even if not perfect):\n");
        prompt.append("   → Return: {\"status\": \"SUCCESS\", \"text\": \"exact transcription\"}\n\n");

        prompt.append("TRANSCRIPTION RULES (when status is SUCCESS):\n");
        prompt.append("- Transcribe EXACTLY what is written, character by character\n");
        prompt.append("- DO NOT correct any spelling mistakes\n");
        prompt.append("- DO NOT correct any grammar errors\n");
        prompt.append("- DO NOT add punctuation that isn't in the original\n");
        prompt.append("- DO NOT add or remove spaces\n");
        prompt.append("- Preserve line breaks with \\n\n");
        prompt.append("- Preserve paragraph structure\n");
        prompt.append("- If a word is unclear but readable, transcribe your best interpretation\n");
        prompt.append("- Keep everything as written, including mistakes\n\n");

        prompt.append("EXAMPLES OF ILLEGIBLE:\n");
        prompt.append("- Extremely messy scribbles where most words are unreadable\n");
        prompt.append("- Blurry or low-quality images where text cannot be distinguished\n");
        prompt.append("- Overlapping text that makes it impossible to separate words\n");
        prompt.append("- Handwriting so poor that fewer than 70% of words can be confidently identified\n\n");

        prompt.append("REMEMBER: Return ONLY the JSON object, nothing else. No markdown code blocks, no explanations.\n");

        prompt.append("IMPORTANT: You MUST respond with ONLY valid JSON in this exact format, no markdown, no extra text:\n\n");
        prompt.append("{\n");
        prompt.append("  \"status\": \"SUCCESS\" | \"ILLEGIBLE_HANDWRITING\" | \"NO_TEXT_FOUND\" | \"BLANK_IMAGE\",\n");
        prompt.append("  \"text\": \"extracted text here (only if status is SUCCESS)\"\n");
        prompt.append("}\n\n");
        return prompt.toString();
    }

    /**
     * Convert image file to base64 string
     */
    private String convertImageToBase64(File imageFile) {
        try (FileInputStream fis = new FileInputStream(imageFile)) {
            byte[] imageBytes = fis.readAllBytes();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            throw new ApiException("Failed to convert image to base64: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    // Build grading prompt
    private String buildWritingGradingPrompt(
            OpenAiServiceImpl.ChallengeContext context,
            String questionText,
            String studentWriting) {

        StringBuilder prompt = new StringBuilder();

        prompt.append(SYSTEM_ROLE_JSON_INSTRUCTION).append("\n\n");

        prompt.append("IMPORTANT: All human-readable feedback content MUST be written in Vietnamese. ")
                .append("JSON field names remain in English. ")
                .append("Return ONLY valid JSON, no markdown, no explanations, no extra text.\n\n");

        prompt.append("You are an experienced English writing teacher following IELTS criteria.\n\n");

        prompt.append("IMPORTANT: All feedback content MUST be in Vietnamese with HTML formatting in one line.\n");
        prompt.append("Use these HTML tags: <h4><strong>Header</strong></h4>, <p>text</p>, <ul><li>item</li></ul>, <strong>text</strong>\n\n");

        prompt.append("Context: Chapter: ").append(context.classChapterName)
                .append(" | Level: ").append(context.studentLevel).append("\n\n");

        prompt.append("TASK: Analyze the writing and return JSON with:\n\n");

        prompt.append("1. overallFeedback: Đánh giá tổng quan theo 3 bước (HTML format)(100-200 words):\n");
        prompt.append("   STEP 1 - <h4><strong>📋 Nhận xét chung</strong></h4><p>Tổng quan về bài viết </p>\n");
        prompt.append("   STEP 2 - <h4><strong>⚠️ Lỗi sai/Cần cải thiện</strong></h4><ul><li>Vấn đề chính 1</li><li>Vấn đề chính 2</li></ul>\n");
        prompt.append("   STEP 3 - <h4><strong>💡 Cách cải thiện</strong></h4><ul><li>Gợi ý 1</li><li>Gợi ý 2</li></ul>\n\n");

        prompt.append("2. suggestedScore: Overall score (0.0-10.0)\n\n");

        prompt.append("3. criteriaFeedback: 4 IELTS criteria (taskResponse, cohesionCoherence, lexicalResource, grammaticalRangeAccuracy)\n");
        prompt.append("   Each criterion MUST have:\n");
        prompt.append("   - score: 0-10\n");
        prompt.append("   - feedback: Vietnamese text with HTML formatting following EXACTLY 3 steps(100-200 words):\n");
        prompt.append("     STEP 1 - <h4><strong>📋 Nhận xét chung</strong></h4><p>Đánh giá tổng quan </p>\n");
        prompt.append("     STEP 2 - <h4><strong>⚠️ Lỗi sai/Cần cải thiện</strong></h4><ul><li>Vấn đề 1</li><li>Vấn đề 2</li></ul>\n");
        prompt.append("     STEP 3 - <h4><strong>💡 Cách cải thiện</strong></h4><ul><li>Gợi ý 1</li><li>Gợi ý 2</li></ul>\n\n");

        prompt.append("4. comments: Inline comments (startIndex, endIndex, commentText, severity, category, correction)\n\n");

        prompt.append("WRITING TASK:\n").append(questionText).append("\n\n");
        prompt.append("STUDENT'S WRITING:\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        prompt.append(studentWriting).append("\n");
        prompt.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        prompt.append("OUTPUT EXAMPLE:\n");
        prompt.append("{\n");
        prompt.append("  \"overallFeedback\": \"<h4><strong>📋 Nhận xét chung</strong></h4><p>Bài viết của bạn có cấu trúc rõ ràng và đã trả lời được yêu cầu đề bài. Tuy nhiên, còn một số điểm cần cải thiện về từ vựng và ngữ pháp.</p><h4><strong>⚠️ Lỗi sai/Cần cải thiện</strong></h4><ul><li>Thiếu ví dụ cụ thể để minh họa ý kiến</li><li>Một số lỗi ngữ pháp cơ bản ảnh hưởng đến ý nghĩa</li><li>Từ vựng còn đơn giản, chưa đa dạng</li></ul><h4><strong>💡 Cách cải thiện</strong></h4><ul><li>Thêm 1-2 ví dụ thực tế cho mỗi luận điểm chính</li><li>Ôn lại các thì cơ bản và cấu trúc câu phức</li><li>Học thêm từ vựng học thuật liên quan đến chủ đề</li></ul>\",\n");
        prompt.append("  \"suggestedScore\": 7.5,\n");
        prompt.append("  \"criteriaFeedback\": {\n");
        prompt.append("    \"taskResponse\": {\n");
        prompt.append("      \"score\": 7.0,\n");
        prompt.append("      \"feedback\": \"<h4><strong>📋 Nhận xét chung</strong></h4><p>Bài viết trả lời được câu hỏi nhưng còn thiếu chi tiết ở một số phần.</p><h4><strong>⚠️ Lỗi sai/Cần cải thiện</strong></h4><ul><li>Thiếu ví dụ cụ thể</li><li>Chưa phân tích sâu</li></ul><h4><strong>💡 Cách cải thiện</strong></h4><ul><li>Thêm ví dụ thực tế</li><li>Giải thích rõ hơn từng ý</li></ul>\"\n");
        prompt.append("    },\n");
        prompt.append("    \"cohesionCoherence\": {\"score\": 7.5, \"feedback\": \"...(3 steps)\"},\n");
        prompt.append("    \"lexicalResource\": {\"score\": 7.0, \"feedback\": \"...(3 steps)\"},\n");
        prompt.append("    \"grammaticalRangeAccuracy\": {\"score\": 6.5, \"feedback\": \"...(3 steps)\"}\n");
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
        prompt.append("}\n\n");

        prompt.append("CRITICAL: Both overallFeedback and ALL criteriaFeedback MUST follow the exact 3-step structure with proper HTML tags and icons. Do not skip any step.\n");

        return prompt.toString();
    }


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

            int maxKeep = 20;
            if (comments.size() > maxKeep) {
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
            throw new ApiException("Failed to parse AI grading response: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
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

    @Override
    public PronunciationAssessmentResponse assessPronunciation(PronunciationAssessmentRequest request, String questionText) {
        String traceId = TraceUtil.getTraceId();
        log.info("[{}] Starting pronunciation assessment", traceId);

        if (request.getAudioUrl() == null || request.getAudioUrl().trim().isEmpty()) {
            throw new ApiException("Audio URL is required", HttpStatus.BAD_REQUEST.value());
        }

        // Retry logic - max 3 attempts
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
                            response = assessFreeForm(wavFile, questionText);
                        }

                        log.info("[{}] Assessment completed on attempt {}. Score: {}",
                                traceId, attempt, response.getPronunciationScore());

                        // Set feedback to null - không cần feedback cho speaking
                        response.setFeedback(null);
                        response.setPronunciationScore(roundToOneDecimal(response.getPronunciationScore() / 10));
                        response.setAccuracyScore(roundToOneDecimal(response.getAccuracyScore() / 10));
                        response.setFluencyScore(roundToOneDecimal(response.getFluencyScore() / 10));
                        response.setCompletenessScore(roundToOneDecimal(response.getCompletenessScore() / 10));
                        response.setProsodyScore(roundToOneDecimal(response.getProsodyScore() / 10));
                        response.setWords(response.getWords());
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

            } catch (Exception e) {
                // All other exceptions - check if retryable
                String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                boolean isRetryable = message.contains("timeout")
                        || message.contains("network")
                        || message.contains("connection")
                        || message.contains("parse")
                        || message.contains("json")
                        || message.contains("recognition failed")
                        || message.contains("speech service")
                        || message.contains("io error")
                        || message.contains("socket");

                if (isRetryable) {
                    lastException = e;
                    log.warn("[{}] Retryable error on attempt {}/{}: {}",
                            traceId, attempt, maxAttempts, e.getMessage());

                    if (attempt < maxAttempts) {
                        log.info("[{}] Retrying pronunciation assessment after {}s delay...", traceId, attempt);
                        try {
                            Thread.sleep(1000L * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new ApiException("Assessment retry interrupted",
                                    HttpStatus.INTERNAL_SERVER_ERROR.value());
                        }
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
                        lastException != null ? lastException.getMessage() : "unknown error"),
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

        validateEnglishOnly(result.getFullText());

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

        return PronunciationAssessmentResponse.builder()
                .pronunciationScore(avgPronunciation)
                .accuracyScore(avgAccuracy)
                .fluencyScore(avgFluency)
                .completenessScore(avgCompleteness)
                .prosodyScore(avgProsody)
                .recognizedText(result.getFullText())
                .referenceText(referenceText)
                .words(allWords)
                .feedback(null) // No feedback for speaking
                .build();
    }

    /**
     * Assess free-form WITH GPT grammar correction AND content assessment
     */
    private PronunciationAssessmentResponse assessFreeForm(File wavFile, String questionText) throws Exception {
        String traceId = TraceUtil.getTraceId();

        // Step 1: Perform speech recognition
        log.info("[{}] Step 1: Performing speech recognition...", traceId);
        SpeechAnalysisResult analysis = performDetailedSpeechRecognition(wavFile);

        if (analysis.getRecognizedText() == null || analysis.getRecognizedText().trim().isEmpty()) {
            throw new ApiException("No speech could be recognized from the audio file",
                    HttpStatus.BAD_REQUEST.value());
        }

        validateEnglishOnly(analysis.getRecognizedText());

        // Step 2: Use GPT to correct grammar and create reference text
        log.info("[{}] Step 2: Correcting grammar with GPT...", traceId);
        String correctedText = correctGrammarWithGPT(analysis.getRecognizedText());

        // Step 3: Re-run Azure Pronunciation Assessment with corrected reference
        log.info("[{}] Step 3: Running pronunciation assessment with corrected reference...", traceId);
        PronunciationAssessmentResponse technicalAssessment = assessWithCorrectedReference(
                wavFile,
                correctedText,
                analysis.getRecognizedText()
        );

        // Step 4: ✅ NEW - Assess content quality if questionText provided
        if (questionText != null && !questionText.trim().isEmpty()) {
            log.info("[{}] Step 4: Assessing content quality against question...", traceId);
            ContentAssessmentResult contentAssessment = assessContentQuality(
                    analysis.getRecognizedText(),
                    questionText
            );

            // Step 5: Merge technical + content assessments
            log.info("[{}] Step 5: Merging technical and content assessments...", traceId);
            return mergeAssessments(technicalAssessment, contentAssessment, questionText);
        }

        // No questionText → return technical assessment only
        return technicalAssessment;
    }

    /**
     * ✅ NEW: Assess content quality against the question
     */
    private ContentAssessmentResult assessContentQuality(String recognizedText, String questionText) {
        try {
            String prompt = buildContentAssessmentPrompt(recognizedText, questionText);

            // Call OpenAI with retry logic
            String aiResponse = null;
            int maxRetries = 3;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    log.info("Calling OpenAI for content assessment (attempt {}/{})...", attempt, maxRetries);
                    aiResponse = openAiService.callOpenAI(prompt);
                    break;
                } catch (Exception e) {
                    log.warn("Content assessment failed on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());

                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(1000L * attempt);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new ApiException("Content assessment interrupted",
                                    HttpStatus.INTERNAL_SERVER_ERROR.value());
                        }
                    }
                }
            }

            if (aiResponse == null) {
                log.warn("Failed to assess content after {} attempts, using default", maxRetries);
                return createDefaultContentAssessment();
            }

            return parseContentAssessmentResponse(aiResponse);

        } catch (Exception e) {
            log.error("Error in content assessment: {}", e.getMessage(), e);
            return createDefaultContentAssessment();
        }
    }

    /**
     * ✅ NEW: Build prompt for content assessment
     */
    private String buildContentAssessmentPrompt(String recognizedText, String questionText) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert English speaking assessment coach. ");
        prompt.append("Evaluate how well the student's spoken response answers the given question/topic.\n\n");

        prompt.append("QUESTION/TOPIC:\n");
        prompt.append(questionText).append("\n\n");

        prompt.append("STUDENT'S RESPONSE:\n");
        prompt.append(recognizedText).append("\n\n");

        prompt.append("ASSESSMENT TASK:\n");
        prompt.append("Evaluate the content quality on these criteria (0-10 scale):\n\n");

        prompt.append("1. **taskAchievementScore** (0-10): Did they answer the question?\n");
        prompt.append("   - 0-3: Completely off-topic or irrelevant\n");
        prompt.append("   - 4-6: Partially answers, missing key points\n");
        prompt.append("   - 7-8: Answers well, minor gaps\n");
        prompt.append("   - 9-10: Fully addresses all aspects\n\n");

        prompt.append("2. **contentQualityScore** (0-10): Is the content detailed and well-developed?\n");
        prompt.append("   - 0-3: Very brief, lacks detail\n");
        prompt.append("   - 4-6: Some details but underdeveloped\n");
        prompt.append("   - 7-8: Good details and examples\n");
        prompt.append("   - 9-10: Rich, well-developed content\n\n");

        prompt.append("3. **relevanceScore** (0-10): Is everything said relevant to the topic?\n");
        prompt.append("   - 0-3: Mostly irrelevant or confused\n");
        prompt.append("   - 4-6: Some relevant, some off-track\n");
        prompt.append("   - 7-8: Mostly relevant\n");
        prompt.append("   - 9-10: Everything is on-topic\n\n");

        prompt.append("4. **coherenceScore** (0-10): Is the response logical and organized?\n");
        prompt.append("   - 0-3: Disorganized, hard to follow\n");
        prompt.append("   - 4-6: Some organization, but jumpy\n");
        prompt.append("   - 7-8: Well-organized, clear flow\n");
        prompt.append("   - 9-10: Excellent structure and logic\n\n");

        prompt.append("Return ONLY valid JSON (no markdown, no extra text):\n");
        prompt.append("{\n");
        prompt.append("  \"taskAchievementScore\": 8.0,\n");
        prompt.append("  \"contentQualityScore\": 7.5,\n");
        prompt.append("  \"relevanceScore\": 9.0,\n");
        prompt.append("  \"coherenceScore\": 8.5\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * ✅ NEW: Parse content assessment response
     */
    private ContentAssessmentResult parseContentAssessmentResponse(String jsonResponse) {
        try {
            String cleaned = cleanJsonResponse(jsonResponse);
            JsonNode root = objectMapper.readTree(cleaned);

            double taskAchievementScore = root.has("taskAchievementScore")
                    ? root.get("taskAchievementScore").asDouble() : 0.0;
            double contentQualityScore = root.has("contentQualityScore")
                    ? root.get("contentQualityScore").asDouble() : 0.0;
            double relevanceScore = root.has("relevanceScore")
                    ? root.get("relevanceScore").asDouble() : 0.0;
            double coherenceScore = root.has("coherenceScore")
                    ? root.get("coherenceScore").asDouble() : 0.0;

            // Clamp scores
            taskAchievementScore = clampScore(taskAchievementScore);
            contentQualityScore = clampScore(contentQualityScore);
            relevanceScore = clampScore(relevanceScore);
            coherenceScore = clampScore(coherenceScore);

            return ContentAssessmentResult.builder()
                    .taskAchievementScore(taskAchievementScore)
                    .contentQualityScore(contentQualityScore)
                    .relevanceScore(relevanceScore)
                    .coherenceScore(coherenceScore)
                    .contentFeedback(null) // No feedback
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse content assessment: {}", e.getMessage());
            return createDefaultContentAssessment();
        }
    }

    /**
     * ✅ NEW: Create default content assessment on failure
     */
    private ContentAssessmentResult createDefaultContentAssessment() {
        return ContentAssessmentResult.builder()
                .taskAchievementScore(5.0)
                .contentQualityScore(5.0)
                .relevanceScore(5.0)
                .coherenceScore(5.0)
                .contentFeedback(null)
                .build();
    }

    /**
     * ✅ NEW: Merge technical and content assessments
     */
    private PronunciationAssessmentResponse mergeAssessments(
            PronunciationAssessmentResponse technicalAssessment,
            ContentAssessmentResult contentAssessment,
            String questionText) {

        // Just return technical assessment without feedback
        technicalAssessment.setFeedback(null);
        return technicalAssessment;
    }

    /**
     * Correct grammar using GPT while preserving original meaning
     */
    private String correctGrammarWithGPT(String recognizedText) {
        try {
            String prompt = buildGrammarCorrectionPrompt(recognizedText);

            // Call OpenAI with retry logic
            String aiResponse = null;
            int maxRetries = 3;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    log.info("Calling OpenAI for grammar correction (attempt {}/{})...", attempt, maxRetries);
                    aiResponse = openAiService.callOpenAI(prompt);
                    break;
                } catch (Exception e) {
                    log.warn("Grammar correction failed on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());

                    if (attempt < maxRetries) {
                        try {
                            long backoff = 1000L * attempt;
                            Thread.sleep(backoff);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new ApiException("Grammar correction interrupted",
                                    HttpStatus.INTERNAL_SERVER_ERROR.value());
                        }
                    }
                }
            }

            if (aiResponse == null) {
                log.warn("Failed to correct grammar after {} attempts, using original text", maxRetries);
                return recognizedText; // Fallback to original
            }

            // Parse JSON response
            String corrected = parseGrammarCorrectionResponse(aiResponse);

            // Validate corrected text
            if (corrected == null || corrected.trim().isEmpty() || corrected.length() > recognizedText.length() * 2) {
                log.warn("Invalid corrected text, using original");
                return recognizedText;
            }

            log.info("Grammar corrected: '{}' → '{}'", recognizedText, corrected);
            return corrected;

        } catch (Exception e) {
            log.error("Error in grammar correction: {}", e.getMessage(), e);
            return recognizedText; // Fallback to original
        }
    }

    /**
     * Build prompt for grammar correction
     */
    private String buildGrammarCorrectionPrompt(String recognizedText) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert English grammar teacher. Your task is to correct grammatical errors in the student's speech while preserving the original meaning and speaking style as much as possible.\n\n");

        prompt.append("IMPORTANT RULES:\n");
        prompt.append("1. Fix ONLY grammar, spelling, and punctuation errors\n");
        prompt.append("2. DO NOT change the meaning or add new content\n");
        prompt.append("3. DO NOT make the sentence more formal or complex\n");
        prompt.append("4. Keep the same vocabulary level and speaking style\n");
        prompt.append("5. If the sentence is already grammatically correct, return it unchanged\n");
        prompt.append("6. Preserve contractions (don't → don't, not → do not)\n");
        prompt.append("7. Keep informal language if appropriate\n\n");

        prompt.append("EXAMPLES:\n");
        prompt.append("Input: \"I go to school yesterday\"\n");
        prompt.append("Output: \"I went to school yesterday\"\n\n");

        prompt.append("Input: \"She don't like apples\"\n");
        prompt.append("Output: \"She doesn't like apples\"\n\n");

        prompt.append("Input: \"They was very happy\"\n");
        prompt.append("Output: \"They were very happy\"\n\n");

        prompt.append("Input: \"I have three friend\"\n");
        prompt.append("Output: \"I have three friends\"\n\n");

        prompt.append("STUDENT'S SPEECH:\n");
        prompt.append(recognizedText).append("\n\n");

        prompt.append("Return ONLY valid JSON (no markdown, no extra text):\n");
        prompt.append("{\n");
        prompt.append("  \"correctedText\": \"The grammatically correct version\",\n");
        prompt.append("  \"hasChanges\": true,\n");
        prompt.append("  \"changes\": [\"went instead of go\", \"yesterday requires past tense\"]\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * Parse grammar correction response
     */
    private String parseGrammarCorrectionResponse(String jsonResponse) {
        try {
            String cleaned = cleanJsonResponse(jsonResponse);
            JsonNode root = objectMapper.readTree(cleaned);

            if (root.has("correctedText")) {
                return root.get("correctedText").asText().trim();
            }

            return null;

        } catch (Exception e) {
            log.error("Failed to parse grammar correction response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Assess pronunciation with corrected reference text
     */
    private PronunciationAssessmentResponse assessWithCorrectedReference(
            File wavFile,
            String correctedReferenceText,
            String originalRecognizedText) throws Exception {

        String traceId = TraceUtil.getTraceId();

        // Create request with corrected reference
        PronunciationAssessmentRequest request = new PronunciationAssessmentRequest();
        request.setReferenceText(correctedReferenceText);
        request.setGradingSystem("HundredMark");
        request.setGranularity("Word");
        request.setEnableMiscue(true);
        request.setEnableProsody(true);

        // Use the existing method for assessment with reference text
        PronunciationAssessmentResponse response = assessWithReferenceTextContinuous(wavFile, request);

        // No feedback needed
        response.setFeedback(null);

        log.info("[{}] Assessment with corrected reference completed. Score: {}",
                traceId, response.getPronunciationScore());

        return response;
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

    /**
     * Download audio file from Azure Blob URL
     */
    private File downloadFromBlobUrl(String blobUrl) {
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
            throw new ApiException("Failed to download audio from blob URL: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private File ensureWavFormat(File audioFile) {
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
            throw new ApiException("Failed to convert audio: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
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
        return switch (granularity.toLowerCase()) {
            case "word" -> PronunciationAssessmentGranularity.Word;
            case "fulltext" -> PronunciationAssessmentGranularity.FullText;
            default -> PronunciationAssessmentGranularity.Phoneme;
        };
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

            if (nBestArray != null && nBestArray.isArray() && !nBestArray.isEmpty()) {
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
            throw new ApiException("Failed to parse speech recognition results: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
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
     * Clamp score to 0-100 range
     */
    private double clampScore(double score) {
        if (Double.isNaN(score) || score < 0) return 0.0;
        if (score > 100) return 100.0;
        return score;
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

        // If more than 10% of letters are Vietnamese, reject
        if (totalLetters > 0) {
            double vietnameseRatio = (double) vietnameseCount / totalLetters;

            if (vietnameseRatio > 0.10) {
                log.warn("Detected Vietnamese content: {} Vietnamese chars out of {} total letters ({:.1f}%)",
                        vietnameseCount, totalLetters, vietnameseRatio * 100);
                throw new ApiException(
                        "This assessment only supports English pronunciation. Please provide English text or speech only.",
                        HttpStatus.BAD_REQUEST.value()
                );
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SseEmitter gradeWritingStream(GradingWritingRequest request) {
        return null;
    }
}