package com.mcart.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String USER_ID = "userId";
    private static final String TYPE = "type";
    private static final String ACCESS = "ACCESS";

    @Value("${security.jwt.access-token-ttl-seconds}")
    private long accessTtl;

    private JwtEncoder jwtEncoder;

    public String generateAccessToken(UUID authIdentityId, UUID userId) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://auth.mycompany.com")
                .subject(authIdentityId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTtl))
                .claim(USER_ID, userId.toString())
                .claim(TYPE, ACCESS)
                .id(UUID.randomUUID().toString())
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(claims)
        ).getTokenValue();

    }

    public long getAccessTokenTtl() {
        return accessTtl;
    }
}
