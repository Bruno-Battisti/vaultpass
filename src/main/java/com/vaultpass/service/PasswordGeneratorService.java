package com.vaultpass.service;

import com.vaultpass.dto.password.PasswordGenerateRequest;
import com.vaultpass.dto.password.PasswordStrength;
import com.vaultpass.dto.password.PasswordStrengthResponse;
import com.vaultpass.exception.InvalidRequestException;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PasswordGeneratorService {

    // Ambiguous characters (I, l, 1, O, 0, etc.) are excluded so generated
    // passwords are easier for a human to transcribe correctly if ever needed.
    private static final String UPPERCASE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWERCASE_CHARS = "abcdefghijkmnpqrstuvwxyz";
    private static final String NUMBER_CHARS = "23456789";
    private static final String SYMBOL_CHARS = "!@#$%^&*()-_=+[]{}";

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[^A-Za-z0-9]");

    // Never java.util.Random for password material — it's predictable and
    // unsuitable for anything security-sensitive.
    private final SecureRandom secureRandom = new SecureRandom();

    private Set<String> commonPasswords;

    @PostConstruct
    void init() {
        this.commonPasswords = loadCommonPasswords();
    }

    public String generate(PasswordGenerateRequest request) {
        StringBuilder pool = new StringBuilder();
        if (request.uppercase()) {
            pool.append(UPPERCASE_CHARS);
        }
        if (request.lowercase()) {
            pool.append(LOWERCASE_CHARS);
        }
        if (request.numbers()) {
            pool.append(NUMBER_CHARS);
        }
        if (request.symbols()) {
            pool.append(SYMBOL_CHARS);
        }

        if (pool.isEmpty()) {
            throw new InvalidRequestException("At least one character class must be selected");
        }

        StringBuilder password = new StringBuilder(request.length());
        for (int i = 0; i < request.length(); i++) {
            password.append(pool.charAt(secureRandom.nextInt(pool.length())));
        }
        return password.toString();
    }

    public PasswordStrengthResponse calculateStrength(String password) {
        int score = 0;
        if (password.length() >= 8) {
            score += 15;
        }
        if (password.length() >= 12) {
            score += 15;
        }
        if (password.length() >= 16) {
            score += 10;
        }
        if (UPPERCASE_PATTERN.matcher(password).find()) {
            score += 15;
        }
        if (LOWERCASE_PATTERN.matcher(password).find()) {
            score += 15;
        }
        if (DIGIT_PATTERN.matcher(password).find()) {
            score += 15;
        }
        if (SYMBOL_PATTERN.matcher(password).find()) {
            score += 15;
        }
        if (commonPasswords.contains(password.toLowerCase())) {
            // A password on the common list is weak no matter how it scores
            // on length/character-class diversity (e.g. "Password123!").
            score = Math.min(score, 20);
        }
        score = Math.min(score, 100);

        return new PasswordStrengthResponse(strengthFor(score), score);
    }

    private PasswordStrength strengthFor(int score) {
        if (score < 30) {
            return PasswordStrength.MUITO_FRACA;
        }
        if (score < 50) {
            return PasswordStrength.FRACA;
        }
        if (score < 70) {
            return PasswordStrength.MODERADA;
        }
        if (score < 90) {
            return PasswordStrength.FORTE;
        }
        return PasswordStrength.MUITO_FORTE;
    }

    private Set<String> loadCommonPasswords() {
        try (var in = getClass().getResourceAsStream("/common-passwords.txt")) {
            if (in == null) {
                return Set.of();
            }
            try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .map(String::toLowerCase)
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
