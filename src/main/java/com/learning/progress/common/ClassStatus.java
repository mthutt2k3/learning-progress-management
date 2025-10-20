package com.learning.progress.common;

public enum ClassStatus {
    PENDING,      // lớp mới tạo, chưa khai giảng
    ACTIVE,       // lớp đang diễn ra
    UPCOMING_END, // lớp gần kết thúc
    FINISHED,     // lớp đã kết thúc
    INACTIVE      // lớp chưa kích hoạt hoặc tạm dừng
}
