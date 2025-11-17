package com.learning.progress.common;

public enum RiskType {
    LOW_SCORES,              // 3 bài liền < 6 điểm
    FREQUENT_LATE_SUBMISSIONS, // >= 50% bài nộp muộn
    SUSPECTED_CHEATING,      // Nhiều TAB_SWITCH hoặc COPY_ATTEMPT
    DECLINING_VOCABULARY,    // GV giảm liên tục 3 bài hoặc giảm > 2 điểm
    DECLINING_READING,       // RE giảm liên tục 3 bài hoặc giảm > 2 điểm
    DECLINING_LISTENING,     // LI giảm liên tục 3 bài hoặc giảm > 2 điểm
    DECLINING_WRITING,       // WR giảm liên tục 3 bài hoặc giảm > 2 điểm
    DECLINING_SPEAKING       // SP giảm liên tục 3 bài hoặc giảm > 2 điểm
}