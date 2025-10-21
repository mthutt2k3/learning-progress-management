package com.learning.progress.service.factory;

import com.learning.progress.common.Const;
import com.learning.progress.common.RoleName;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.ClassService;
import com.learning.progress.service.impl.ManagerClassServiceImpl;
import com.learning.progress.service.impl.StudentClassServiceImpl;
import com.learning.progress.service.impl.TeacherClassServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ClassServiceFactory {

    @Autowired
    private ManagerClassServiceImpl managerClassService;

    @Autowired
    private TeacherClassServiceImpl teacherClassService;

    @Autowired
    private StudentClassServiceImpl studentClassService;

    @Autowired
    private UserRepository userRepository;

    public ClassService getClassService() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUserNameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));
        RoleName role = currentUser.getRole().getName();

        return switch (role) {
            case MANAGER -> managerClassService;
            case TEACHER, TEACHING_ASSISTANT -> teacherClassService;
            case STUDENT, TEST_TAKER -> studentClassService;
            default -> throw new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value());
        };
    }

}