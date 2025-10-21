package com.learning.progress.service.impl;

import com.learning.progress.common.CommonStatus;
import com.learning.progress.common.Const;
import com.learning.progress.common.LevelEnum;
import com.learning.progress.entity.Level;
import com.learning.progress.entity.StudentLevel;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.LevelRepository;
import com.learning.progress.repository.StudentLevelRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.StudentLevelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class StudentLevelServiceImpl implements StudentLevelService {

    @Autowired
    private StudentLevelRepository studentLevelRepository;

    @Autowired
    private LevelRepository levelRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public void assignLevelToStudent(Long userId, Long levelId) {
        if (userId == null || levelId == null) {
            return; // Không làm gì nếu userId hoặc levelId null
        }

        // Tìm User theo userId
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(Const.ACCOUNT.ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        // Tìm Level theo levelId
        Level level = levelRepository.findById(levelId)
                .orElseThrow(() -> new ApiException(Const.LEVEL.LEVEL_NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        if (level.getStatus() != LevelEnum.PUBLISHED) {
            throw new ApiException(Const.LEVEL.NOT_PUBLISHED, HttpStatus.BAD_REQUEST.value());
        }
        // Kiểm tra xem học sinh đã có level ACTIVE chưa
        StudentLevel existingActive = studentLevelRepository
                .findByUserIdAndStatus(userId, CommonStatus.ACTIVE.toString()).orElse(null);

        // Nếu có, set thành INACTIVE
        if(existingActive != null) {
            existingActive.setStatus(CommonStatus.INACTIVE.toString());
            studentLevelRepository.saveAndFlush(existingActive);
        }

        // Tạo bản ghi StudentLevel
        StudentLevel studentLevel = StudentLevel.builder()
                .user(user)
                .level(level)
                .status(CommonStatus.ACTIVE.toString())
                .build();

        // Lưu vào student_levels
        studentLevelRepository.save(studentLevel);
    }
}