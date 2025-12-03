package com.learning.progress.dto.report.challenge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/* --------------------------------------------------------
 * 3. CHART DATA
 * -------------------------------------------------------- */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeChartData {
    private List<StudentChartPoint> dataPoints;
}
