package com.learning.progress.dto.clazz;

import lombok.Data;

@Data
public class StudentProgressOverview {
    private Long userId;
    private Long classId;
    private String studentName;
    private int totalChallenges;
    private int completedChallenges;
    private double completionRate;

    public void setCompletedChallenges(int completedChallenges) {
        this.completedChallenges = completedChallenges;
        updateCompletionRate();
    }

    public void setTotalChallenges(int totalChallenges) {
        this.totalChallenges = totalChallenges;
        updateCompletionRate();
    }

    private void updateCompletionRate() {
        this.completionRate = totalChallenges > 0 ?
                ((double) completedChallenges / totalChallenges) * 100 : 0;
    }
}