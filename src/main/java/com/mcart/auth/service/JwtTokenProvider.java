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

    private final JwtEncoder jwtEncoder;

    public String generateAccessToken(UUID authIdentityId, UUID userId, boolean platformAdmin) {

        Instant now = Instant.now();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuerUri)
                .subject(authIdentityId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTtl))
                .claim(ConfigConstants.JwtClaims.USER_ID, userId.toString())
                .claim(ConfigConstants.JwtClaims.TYPE, ConfigConstants.JwtClaims.TYPE_ACCESS)
                .id(UUID.randomUUID().toString());

        if (platformAdmin) {
            claims.claim("scope", ConfigConstants.JwtClaims.SCOPE_PRODUCT_ADMIN);
        }

        return jwtEncoder.encode(
                JwtEncoderParameters.from(claims.build())
        ).getTokenValue();

    }

    public long getAccessTokenTtl() {
        return accessTtl;
    }
}
