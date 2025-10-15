package com.learning.progress.dto;

import com.learning.progress.common.ActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassHistoryDTO {
    private Long id;
    private Long classId;
    private String className;
    private String actionDetails;
    private Long actionById;
    private String actionByUsername;
    private String actionByFullName;
    private OffsetDateTime actionAt;
    private ActionType actionType;
    private String visibleToRoles;
}