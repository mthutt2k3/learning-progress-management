package com.learning.progress.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import com.learning.progress.dto.auth.LoginRequest;
import com.learning.progress.dto.auth.LoginResponse;
import com.learning.progress.exception.ApiException;
import com.learning.progress.exception.GlobalExceptionHandler;
import com.learning.progress.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ====================== VALIDATION TESTS (400) ======================

    @Test
    @DisplayName("1. Username blank → 400")
    void username_blank() throws Exception {
        String json = """
                { "username": "       ", "password": "ValidPass1", "loginRole": "STUDENT" }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.USERNAME.REQUIRED));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("2. Username too short → 400")
    void username_too_short() throws Exception {
        int min = Const.USERNAME.MIN_LENGTH_VALUE;
        String shortUsername = "a".repeat(Math.max(0, min - 1));
        String json = String.format("""
                { "username": "%s", "password": "ValidPass1", "loginRole": "STUDENT" }
                """, shortUsername);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.USERNAME.LENGTH_INVALID));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("3. Username too long → 400")
    void username_too_long() throws Exception {
        int max = Const.USERNAME.MAX_LENGTH_VALUE;
        String longUsername = "a".repeat(max + 1);
        String json = String.format("""
                { "username": "%s", "password": "ValidPass1", "loginRole": "STUDENT" }
                """, longUsername);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.USERNAME.LENGTH_INVALID));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("4. Password blank → 400")
    void password_blank() throws Exception {
        String json = """
                { "username": "validuser", "password": null, "loginRole": "STUDENT" }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.PASSWORD.REQUIRED));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("5. Password too short → 400")
    void password_too_short() throws Exception {
        String json = """
                { "username": "validuser", "password": "123", "loginRole": "STUDENT" }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.PASSWORD.INVALID_PASSWORD_FORMAT));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("6. Password has special char → 400")
    void password_invalid_char() throws Exception {
        String json = """
                { "username": "validuser", "password": "Pass1!", "loginRole": "STUDENT" }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.PASSWORD.INVALID_PASSWORD_FORMAT));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("7. LoginRole blank → 400")
    void loginRole_blank() throws Exception {
        String json = """
                { "username": "validuser", "password": "ValidPass1", "loginRole": null }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.ROLE.REQUIRED));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("8. LoginRole invalid pattern → 400")
    void loginRole_invalid_pattern() throws Exception {
        String json = """
                { "username": "validuser", "password": "ValidPass1", "loginRole": "INVALID_ROLE" }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.ROLE.INVALID_LOGIN_ROLE));

        verifyNoInteractions(authService);
    }

    // ====================== SERVICE EXCEPTION TESTS ======================

    @Test
    @DisplayName("9. User not found → 401")
    void user_not_found() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("nouser")
                .password("ValidPass1")
                .loginRole("STUDENT")
                .build();

        when(authService.login(any())).thenThrow(new ApiException(Const.USER.NOT_FOUND, HttpStatus.UNAUTHORIZED.value()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(Const.USER.NOT_FOUND));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("10. Invalid password → 401")
    void invalid_password() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("testuser")
                .password("wrong1")
                .loginRole("STUDENT")
                .build();

        when(authService.login(any())).thenThrow(new ApiException(Const.AUTH.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED.value()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(Const.AUTH.INVALID_CREDENTIALS));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("11. Inactive user → 403")
    void inactive_user() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("testuser")
                .password("ValidPass1")
                .loginRole("STUDENT")
                .build();

        when(authService.login(any())).thenThrow(new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(Const.USER.USER_INACTIVE));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("12. Forbidden role (STUDENT tries TEACHER) → 403")
    void forbidden_role_student_tries_teacher() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("testuser")
                .password("ValidPass1")
                .loginRole("TEACHER")
                .build();

        when(authService.login(any())).thenThrow(new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(Const.SECURITY.FORBIDDEN_ROLE));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("13. Forbidden role (TEACHER tries STUDENT) → 403")
    void forbidden_role_teacher_tries_student() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("teacheruser")
                .password("ValidPass1")
                .loginRole("STUDENT")
                .build();

        when(authService.login(any())).thenThrow(new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(Const.SECURITY.FORBIDDEN_ROLE));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("14. Invalid login role (case-sensitive check bypassed) → 400")
    void invalid_login_role_case_insensitive() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("validuser")
                .password("ValidPass1")
                .loginRole("TEACHER")
                .build();

        when(authService.login(any())).thenThrow(new ApiException(Const.ROLE.INVALID_LOGIN_ROLE, HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.ROLE.INVALID_LOGIN_ROLE));

        verify(authService, times(1)).login(any());
    }

    // ====================== SUCCESS TESTS ======================

    @Test
    @DisplayName("15. Valid login (STUDENT) → 200")
    void valid_login_student() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("testuser")
                .password("ValidPass1")
                .loginRole("STUDENT")
                .build();

        LoginResponse resp = LoginResponse.builder()
                .username("testuser")
                .accessToken("atoken")
                .refreshToken("rtoken")
                .role("STUDENT")
                .mustChangePassword(false)
                .build();

        when(authService.login(any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(Const.RESULT_MESSAGE_CODE.LOGIN_SUCCESS))
                .andExpect(jsonPath("$.data.accessToken").value("atoken"));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("16. Valid login (TEACHER) → 200")
    void valid_login_teacher() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("teacher")
                .password("ValidPass1")
                .loginRole("TEACHER")
                .build();

        LoginResponse resp = LoginResponse.builder()
                .username("teacher")
                .accessToken("atoken")
                .refreshToken("rtoken")
                .role("TEACHER")
                .mustChangePassword(false)
                .build();

        when(authService.login(any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("TEACHER"));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("17. Extra field ignored → 200")
    void extra_field_ignored() throws Exception {
        String json = """
                {
                  "username": "testuser",
                  "password": "ValidPass1",
                  "loginRole": "STUDENT",
                  "extra": "ignored"
                }
                """;

        LoginResponse resp = LoginResponse.builder()
                .username("testuser")
                .accessToken("atoken")
                .refreshToken("rtoken")
                .role("STUDENT")
                .mustChangePassword(false)
                .build();

        when(authService.login(any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("atoken"));

        verify(authService, times(1)).login(any());
    }

    // ====================== ADDITIONAL SERVICE TESTS (MOCK SERVICE BEHAVIOR) ======================

    @Test
    @DisplayName("18. Login Success (STUDENT) - Full flow")
    void login_success_student_full_flow() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("student01")
                .password("ValidPass123")
                .loginRole("STUDENT")
                .build();

        LoginResponse resp = LoginResponse.builder()
                .username("student01")
                .accessToken("access-token-xyz")
                .refreshToken("refresh-token-123")
                .role("STUDENT")
                .mustChangePassword(false)
                .build();

        when(authService.login(any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token-xyz"));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("19. User not found - Service Level → 401")
    void login_userNotFound_401_service() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("unknown")
                .password("password")
                .loginRole("STUDENT")
                .build();

        when(authService.login(any())).thenThrow(new ApiException(Const.USER.NOT_FOUND, HttpStatus.UNAUTHORIZED.value()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(Const.USER.NOT_FOUND));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("20. Invalid password - Service Level → 401")
    void login_invalidPassword_401_service() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("student01")
                .password("wrongPass")
                .loginRole("STUDENT")
                .build();

        when(authService.login(any())).thenThrow(new ApiException(Const.AUTH.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED.value()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(Const.AUTH.INVALID_CREDENTIALS));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("21. User inactive - Service Level → 403")
    void login_userInactive_403_service() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("student01")
                .password("ValidPass123")
                .loginRole("STUDENT")
                .build();

        when(authService.login(any())).thenThrow(new ApiException(Const.USER.USER_INACTIVE, HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(Const.USER.USER_INACTIVE));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("22. TEACHER login as STUDENT - Service Level → 403")
    void login_forbiddenRole_teacherTryStudent_403_service() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("teacher01")
                .password("ValidPass123")
                .loginRole("STUDENT")
                .build();

        when(authService.login(any())).thenThrow(new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(Const.SECURITY.FORBIDDEN_ROLE));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("23. STUDENT login as TEACHER - Service Level → 403")
    void login_forbiddenRole_studentTryTeacher_403_service() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("student01")
                .password("ValidPass123")
                .loginRole("TEACHER")
                .build();

        when(authService.login(any())).thenThrow(new ApiException(Const.SECURITY.FORBIDDEN_ROLE, HttpStatus.FORBIDDEN.value()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(Const.SECURITY.FORBIDDEN_ROLE));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("24. Must change password - Service Level → returns reset token")
    void login_mustChangePassword_generateResetToken_service() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("student01")
                .password("ValidPass123")
                .loginRole("STUDENT")
                .build();

        LoginResponse resp = LoginResponse.builder()
                .username("student01")
                .refreshToken("reset-token-123")
                .role("STUDENT")
                .mustChangePassword(true)
                .build();

        when(authService.login(any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.refreshToken").value("reset-token-123"));

        verify(authService, times(1)).login(any());
    }
}