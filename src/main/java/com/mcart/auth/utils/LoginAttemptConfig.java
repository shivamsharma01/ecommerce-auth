package com.mcart.auth.utils;

import com.mcart.auth.model.LoginAttemptProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LoginAttemptConfig {

    @Value("${auth.login-attempt.max-attempts:5}")
    private int maxAttempts;

    @Value("${auth.login-attempt.lock-duration-minutes:15}")
    private int lockDurationMinutes;

    @Bean
    public LoginAttemptProperties loginAttemptProperties() {
        return new LoginAttemptProperties(maxAttempts, Duration.ofMinutes(lockDurationMinutes));
    }
}
