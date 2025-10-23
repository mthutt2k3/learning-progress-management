package com.learning.progress.service.strategy;

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

import java.util.List;

@Component
public class ClassServiceStrategyFactory {

    private final List<ClassServiceStrategy> strategies;
    private final UserRepository userRepository;

    @Autowired
    public ClassServiceStrategyFactory(List<ClassServiceStrategy> strategies, UserRepository userRepository) {
        this.strategies = strategies;
        this.userRepository = userRepository;
    }

    public ClassService getClassService() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUserNameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ApiException(Const.USER.NOT_FOUND, HttpStatus.NOT_FOUND.value()));

        RoleName role = currentUser.getRole().getName();

        return strategies.stream()
                .filter(s -> s.supports(role))
                .findFirst()
                .orElseThrow(() -> new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value()));
    }

}