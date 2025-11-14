package com.learning.progress.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.progress.common.Const;
import com.learning.progress.dto.auth.LoginRequest;
import com.learning.progress.dto.auth.LoginResponse;
import com.learning.progress.exception.GlobalExceptionHandler;
import com.learning.progress.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class AuthControllerValidationTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/auth/login -> 400 when username is blank")
    void shouldReturn400_whenUsernameBlank() throws Exception {
        String json = """
                {
                  "username": " ",
                  "password": "ValidPass1",
                  "loginRole": "STUDENT"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.USERNAME.REQUIRED));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login -> 400 when username too short")
    void shouldReturn400_whenUsernameTooShort() throws Exception {
        int min = Const.USERNAME.MIN_LENGTH_VALUE;
        String shortUsername = "a".repeat(Math.max(0, min - 1));
        String json = String.format("""
                {
                  "username": "%s",
                  "password": "ValidPass1",
                  "loginRole": "STUDENT"
                }
                """, shortUsername);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.USERNAME.LENGTH_INVALID));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login -> 400 when password is blank")
    void shouldReturn400_whenPasswordBlank() throws Exception {
        String json = """
                {
                  "username": "validuser",
                  "password": " ",
                  "loginRole": "STUDENT"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.PASSWORD.REQUIRED));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login -> 400 when password format invalid")
    void shouldReturn400_whenPasswordInvalidFormat() throws Exception {
        String json = """
                {
                  "username": "validuser",
                  "password": "123",
                  "loginRole": "STUDENT"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.PASSWORD.INVALID_PASSWORD_FORMAT));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login -> 400 when loginRole is blank")
    void shouldReturn400_whenLoginRoleBlank() throws Exception {
        String json = """
                {
                  "username": "validuser",
                  "password": "ValidPass1",
                  "loginRole": " "
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.ROLE.REQUIRED));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login -> 400 when loginRole invalid pattern")
    void shouldReturn400_whenLoginRoleInvalid() throws Exception {
        String json = """
                {
                  "username": "validuser",
                  "password": "ValidPass1",
                  "loginRole": "INVALID_ROLE"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(Const.ROLE.INVALID_LOGIN_ROLE));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login -> 200 when payload valid and service called")
    void shouldReturn200_whenValidLogin() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .username("validuser")
                .password("ValidPass1")
                .loginRole("STUDENT")
                .build();

        LoginResponse resp = LoginResponse.builder()
                .username("validuser")
                .accessToken("atoken")
                .refreshToken("rtoken")
                .role("STUDENT")
                .mustChangePassword(false)
                .build();

        when(authService.login(ArgumentMatchers.any(LoginRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("atoken"))
                .andExpect(jsonPath("$.data.refreshToken").value("rtoken"));

        verify(authService, times(1)).login(ArgumentMatchers.any(LoginRequest.class));
    }
}
