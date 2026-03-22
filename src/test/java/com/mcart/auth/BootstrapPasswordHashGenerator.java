package com.mcart.auth;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * One-off helper: {@code ./gradlew generateBootstrapPasswordHash -PbootstrapPassword='YourPassword'}.
 * Output is for Flyway SQL in ecomm-infra (same Argon2 params as {@code JwtConfig}).
 */
public final class BootstrapPasswordHashGenerator {

    public static void main(String[] args) {
        String raw = args.length > 0 ? args[0] : "ChangeMeAfterFirstDeploy!";
        var encoder = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
        System.out.println(encoder.encode(raw));
    }
}
