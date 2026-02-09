package com.mcart.auth.service;

import com.mcart.auth.entity.AuthUserEntity;
import com.mcart.auth.exception.UnauthorizedException;
import com.mcart.auth.model.AuthUserStatus;
import com.mcart.auth.model.RefreshTokenData;
import com.mcart.auth.model.TokenResult;
import com.mcart.auth.repository.AuthUserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenStore refreshTokenStore;
    private final AuthUserRepository authUserRepo;

    @Value("${security.jwt.refresh-token-ttl-seconds}")
    private long refreshTtlSeconds;

    private final Clock clock = Clock.systemUTC();

    private String generateAccessToken(UUID authIdentityId, UUID userId) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://auth.mycompany.com")
                .subject(authIdentityId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .claim("userId", userId.toString())
                .claim("type", "ACCESS")
                .id(UUID.randomUUID().toString())
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(claims)
        ).getTokenValue();
    }
    public TokenResult issueTokens(
            UUID authIdentityId,
            UUID userId,
            HttpServletResponse response
    ) {

        // 1️⃣ Access token (JWT)
        String accessToken = generateAccessToken(authIdentityId, userId);

        // 2️⃣ Refresh token (opaque)
        String refreshTokenId = UUID.randomUUID().toString();

        refreshTokenStore.store(
                refreshTokenId,
                new RefreshTokenData(authIdentityId, userId, Instant.now(clock)),
                Duration.ofSeconds(refreshTtlSeconds)
        );

        // 3️⃣ Set refresh token as HttpOnly cookie
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshTokenId)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/auth/refresh")
                .maxAge(refreshTtlSeconds)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return new TokenResult(
                accessToken,
                refreshTtlSeconds
        );
    }

    @Transactional
    @Override
    public TokenResult refreshTokens(
            String refreshTokenId,
            HttpServletResponse response
    ) {

        if (refreshTokenId == null) {
            throw new UnauthorizedException("Missing refresh token");
        }

        // 1️⃣ Load refresh token data
        RefreshTokenData tokenData = refreshTokenStore
                .get(refreshTokenId)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        // 2️⃣ Check user still active
        AuthUserEntity user = authUserRepo
                .findByUserId(tokenData.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (user.getStatus() != AuthUserStatus.ACTIVE) {
            throw new UnauthorizedException("User inactive");
        }

        // 3️⃣ Rotate refresh token
        refreshTokenStore.revoke(refreshTokenId);

        return issueTokens(
                tokenData.getAuthIdentityId(),
                tokenData.getUserId(),
                response
        );
    }

}
