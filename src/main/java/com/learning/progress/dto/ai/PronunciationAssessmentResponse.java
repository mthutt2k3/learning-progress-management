package com.learning.progress.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PronunciationAssessmentResponse {

    /**
     * Overall pronunciation score (0-100)
     */
    private Double pronunciationScore;

    /**
     * Accuracy score - how correctly phonemes are pronounced
     */
    private Double accuracyScore;

    /**
     * Fluency score - natural speech flow
     */
    private Double fluencyScore;

    /**
     * Completeness score - percentage of reference text spoken
     */
    private Double completenessScore;

    /**
     * Prosody score - intonation and stress patterns (optional)
     */
    private Double prosodyScore;

    /**
     * Recognized text from audio
     */
    private String recognizedText;

    /**
     * Reference text provided
     */
    private String referenceText;

    /**
     * Word-level assessment details
     */
    private List<WordAssessment> words;

    /**
     * Overall feedback in Vietnamese
     */
    private String feedback;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WordAssessment {
        /**
         * The word being assessed
         */
        private String word;

        /**
         * Accuracy score for this word (0-100)
         */
        private Double accuracyScore;

        /**
         * Error type: None, Omission, Insertion, Mispronunciation
         */
        private String errorType;

        /**
         * Position in the sentence (0-based index)
         */
        private Integer position;

        /**
         * Duration in milliseconds
         */
        private Long duration;
    }
}