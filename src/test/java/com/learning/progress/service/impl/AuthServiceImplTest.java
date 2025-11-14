package com.learning.progress.service.impl;

import com.learning.progress.common.Const;
import com.learning.progress.common.RoleName;
import com.learning.progress.common.UserStatus;
import com.learning.progress.dto.auth.LoginRequest;
import com.learning.progress.dto.auth.LoginResponse;
import com.learning.progress.entity.RefreshToken;
import com.learning.progress.entity.Role;
import com.learning.progress.entity.User;
import com.learning.progress.exception.ApiException;
import com.learning.progress.mapper.AuthMapper;
import com.learning.progress.repository.RefreshTokenRepository;
import com.learning.progress.repository.UserRepository;
import com.learning.progress.service.EmailService;
import com.learning.progress.service.TokenService;
import com.learning.progress.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private TokenService tokenService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuthMapper authMapper;

    // other dependencies present in implementation but not used in login flow
    @Mock private SpringTemplateEngine templateEngine;
    @Mock private EmailService emailService;
    @Mock private jakarta.mail.Session mailSession;

    private User sampleUser;
    private Role sampleRole;

    @BeforeEach
    void setUp() {
        sampleRole = new Role();
        sampleRole.setName(RoleName.STUDENT);

        sampleUser = new User();
        sampleUser.setUserName("testuser");
        sampleUser.setPassword("encoded-pass");
        sampleUser.setEmail("user@example.com");
        sampleUser.setStatus(UserStatus.ACTIVE);
        sampleUser.setRole(sampleRole);
    }

    @Test
    void login_success_student() {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("plain-pass")
                .loginRole("STUDENT")
                .build();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("rtoken");

        LoginResponse expectedResponse = LoginResponse.builder()
                .username("testuser")
                .accessToken("atoken")
                .refreshToken("rtoken")
                .role("STUDENT")
                .mustChangePassword(false)
                .build();

        when(userRepository.findByUserNameAndDeletedAtIsNull("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("plain-pass", "encoded-pass")).thenReturn(true);
        when(tokenService.createRefreshToken(sampleUser)).thenReturn(refreshToken);
        when(jwtUtil.generateAuthToken(
                        "testuser",
                        "STUDENT",
                        null,
                        "user@example.com"
                )
        ).thenReturn("atoken");
        when(authMapper.toLoginResponse(eq(sampleUser), eq(refreshToken), eq("atoken"), eq(false))).thenReturn(expectedResponse);

        // Act
        LoginResponse actual = authService.login(request);

        // Assert
        assertThat(actual).isNotNull();
        assertThat(actual.getUsername()).isEqualTo("testuser");
        assertThat(actual.getAccessToken()).isEqualTo("atoken");
        assertThat(actual.getRefreshToken()).isEqualTo("rtoken");
        verify(userRepository).findByUserNameAndDeletedAtIsNull("testuser");
        verify(passwordEncoder).matches("plain-pass", "encoded-pass");
        verify(tokenService).createRefreshToken(sampleUser);
        verify(authMapper).toLoginResponse(sampleUser, refreshToken, "atoken", false);
    }

    @Test
    void login_userNotFound_throwsApiException() {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .username("nouser")
                .password("x")
                .loginRole("STUDENT")
                .build();

        when(userRepository.findByUserNameAndDeletedAtIsNull("nouser")).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessage(Const.USER.NOT_FOUND);
        verify(userRepository).findByUserNameAndDeletedAtIsNull("nouser");
        verifyNoMoreInteractions(passwordEncoder, tokenService, authMapper);
    }

    @Test
    void login_invalidPassword_throwsApiException() {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("wrong")
                .loginRole("STUDENT")
                .build();

        when(userRepository.findByUserNameAndDeletedAtIsNull("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong", "encoded-pass")).thenReturn(false);

        // Act / Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessage(Const.AUTH.INVALID_CREDENTIALS);
        verify(passwordEncoder).matches("wrong", "encoded-pass");
        verifyNoInteractions(tokenService, authMapper);
    }

    @Test
    void login_inactiveUser_throwsApiException() {
        // Arrange
        sampleUser.setStatus(UserStatus.INACTIVE);
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("plain")
                .loginRole("STUDENT")
                .build();

        when(userRepository.findByUserNameAndDeletedAtIsNull("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("plain", "encoded-pass")).thenReturn(true);

        // Act / Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessage(Const.USER.USER_INACTIVE);
        verify(userRepository).findByUserNameAndDeletedAtIsNull("testuser");
    }

    @Test
    void login_invalidLoginRole_throwsApiException() {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("plain")
                .loginRole("INVALID_ROLE")
                .build();

        when(userRepository.findByUserNameAndDeletedAtIsNull("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("plain", "encoded-pass")).thenReturn(true);

        // Act / Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessage(Const.ROLE.INVALID_LOGIN_ROLE);
    }

    @Test
    void login_forbiddenRoleForTeacher_throwsApiException() {
        // Arrange: user is STUDENT but attempting TEACHER login
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("plain")
                .loginRole("TEACHER")
                .build();

        when(userRepository.findByUserNameAndDeletedAtIsNull("testuser")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("plain", "encoded-pass")).thenReturn(true);

        // Act / Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessage(Const.SECURITY.FORBIDDEN_ROLE);
    }
}

