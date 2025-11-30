package com.learning.progress.dto.report.clazz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleDistribution {
    private String roleName;
    private Long count;
    private BigDecimal percentage;
}
