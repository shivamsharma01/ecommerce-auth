package com.mcart.auth.controller;

import com.mcart.auth.exception.UnauthorizedException;
import com.mcart.auth.model.ResendVerificationRequest;
import com.mcart.auth.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @Value("${auth.verification.base-url}")
    private String verificationBaseUrl;

    /**
     * Full URL to send users after successful verification (e.g. https://mcart.space/signup?...).
     * If empty, {@code auth.verification.base-url + "/signup"} is used.
     */
    @Value("${auth.verification.post-verify-redirect-url:}")
    private String postVerifyRedirectUrl;

    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam("token") String token) {
        try {
            emailVerificationService.verifyEmail(token);
            return ResponseEntity.status(HttpStatus.FOUND).location(successUri()).build();
        } catch (UnauthorizedException ex) {
            return ResponseEntity.status(HttpStatus.FOUND).location(errorUri(ex.getMessage())).build();
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(errorUri("Verification failed. Please request a new link."))
                    .build();
        }
    }

    private String signupPageUrl() {
        if (postVerifyRedirectUrl != null && !postVerifyRedirectUrl.isBlank()) {
            return postVerifyRedirectUrl.trim();
        }
        String base = verificationBaseUrl.endsWith("/")
                ? verificationBaseUrl.substring(0, verificationBaseUrl.length() - 1)
                : verificationBaseUrl;
        return base + "/signup";
    }

    private URI successUri() {
        String base = signupPageUrl();
        String sep = base.contains("?") ? "&" : "?";
        return URI.create(base + sep + "verified=1");
    }

    private URI errorUri(String message) {
        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
        String base = signupPageUrl();
        String sep = base.contains("?") ? "&" : "?";
        return URI.create(base + sep + "error=" + encoded);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerification(
            @RequestBody @Valid ResendVerificationRequest request
    ) {
        emailVerificationService.resendVerification(request.getEmail());
        return ResponseEntity.ok(
                "If the email exists, a verification link has been sent"
        );
    }
}
