package com.mcart.auth.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class RefreshTokenData {
    private UUID authIdentityId;
    private UUID userId;
    private Instant issuedAt;
}
