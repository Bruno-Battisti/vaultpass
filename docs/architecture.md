# Arquitetura — VaultPass

## Fluxo de autenticação (sem 2FA)

```
POST /auth/register  → hash Argon2id → salva User → seed 7 categorias padrão
POST /auth/login      → valida senha (Argon2id) → gera access token (JWT, 15min)
                                                 → gera refresh token (opaco, hash SHA-256, 7 dias)
                                                 → registra sessão + auditoria LOGIN_SUCCESS
POST /auth/refresh    → rotaciona o refresh token (o antigo é revogado)
                       → reuso do token antigo revogado ⇒ revoga toda a sessão (TOKEN_REUSE_DETECTED)
POST /auth/logout     → revoga o refresh token informado (não exige access token)
```

## Fluxo de autenticação (com 2FA habilitado)

```
POST /auth/login
   senha correta + 2FA habilitado
   → NÃO emite tokens
   → emite challengeToken (JWT de 5min, claim purpose=2fa-challenge)
   → resposta: { "twoFactorRequired": true, "challengeToken": "..." }

POST /auth/2fa/verify { challengeToken, code }
   → valida o código TOTP (ou um código de recuperação, uso único)
   → só então emite access token + refresh token normalmente
```

O `challengeToken` nunca é aceito como access token: `JwtAuthenticationFilter` verifica a claim
`purpose` e recusa autenticar qualquer request que carregue esse tipo de token fora de
`/auth/2fa/verify`.

## Modelo de dados (visão geral)

```
users ──< credentials >── categories
  │
  ├──< refresh_tokens
  ├──< audit_logs
  ├──< user_sessions >── refresh_tokens (opcional)
  └──< two_factor_auth ──< two_factor_recovery_codes
```

- `credentials.encrypted_password` e `two_factor_auth.secret_encrypted` são cifrados com
  AES-256-GCM (mesma `EncryptionService`, chave `MASTER_ENCRYPTION_KEY`).
- `refresh_tokens.token_hash` e `two_factor_recovery_codes.code_hash` guardam apenas o hash
  (SHA-256) do valor real — o valor em claro nunca é persistido.
- Todas as tabelas usam UUID como chave primária (evita enumeração de recursos via ID sequencial).

## Camadas

```
Controller → Service → Repository (Spring Data JPA) → PostgreSQL
     │
     └─ DTOs de request/response (records) + MapStruct para mapear Entity → DTO
```

Regras de autorização (ownership check, "usuário só acessa o que é seu") vivem na camada de
Service, nunca no Controller — repositórios expõem `findByIdAndUserId(...)` em vez de
`findById(...)` para os agregados por usuário (Credential, Category, UserSession).
