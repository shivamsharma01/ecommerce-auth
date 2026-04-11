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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenServiceImpl implements TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final AuthUserRepository authUserRepo;

    @Value("${security.jwt.refresh-token-ttl-seconds}")
    private long refreshTtlSeconds;

    @Value("${auth.cookie.secure:true}")
    private boolean cookieSecure;

    private final Clock clock = Clock.systemUTC();

    public TokenResult issueTokens(
            UUID authIdentityId,
            UUID userId,
            HttpServletResponse response
    ) {

        AuthUserEntity user = authUserRepo.findByUserId(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found for token issue"));

        // 1️⃣ Access token (JWT) — platform admins get scope product.admin for downstream services
        String accessToken = jwtTokenProvider.generateAccessToken(
                authIdentityId, userId, user.isPlatformAdmin());

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

        log.debug("Issued token pair userId={} platformAdmin={}", userId, user.isPlatformAdmin());
        return new TokenResult(
                accessToken,
                jwtTokenProvider.getAccessTokenTtl()
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

        log.debug("Refresh token rotated userId={}", tokenData.getUserId());
        return issueTokens(
                tokenData.getAuthIdentityId(),
                tokenData.getUserId(),
                response
        );
    }

}
