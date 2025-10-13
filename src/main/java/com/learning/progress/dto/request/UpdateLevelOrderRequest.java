package com.learning.progress.dto.request;

import com.learning.progress.common.LevelDifficulty;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLevelOrderRequest {

    @NotNull(message = "ID is required")
    private Long id;

    @NotNull(message = "Order number is required")
    private Integer orderNumber;

    @NotNull(message = "Level name number is required")
    private String levelName;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Difficulty number is required")
    private LevelDifficulty difficulty;

}
