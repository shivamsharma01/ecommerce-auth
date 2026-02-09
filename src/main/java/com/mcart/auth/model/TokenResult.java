package com.mcart.auth.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenResult {
    private String accessToken;
    private long expiresIn;
}