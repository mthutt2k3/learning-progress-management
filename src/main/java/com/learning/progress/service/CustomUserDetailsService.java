package com.learning.progress.service;

import com.learning.progress.entity.User;
import com.learning.progress.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Service
@Transactional(readOnly = true)
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("[AUTHENTICATION] Loading user: {}", username);
        User user = userRepository.findByUserNameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        log.info("[AUTHENTICATION] User found: {}, role: {}", user.getUserName(), user.getRole().getName());
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName().name()));
        return new org.springframework.security.core.userdetails.User(
                user.getUserName(), user.getPassword(), authorities);
    }
}