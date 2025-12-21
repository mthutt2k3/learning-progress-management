package com.learning.progress.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for loading and rendering prompt templates from resource files
 */
@Component
@Slf4j
public class PromptTemplateLoader {

    private static final String PROMPTS_BASE_PATH = "prompts/";
    private final Map<String, String> templateCache = new HashMap<>();

    /**
     * Load a prompt template from resources
     *
     * @param templatePath Path relative to resources/prompts/ (e.g., "common/content_moderation.txt")
     * @return Template content as string
     */
    public String loadTemplate(String templatePath) {
        // Check cache first
        if (templateCache.containsKey(templatePath)) {
            return templateCache.get(templatePath);
        }

        try {
            String fullPath = PROMPTS_BASE_PATH + templatePath;
            ClassPathResource resource = new ClassPathResource(fullPath);

            if (!resource.exists()) {
                log.error("Prompt template not found: {}", fullPath);
                throw new IllegalArgumentException("Prompt template not found: " + fullPath);
            }

            String content;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                content = reader.lines().collect(Collectors.joining("\n"));
            }

            // Cache the template
            templateCache.put(templatePath, content);
            log.debug("Loaded prompt template: {}", templatePath);

            return content;

        } catch (IOException e) {
            log.error("Error loading prompt template: {}", templatePath, e);
            throw new RuntimeException("Failed to load prompt template: " + templatePath, e);
        }
    }

    /**
     * Load and render a template with variables
     *
     * @param templatePath Path to template file
     * @param variables Map of variable names to values (e.g., {"level_name": "Intermediate"})
     * @return Rendered template with variables replaced
     */
    public String render(String templatePath, Map<String, String> variables) {
        String template = loadTemplate(templatePath);
        return renderTemplate(template, variables);
    }

    /**
     * Render a template string with variables
     * Variables in template should be in format: {variable_name}
     *
     * @param template Template string
     * @param variables Map of variable names to values
     * @return Rendered string
     */
    public String renderTemplate(String template, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return template;
        }

        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }

        return result;
    }

    /**
     * Clear the template cache (useful for testing or dynamic reloading)
     */
    public void clearCache() {
        templateCache.clear();
        log.info("Prompt template cache cleared");
    }

    /**
     * Helper method to build common variable map for level info
     */
    public static Map<String, String> buildLevelVariables(String levelName, String levelDescription, String learningObjective) {
        Map<String, String> vars = new HashMap<>();
        vars.put("level_name", levelName != null ? levelName : "");
        vars.put("level_description", levelDescription != null ? levelDescription : "");
        vars.put("learning_objective", learningObjective != null && !learningObjective.isBlank()
                ? "Learning Objective: " + learningObjective + "\n"
                : "");
        return vars;
    }
}