package com.mcart.auth.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

@Getter
@AllArgsConstructor
public class LoginAttemptProperties {
    private final int maxAttempts;
    private final Duration lockDuration;
}
