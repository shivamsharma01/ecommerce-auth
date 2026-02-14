package com.mcart.auth.mapper;

import com.mcart.auth.dto.LoginResponse;
import com.mcart.auth.dto.PasswordSignupRequest;
import com.mcart.auth.dto.SocialLoginRequest;
import com.mcart.auth.dto.SocialSignupEventPayload;
import com.mcart.auth.dto.UserSignupEventPayload;
import com.mcart.auth.model.AuthProviderType;
import com.mcart.auth.model.TokenResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.UUID;

/**
 * MapStruct mapper for auth-related DTO conversions.
 */
@Mapper(componentModel = "spring")
public interface AuthMapper {

    /**
     * Maps TokenResult to LoginResponse.
     */
    LoginResponse toLoginResponse(TokenResult tokenResult);

    /**
     * Maps PasswordSignupRequest to UserSignupEventPayload for outbox events.
     */
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "providerType", constant = "PASSWORD")
    UserSignupEventPayload toUserSignupPayload(PasswordSignupRequest request, UUID userId);

    /**
     * Maps SocialLoginRequest to SocialSignupEventPayload for outbox events.
     */
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "providerType", source = "request.providerType", qualifiedByName = "providerTypeToString")
    SocialSignupEventPayload toSocialSignupPayload(SocialLoginRequest request, UUID userId);

    @Named("providerTypeToString")
    default String providerTypeToString(AuthProviderType providerType) {
        return providerType != null ? providerType.name() : null;
    }
}
