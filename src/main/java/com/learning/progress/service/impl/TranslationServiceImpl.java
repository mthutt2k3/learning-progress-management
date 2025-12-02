package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.dto.ai.*;
import com.learning.progress.exception.ApiException;
import com.learning.progress.service.TranslationService;
import com.learning.progress.util.TraceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class TranslationServiceImpl implements TranslationService {

    @Value("${azure.translator.endpoint}")
    private String translatorEndpoint;

    @Value("${azure.translator.key}")
    private String translatorKey;

    @Value("${azure.translator.region}")
    private String translatorRegion;

    @Autowired
    private RestTemplate restTemplate;

    public TranslationServiceImpl() {
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
}