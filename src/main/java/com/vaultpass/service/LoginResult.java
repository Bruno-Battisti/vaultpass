package com.vaultpass.service;

import com.vaultpass.dto.auth.AuthResponse;
import com.vaultpass.dto.auth.LoginChallengeResponse;

/**
 * Login has two distinct outcomes on success: tokens issued outright, or a
 * short-lived challenge when the account has 2FA enabled. A sealed
 * interface makes the controller handle both explicitly instead of
 * overloading AuthResponse with a nullable/optional shape.
 */
public sealed interface LoginResult {

    record Success(AuthResponse authResponse) implements LoginResult {
    }

    record TwoFactorRequired(LoginChallengeResponse challenge) implements LoginResult {
    }
}
