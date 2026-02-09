package com.mcart.auth.dto;

import com.mcart.auth.model.AuthProviderType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SocialLoginRequest {
    private AuthProviderType providerType;
    private String providerUserId;
    private String email;
}