package com.mcart.auth.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mcart.auth.dto.*;
import com.mcart.auth.model.TokenResult;
import com.mcart.auth.service.AuthService;
import com.mcart.auth.service.TokenService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    // -----------------------------
    // PASSWORD SIGNUP
    // -----------------------------
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
            @RequestBody @Valid PasswordSignupRequest request) throws JsonProcessingException {

        UUID userId = authService.signupWithPassword(request);

        return ResponseEntity.ok(
                new SignupResponse(userId, "Verification email sent")
        );
    }

    // -----------------------------
    // PASSWORD LOGIN
    // -----------------------------
    @Transactional
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody @Valid LoginRequest request, HttpServletResponse servletResponse) {
        LoginResponse response = authService.login(request, servletResponse);
        return ResponseEntity.ok(response);
    }

    // -----------------------------
    // SOCIAL LOGIN
    // -----------------------------
    @PostMapping("/social/login")
    public ResponseEntity<LoginResponse> socialLogin(
            @RequestBody @Valid SocialLoginRequest request, HttpServletResponse servletResponse) {
        LoginResponse response = authService.socialLogin(request, servletResponse);
        return ResponseEntity.ok(response);
    }

    // -----------------------------
    // TOKEN REFRESH
    // -----------------------------
    @PostMapping("/refresh")
    public LoginResponse refresh(
            @CookieValue(name = "refresh_token", required = false)
            String refreshToken,
            HttpServletResponse response
    ) {
        TokenResult result = tokenService.refreshTokens(
                refreshToken,
                response
        );

        return new LoginResponse(
                result.getAccessToken(),
                result.getExpiresIn()
        );
    }
}
