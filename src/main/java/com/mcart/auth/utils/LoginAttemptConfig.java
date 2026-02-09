package com.mcart.auth.utils;

import com.mcart.auth.model.LoginAttemptProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LoginAttemptConfig {

    @Bean
    public LoginAttemptProperties loginAttemptProperties() {
        return new LoginAttemptProperties(5, Duration.ofMinutes(15));
    }
}
