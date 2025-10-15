package com.learning.progress.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentInfo {
    @NotBlank
    private String parentName;
    private String parentEmail;
    @NotBlank
    private String parentPhone;
    private String relationship; // e.g., "Father", "Mother"
}