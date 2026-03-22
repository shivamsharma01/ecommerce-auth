package com.mcart.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps Flyway bootstrap SQL in ecomm-infra in sync with {@link com.mcart.auth.utils.JwtConfig} Argon2 params and default password.
 */
class BootstrapFlywayAdminPasswordTest {

    private static final String FLYWAY_DEFAULT_PASSWORD = "ChangeMeAfterFirstDeploy!";
    private static final String FLYWAY_EMBEDDED_HASH =
            "$argon2id$v=19$m=65536,t=3,p=1$rvbTxi43tYCG+YqGkwHEsw$fiMoJ0fHvZylfLfchlcaeXpNqqIB9Ht54UIfct8iaG8";

    @Test
    void flywayBootstrapHashMatchesDefaultPassword() {
        var encoder = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
        assertThat(encoder.matches(FLYWAY_DEFAULT_PASSWORD, FLYWAY_EMBEDDED_HASH)).isTrue();
    }
}
