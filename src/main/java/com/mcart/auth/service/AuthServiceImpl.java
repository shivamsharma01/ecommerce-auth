package com.mcart.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mcart.auth.dto.LoginRequest;
import com.mcart.auth.dto.LoginResponse;
import com.mcart.auth.dto.PasswordSignupRequest;
import com.mcart.auth.dto.SocialLoginRequest;
import com.mcart.auth.entity.AuthIdentityEntity;
import com.mcart.auth.entity.AuthUserEntity;
import com.mcart.auth.exception.UnauthorizedException;
import com.mcart.auth.model.AuthProviderType;
import com.mcart.auth.model.AuthUserStatus;
import com.mcart.auth.model.TokenResult;
import com.mcart.auth.repository.AuthIdentityRepository;
import com.mcart.auth.repository.AuthUserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final Clock clock = Clock.systemUTC();
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;
    private final EmailVerificationService emailVerificationService;
    private final AuthUserRepository authUserRepo;
    private final AuthIdentityRepository authIdentityRepo;

    public UUID signupWithPassword(PasswordSignupRequest request) throws JsonProcessingException {

        UUID userId = UUID.randomUUID();
        UUID authIdentityId = UUID.randomUUID();

        authIdentityRepo.save(AuthIdentityEntity.builder()
                .authIdentityId(authIdentityId)
                .userId(userId)
                .providerType(AuthProviderType.PASSWORD)
                .identifier(request.getEmail())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .emailVerified(false)
                .createdAt(Instant.now(clock))
                .build());

        authUserRepo.save(AuthUserEntity.builder()
                .authIdentityId(authIdentityId)
                .userId(userId)
                .status(AuthUserStatus.ACTIVE)
                .createdAt(Instant.now(clock))
                .updatedAt(Instant.now(clock))
                .build());

        emailVerificationService.issueVerification(authIdentityId, userId, request.getEmail());

        return userId;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {

        // 1️⃣ Lookup PASSWORD identity by identifier (email)
        AuthIdentityEntity identity = authIdentityRepo
                .findByProviderTypeAndIdentifier(
                        AuthProviderType.PASSWORD,
                        request.getIdentifier()
                )
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        // 2️⃣ Fetch user with lock for update (prevents race conditions)
        AuthUserEntity user = authUserRepo
                .findForUpdateByUserId(identity.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // 3️⃣ Check account status
        if (user.getStatus() == AuthUserStatus.DELETED) {
            throw new UnauthorizedException("Account deleted");
        }

        if (user.getStatus() == AuthUserStatus.LOCKED) {
            if (user.getLockedUntil() != null &&
                    user.getLockedUntil().isAfter(Instant.now(clock))) {
                throw new UnauthorizedException("Account locked. Try later.");
            }
            // auto-unlock if lock expired
            user.setStatus(AuthUserStatus.ACTIVE);
            user.setLockedUntil(null);
        }

        // 4️⃣ Check email verification (PASSWORD only)
        if (!identity.isEmailVerified()) {
            throw new UnauthorizedException("Email not verified");
        }

        // check if already locked
        if (loginAttemptService.isLocked(identity)) {
            Instant until = loginAttemptService.lockedUntil(identity);
            throw new UnauthorizedException(
                    "Account locked. Try again after " + until
            );
        }

        // 5️⃣ Validate password
        if (!passwordEncoder.matches(request.getPassword(), identity.getPasswordHash())) {

            loginAttemptService.recordFailure(identity);

            if (loginAttemptService.isLocked(identity)) {
                user.setStatus(AuthUserStatus.LOCKED);
                user.setLockedUntil(
                        loginAttemptService.lockedUntil(identity)
                );
                authUserRepo.save(user);
            }

            throw new UnauthorizedException("Invalid credentials");
        }

        // 6️⃣ Successful login — reset failed attempts (Redis hook)
        loginAttemptService.resetFailures(identity);

        // 7️⃣ Update last login
        identity.setLastLoginAt(Instant.now(clock));
        authIdentityRepo.save(identity);

        // 8️⃣ Issue tokens
        TokenResult tokenResult = tokenService.issueTokens(
                identity.getAuthIdentityId(),
                identity.getUserId(),
                response
        );

        return new LoginResponse(
                tokenResult.getAccessToken(),
                tokenResult.getExpiresIn()
        );
    }


    @Transactional
    public LoginResponse socialLogin(
            SocialLoginRequest request,
            HttpServletResponse response
    ) {

        AuthProviderType provider = request.getProviderType();

        // 1️⃣ Lookup identity by provider + provider_user_id
        AuthIdentityEntity identity =
                authIdentityRepo
                        .findByProviderTypeAndProviderUserId(
                                provider,
                                request.getProviderUserId()
                        )
                        .orElseGet(() -> createSocialUser(request));

        // 2️⃣ Fetch user with lock (consistency with password login)
        AuthUserEntity user =
                authUserRepo
                        .findForUpdateByUserId(identity.getUserId())
                        .orElseThrow(() ->
                                new UnauthorizedException("User not found")
                        );

        // 3️⃣ Check account status
        if (user.getStatus() == AuthUserStatus.DELETED) {
            throw new UnauthorizedException("Account deleted");
        }

        if (user.getStatus() == AuthUserStatus.LOCKED) {
            throw new UnauthorizedException("Account locked");
        }

        // 4️⃣ Update last login
        identity.setLastLoginAt(Instant.now(clock));
        authIdentityRepo.save(identity);

        // 5️⃣ Issue tokens
        TokenResult tokenResult = tokenService.issueTokens(
                identity.getAuthIdentityId(),
                identity.getUserId(),
                response
        );

        return new LoginResponse(
                tokenResult.getAccessToken(),
                tokenResult.getExpiresIn()
        );
    }

    private AuthIdentityEntity createSocialUser(SocialLoginRequest request) {

        UUID userId = UUID.randomUUID();
        UUID authIdentityId = UUID.randomUUID();

        // 1️⃣ Create user
        authUserRepo.save(
                AuthUserEntity.builder()
                        .userId(userId)
                        .authIdentityId(authIdentityId)
                        .status(AuthUserStatus.ACTIVE)
                        .createdAt(Instant.now(clock))
                        .updatedAt(Instant.now(clock))
                        .build()
        );

        // 2️⃣ Create identity
        AuthIdentityEntity identity =
                AuthIdentityEntity.builder()
                        .authIdentityId(authIdentityId)
                        .userId(userId)
                        .providerType(request.getProviderType())
                        .providerUserId(request.getProviderUserId())
                        .identifier(request.getEmail())   // optional
                        .email(request.getEmail())
                        .emailVerified(true)              // 👈 important
                        .createdAt(Instant.now(clock))
                        .build();

        return authIdentityRepo.save(identity);
    }


}
