package com.mcart.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class SignupResponse {
    private UUID userId;
    private String message;
}