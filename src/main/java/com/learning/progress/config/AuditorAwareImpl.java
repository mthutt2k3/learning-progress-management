package com.learning.progress.config;

import com.learning.progress.util.DataUtil;
import com.learning.progress.util.JwtUtil;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuditorAwareImpl implements AuditorAware<String> {
    public AuditorAwareImpl() {
        System.out.println("AuditorAwareImpl initialized");
    }

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            return Optional.of("system"); // Giá trị mặc định nếu không có người dùng
        }

        // Giả sử principal chứa thông tin email của người dùng
        String email = jwtUtil.extractEmailFromCurrentRequest(); // Trích xuất phần trước @

        return Optional.ofNullable(DataUtil.getEmailPrefix(email));
    }

}