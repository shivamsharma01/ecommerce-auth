package com.mcart.auth.service;

import com.mcart.auth.config.ConfigConstants;
import com.mcart.auth.model.RefreshTokenData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = ConfigConstants.RedisKeys.REFRESH_TOKEN;

    @Override
    public void store(String tokenId, RefreshTokenData data, Duration ttl) {
        redisTemplate.opsForValue().set(
                PREFIX + tokenId,
                serialize(data),
                ttl
        );
    }

    @Override
    public Optional<RefreshTokenData> get(String tokenId) {
        String value = redisTemplate.opsForValue().get(PREFIX + tokenId);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(deserialize(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public void revoke(String tokenId) {
        redisTemplate.delete(PREFIX + tokenId);
    }

    private String serialize(RefreshTokenData data) {
        return data.getAuthIdentityId() + "|" + data.getUserId() + "|" + data.getIssuedAt();
    }

    private RefreshTokenData deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Invalid refresh token data");
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid refresh token format");
        }
        try {
            return new RefreshTokenData(
                    UUID.fromString(parts[0]),
                    UUID.fromString(parts[1]),
                    Instant.parse(parts[2])
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid refresh token data", e);
        }
    }
}
