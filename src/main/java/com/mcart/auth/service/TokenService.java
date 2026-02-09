package com.mcart.auth.service;

import com.mcart.auth.model.TokenResult;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

public interface TokenService {

    TokenResult issueTokens(
            UUID authIdentityId,
            UUID userId,
            HttpServletResponse response
    );

    TokenResult refreshTokens(
            String refreshTokenId,
            HttpServletResponse response
    );

}
