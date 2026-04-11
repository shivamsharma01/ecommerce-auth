package com.mcart.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mcart.auth.dto.LoginRequest;
import com.mcart.auth.dto.LoginResponse;
import com.mcart.auth.dto.PasswordSignupRequest;
import com.mcart.auth.dto.SocialLoginRequest;
import com.mcart.auth.entity.AuthIdentityEntity;
import com.mcart.auth.entity.AuthUserEntity;
import com.mcart.auth.exception.ConflictException;
import com.mcart.auth.exception.UnauthorizedException;
import com.mcart.auth.mapper.AuthMapper;
import com.mcart.auth.model.AuthProviderType;
import com.mcart.auth.model.AuthUserStatus;
import com.mcart.auth.model.TokenResult;
import com.mcart.auth.repository.AuthIdentityRepository;
import com.mcart.auth.repository.AuthUserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link AuthService} handling the complete authentication flow:
 * <ul>
 *   <li>Password signup with email verification</li>
 *   <li>Password login with lockout and email verification checks</li>
 *   <li>Social login (Google, Facebook, Apple) with auto-provisioning</li>
 * </ul>
 * <p>
 * Publishes outbox events (USER_SIGNUP_COMPLETED) consumed by downstream services
 * (e.g. User Service) via pub/sub for eventual consistency.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final Clock clock = Clock.systemUTC();
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;
    private final EmailVerificationService emailVerificationService;
    private final OutboxEventService outboxEventService;
    private final AuthUserRepository authUserRepo;
    private final AuthIdentityRepository authIdentityRepo;
    private final AuthMapper authMapper;

    /**
     * Registers a new user with email and password.
     * <p>
     * Flow:
     * 1. Validates email is not already registered
     * 2. Creates auth identity (PASSWORD provider) and auth user
     * 3. Issues email verification token
     * 4. Persists USER_SIGNUP_COMPLETED outbox event for User Service
     * </p>
     *
     * @param request the signup request (email, password, firstName, lastName)
     * @return the created user ID
     * @throws ConflictException        if email is already registered
     * @throws JsonProcessingException  if outbox payload serialization fails
     */
    @Override
    @Transactional
    public UUID signupWithPassword(PasswordSignupRequest request) throws JsonProcessingException {
        // 1. Check for duplicate email (PASSWORD provider uses email as identifier)
        Optional<AuthIdentityEntity> existing = authIdentityRepo.findByProviderTypeAndIdentifier(
                AuthProviderType.PASSWORD,
                request.getEmail()
        );
        if (existing.isPresent()) {
            throw new ConflictException("Email already registered");
        }

        UUID userId = UUID.randomUUID();
        UUID authIdentityId = UUID.randomUUID();

        // 2. Create auth identity
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

        // 3. Create auth user (1:1 with identity)
        authUserRepo.save(AuthUserEntity.builder()
                .authIdentityId(authIdentityId)
                .userId(userId)
                .status(AuthUserStatus.ACTIVE)
                .createdAt(Instant.now(clock))
                .updatedAt(Instant.now(clock))
                .build());

        // 4. Issue email verification
        emailVerificationService.issueVerification(authIdentityId, userId, request.getEmail());

        // 5. Publish USER_SIGNUP_COMPLETED for User Service (pub/sub)
        outboxEventService.publishUserSignupEvent(
                authIdentityId,
                userId,
                authMapper.toUserSignupPayload(request, userId)
        );

        log.info("Password signup completed userId={} authIdentityId={}", userId, authIdentityId);
        return userId;
    }

    /**
     * Authenticates a user with email and password.
     * <p>
     * Flow:
     * 1. Lookup PASSWORD identity by email
     * 2. Fetch user with pessimistic lock (prevents race conditions)
     * 3. Validate account status (not deleted, not locked or lock expired)
     * 4. Require email verification for PASSWORD provider
     * 5. Check login attempt lockout (Redis)
     * 6. Validate password; on failure, record attempt and optionally lock account
     * 7. On success: reset failures, update last login, issue tokens
     * </p>
     *
     * @param request  the login request (identifier=email, password)
     * @param response HTTP response for setting auth cookies
     * @return login response with access token and expiry
     * @throws UnauthorizedException if credentials invalid, account deleted/locked, or email unverified
     */
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        // 1. Lookup PASSWORD identity by identifier (email)
        AuthIdentityEntity identity = authIdentityRepo
                .findByProviderTypeAndIdentifier(AuthProviderType.PASSWORD, request.getIdentifier())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // 2. Fetch user with lock for update (prevents race conditions on lock/unlock)
        AuthUserEntity user = authUserRepo
                .findForUpdateByUserId(identity.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // 3. Check account status
        validateAccountStatus(user);

        // 4. Require email verification for PASSWORD provider
        if (!identity.isEmailVerified()) {
            throw new UnauthorizedException("Email not verified");
        }

        // 5. Check login attempt lockout (Redis)
        if (loginAttemptService.isLocked(identity)) {
            Instant until = loginAttemptService.lockedUntil(identity);
            throw new UnauthorizedException("Account locked. Try again after " + until);
        }

        // 6. Validate password
        if (!passwordEncoder.matches(request.getPassword(), identity.getPasswordHash())) {
            loginAttemptService.recordFailure(identity);
            if (loginAttemptService.isLocked(identity)) {
                user.setStatus(AuthUserStatus.LOCKED);
                user.setLockedUntil(loginAttemptService.lockedUntil(identity));
                authUserRepo.save(user);
                log.warn("Account locked after failed password attempts userId={}", user.getUserId());
            }
            throw new UnauthorizedException("Invalid credentials");
        }

        // 7. Successful login
        loginAttemptService.resetFailures(identity);
        identity.setLastLoginAt(Instant.now(clock));
        authIdentityRepo.save(identity);

        TokenResult tokenResult = tokenService.issueTokens(
                identity.getAuthIdentityId(),
                identity.getUserId(),
                response
        );

        log.info("Password login succeeded userId={}", identity.getUserId());
        return authMapper.toLoginResponse(tokenResult);
    }

    /**
     * Authenticates or provisions a user via social provider (Google, Facebook, Apple).
     * <p>
     * Flow:
     * 1. Lookup identity by provider + provider_user_id
     * 2. If not found, auto-provision (create identity + user + publish USER_SIGNUP)
     * 3. Fetch user with lock, validate account status
     * 4. Update last login, issue tokens
     * </p>
     *
     * @param request  the social login request (providerType, providerUserId, email)
     * @param response HTTP response for setting auth cookies
     * @return login response with access token and expiry
     * @throws UnauthorizedException if account is deleted or locked
     */
    @Override
    @Transactional
    public LoginResponse socialLogin(SocialLoginRequest request, HttpServletResponse response) {
        AuthProviderType provider = request.getProviderType();

        // 1. Lookup or create identity
        AuthIdentityEntity identity = authIdentityRepo
                .findByProviderTypeAndProviderUserId(provider, request.getProviderUserId())
                .orElseGet(() -> createSocialUser(request));

        // 2. Fetch user with lock
        AuthUserEntity user = authUserRepo
                .findForUpdateByUserId(identity.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // 3. Check account status
        validateAccountStatus(user);

        // 4. Update last login and issue tokens
        identity.setLastLoginAt(Instant.now(clock));
        authIdentityRepo.save(identity);

        TokenResult tokenResult = tokenService.issueTokens(
                identity.getAuthIdentityId(),
                identity.getUserId(),
                response
        );

        log.info("Social login succeeded userId={} provider={}", identity.getUserId(), provider);
        return authMapper.toLoginResponse(tokenResult);
    }

    /**
     * Validates that the user account is in an allowed state for login.
     *
     * @param user the auth user entity
     * @throws UnauthorizedException if account is deleted or locked
     */
    private void validateAccountStatus(AuthUserEntity user) {
        if (user.getStatus() == AuthUserStatus.DELETED) {
            throw new UnauthorizedException("Account deleted");
        }

        if (user.getStatus() == AuthUserStatus.LOCKED) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now(clock))) {
                throw new UnauthorizedException("Account locked. Try later.");
            }
            // Auto-unlock if lock has expired
            user.setStatus(AuthUserStatus.ACTIVE);
            user.setLockedUntil(null);
        }
    }

    /**
     * Creates a new user and identity for social login (auto-provisioning).
     * Publishes USER_SIGNUP_COMPLETED so User Service can create the profile.
     *
     * @param request the social login request
     * @return the created auth identity entity
     */
    private AuthIdentityEntity createSocialUser(SocialLoginRequest request) {
        UUID userId = UUID.randomUUID();
        UUID authIdentityId = UUID.randomUUID();

        // 1. Create auth user
        authUserRepo.save(AuthUserEntity.builder()
                .userId(userId)
                .authIdentityId(authIdentityId)
                .status(AuthUserStatus.ACTIVE)
                .createdAt(Instant.now(clock))
                .updatedAt(Instant.now(clock))
                .build());

        // 2. Create identity (social providers treat email as verified)
        AuthIdentityEntity identity = AuthIdentityEntity.builder()
                .authIdentityId(authIdentityId)
                .userId(userId)
                .providerType(request.getProviderType())
                .providerUserId(request.getProviderUserId())
                .identifier(request.getEmail())
                .email(request.getEmail())
                .emailVerified(true)
                .createdAt(Instant.now(clock))
                .build();

        identity = authIdentityRepo.save(identity);

        // 3. Publish USER_SIGNUP_COMPLETED for User Service
        try {
            outboxEventService.publishSocialSignupEvent(
                    authIdentityId,
                    userId,
                    authMapper.toSocialSignupPayload(request, userId)
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to publish social signup event", e);
        }

        log.info("Social user provisioned userId={} provider={}", userId, request.getProviderType());
        return identity;
    }
}
