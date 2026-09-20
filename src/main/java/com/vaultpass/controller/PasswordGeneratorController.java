package com.vaultpass.controller;

import com.vaultpass.dto.password.PasswordGenerateRequest;
import com.vaultpass.dto.password.PasswordGenerateResponse;
import com.vaultpass.dto.password.PasswordStrengthRequest;
import com.vaultpass.dto.password.PasswordStrengthResponse;
import com.vaultpass.service.PasswordGeneratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/password")
@RequiredArgsConstructor
public class PasswordGeneratorController {

    private final PasswordGeneratorService passwordGeneratorService;

    @PostMapping("/generate")
    public ResponseEntity<PasswordGenerateResponse> generate(@Valid @RequestBody PasswordGenerateRequest request) {
        return ResponseEntity.ok(new PasswordGenerateResponse(passwordGeneratorService.generate(request)));
    }

    @PostMapping("/strength")
    public ResponseEntity<PasswordStrengthResponse> strength(@Valid @RequestBody PasswordStrengthRequest request) {
        return ResponseEntity.ok(passwordGeneratorService.calculateStrength(request.password()));
    }
}
