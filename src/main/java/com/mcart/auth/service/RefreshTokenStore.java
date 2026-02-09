package com.mcart.auth.service;

import com.mcart.auth.model.RefreshTokenData;

import java.time.Duration;
import java.util.Optional;

public interface RefreshTokenStore {

    void store(String tokenId, RefreshTokenData data, Duration ttl);

    Optional<RefreshTokenData> get(String tokenId);

    void revoke(String tokenId);
}