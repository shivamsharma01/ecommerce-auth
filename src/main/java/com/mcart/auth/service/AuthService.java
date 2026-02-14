package com.mcart.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mcart.auth.dto.LoginRequest;
import com.mcart.auth.dto.LoginResponse;
import com.mcart.auth.dto.PasswordSignupRequest;
import com.mcart.auth.dto.SocialLoginRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.UUID;

/**
 * Service for the complete authentication flow: signup, login (password and social),
 * and pub/sub event publishing for downstream services.
 */
public interface AuthService {

    /**
     * Registers a new user with email and password.
     *
     * @param request the signup request
     * @return the created user ID
     * @throws com.mcart.auth.exception.ConflictException if email already registered
     */
    UUID signupWithPassword(PasswordSignupRequest request) throws JsonProcessingException;

    /**
     * Authenticates a user with email and password.
     *
     * @param request  the login request
     * @param response HTTP response for auth cookies
     * @return login response with tokens
     */
    LoginResponse login(LoginRequest request, HttpServletResponse response);

    /**
     * Authenticates or provisions a user via social provider.
     *
     * @param request  the social login request
     * @param response HTTP response for auth cookies
     * @return login response with tokens
     */
    LoginResponse socialLogin(SocialLoginRequest request, HttpServletResponse response);
}
