package com.mcart.auth.service;

import com.mcart.auth.config.ConfigConstants;
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

    @Value("${security.jwt.access-token-ttl-seconds}")
    private long accessTtlSeconds;

    @Value("${security.jwt.refresh-token-ttl-seconds}")
    private long refreshTtlSeconds;

    @Value("${auth.issuer-uri}")
    private String issuerUri;

    @Value("${auth.cookie.secure:true}")
    private boolean cookieSecure;

    private final Clock clock = Clock.systemUTC();

    private String generateAccessToken(UUID authIdentityId, UUID userId) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuerUri)
                .subject(authIdentityId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTtlSeconds))
                .claim(ConfigConstants.JwtClaims.USER_ID, userId.toString())
                .claim(ConfigConstants.JwtClaims.TYPE, ConfigConstants.JwtClaims.TYPE_ACCESS)
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
        ResponseCookie refreshCookie = ResponseCookie.from(ConfigConstants.Cookie.REFRESH_TOKEN_NAME, refreshTokenId)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(ConfigConstants.Cookie.REFRESH_PATH)
                .maxAge(refreshTtlSeconds)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return new TokenResult(
                accessToken,
                accessTtlSeconds
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
