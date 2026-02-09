package com.mcart.auth.controller;

import com.mcart.auth.model.ResendVerificationRequest;
import com.mcart.auth.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(
            @RequestParam("token") String token
    ) {
        System.out.println("yolo: "+token);
        emailVerificationService.verifyEmail(token);
        return ResponseEntity.ok("Email verified successfully");
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
