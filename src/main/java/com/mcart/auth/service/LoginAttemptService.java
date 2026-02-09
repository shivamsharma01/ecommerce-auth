package com.mcart.auth.service;

import com.mcart.auth.entity.AuthIdentityEntity;

import java.time.Instant;

public interface LoginAttemptService {

    void recordFailure(AuthIdentityEntity identity);

    void resetFailures(AuthIdentityEntity identity);

    boolean isLocked(AuthIdentityEntity identity);

    Instant lockedUntil(AuthIdentityEntity identity);
}
