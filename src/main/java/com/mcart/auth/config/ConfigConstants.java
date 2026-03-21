package com.mcart.auth.config;

/**
 * Application-wide constants used by the auth service.
 * <p>
 * Constants are grouped by domain to reduce cognitive load and adhere to single responsibility.
 * Configuration values (URLs, credentials) are externalized via properties/env vars.
 * </p>
 */
public final class ConfigConstants {

    private ConfigConstants() {
    }

    /**
     * HTTP paths for auth API endpoints.
     */
    public static final class AuthPaths {
        private AuthPaths() {
        }

        /** Path for email verification link (GET). */
        public static final String VERIFY_EMAIL = "/auth/verify-email";

        /** Path where refresh token cookie is valid. */
        public static final String REFRESH = "/auth/refresh";
    }

    /**
     * Redis key prefixes for auth-related data.
     * Keys are namespaced to avoid collisions with other services.
     */
    public static final class RedisKeys {
        private RedisKeys() {
        }

        /** Prefix for login failure attempt counters. Key: {@code auth:login:fail:{authIdentityId}} */
        public static final String LOGIN_FAILURE = "auth:login:fail:";

        /** Prefix for refresh token storage. Key: {@code refresh:{tokenId}} */
        public static final String REFRESH_TOKEN = "refresh:";

        /** Prefix for email verification rate limit counters. Key: {@code email_verification:{authIdentityId}} */
        public static final String EMAIL_VERIFICATION = "email_verification:";
    }

    /**
     * HTTP cookie names and paths used for auth.
     */
    public static final class Cookie {
        private Cookie() {
        }

        /** Name of the HttpOnly cookie storing the refresh token. */
        public static final String REFRESH_TOKEN_NAME = "refresh_token";

        /** Path scope for the refresh token cookie. */
        public static final String REFRESH_PATH = AuthPaths.REFRESH;
    }

    /**
     * JWT claim names used in access tokens.
     */
    public static final class JwtClaims {
        private JwtClaims() {
        }

        /** Claim key for user ID. */
        public static final String USER_ID = "userId";

        /** Claim key for token type. */
        public static final String TYPE = "type";

        /** Token type value for access tokens. */
        public static final String TYPE_ACCESS = "ACCESS";
    }

    /**
     * Email-related constants.
     */
    public static final class Email {
        private Email() {
        }

        /** Subject line for verification emails. */
        public static final String VERIFICATION_SUBJECT = "Verify your email";
    }
}
