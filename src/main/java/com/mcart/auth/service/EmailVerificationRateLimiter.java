package com.mcart.auth.service;

import com.mcart.auth.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EmailVerificationRateLimiter {

    private final StringRedisTemplate redis;

    public void assertAllowed(UUID authIdentityId) {

        String key = "email_verification:" + authIdentityId;

        Long count = redis.opsForValue().increment(key);

        if (count == 1) {
            redis.expire(key, Duration.ofHours(1));
        }

        if (count > 3) {
            throw new TooManyRequestsException(
                    "Too many verification emails requested"
            );
        }
    }
}
