package com.learning.progress.dto.grading;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CriteriaFeedback {
    private CriteriaScore taskResponse;
    private CriteriaScore cohesionCoherence;
    private CriteriaScore lexicalResource;
    private CriteriaScore grammaticalRangeAccuracy;
}
