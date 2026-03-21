package com.mcart.auth.service;

import com.mcart.auth.config.ConfigConstants;
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


    @Value("${security.jwt.access-token-ttl-seconds}")
    private long accessTtl;

    @Value("${auth.issuer-uri}")
    private String issuerUri;

    private JwtEncoder jwtEncoder;

    public String generateAccessToken(UUID authIdentityId, UUID userId) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuerUri)
                .subject(authIdentityId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTtl))
                .claim(ConfigConstants.JwtClaims.USER_ID, userId.toString())
                .claim(ConfigConstants.JwtClaims.TYPE, ConfigConstants.JwtClaims.TYPE_ACCESS)
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
