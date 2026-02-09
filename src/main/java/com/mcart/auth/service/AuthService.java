package com.mcart.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mcart.auth.dto.LoginRequest;
import com.mcart.auth.dto.LoginResponse;
import com.mcart.auth.dto.PasswordSignupRequest;
import com.mcart.auth.dto.SocialLoginRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.UUID;

public interface AuthService {

    UUID signupWithPassword(PasswordSignupRequest request) throws JsonProcessingException;

    LoginResponse login(LoginRequest request, HttpServletResponse response);

    LoginResponse socialLogin(SocialLoginRequest request,HttpServletResponse response);
}
