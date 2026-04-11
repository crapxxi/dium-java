package com.dium.demo.controllers;

import com.dium.demo.dto.requests.LoginRequest;
import com.dium.demo.dto.requests.RegisterRequest;
import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.dto.responses.UserResponse;
import com.dium.demo.enums.UserRole;
import com.dium.demo.services.AuthService;
import com.dium.demo.services.CustomUserDetailsService;
import com.dium.demo.services.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean(name = "userDetailsService")
    private CustomUserDetailsService userDetailsService;

    @Test
    void register_WithValidData_Returns200AndUserResponse() throws Exception {
        RegisterRequest request = new RegisterRequest("+79991234567", "Test User", "password123");
        UserResponse response = new UserResponse(1L, "+79991234567", "Test User", UserRole.CLIENT);

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+79991234567"))
                .andExpect(jsonPath("$.name").value("Test User"));
    }

    @Test
    void register_WithEmptyPhone_Returns400BadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("", "Test User", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.phone").exists());
    }

    @Test
    void register_WithShortPassword_Returns400BadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("+79991234567", "Test User", "123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").exists());
    }

    @Test
    void login_WithValidCredentials_Returns200AndToken() throws Exception {
        LoginRequest request = new LoginRequest("+79991234567", "password123");
        AuthResponse response = new AuthResponse("jwt-token-string", "+79991234567");

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-string"))
                .andExpect(jsonPath("$.phone").value("+79991234567"));
    }

    @Test
    void login_WithBadCredentials_Returns401Unauthorized() throws Exception {
        LoginRequest request = new LoginRequest("+79991234567", "wrongpassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Phone or password is incorrect!"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Phone or password is incorrect!"));
    }
}