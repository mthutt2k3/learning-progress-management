package com.learning.progress.common;

public enum ChallengeStatus {
    DRAFT,
    PUBLISHED,
    IN_PROGRESS, // new: challenge is currently running (startDate reached)
    CLOSED;       // new: challenge finished (endDate reached)

    public boolean isPublishedOrHigher() {
        return this == PUBLISHED || this == IN_PROGRESS || this == CLOSED;
    }
}
