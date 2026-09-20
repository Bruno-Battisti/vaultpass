# 🔐 VaultPass — Secure Password Management API

API REST de gerenciamento seguro de senhas em Java + Spring Boot, com cofre privado por usuário. O foco do projeto é segurança de verdade, não apenas CRUD: Argon2id, AES-256-GCM, refresh token rotativo com detecção de roubo de sessão, rate limiting, lockout de conta, auditoria e 2FA via TOTP.

`Java 21` `Spring Boot 4` `PostgreSQL 16` `Spring Security` `JWT` `Docker` `GitHub Actions`

## Features

- [x] Base de infraestrutura (Fase 1)
- [x] Cadastro, login e JWT (Fase 2)
- [x] Cofre de credenciais (CRUD + criptografia AES-256-GCM) (Fase 3)
- [x] Categorias, gerador de senhas e busca (Fase 4)
- [x] Refresh token persistido, rate limiting e auditoria (Fase 5)
- [x] 2FA (TOTP), sessões e detecção de atividade suspeita (Fase 6)
- [x] Deploy, CI/CD e documentação final (Fase 7)

## Architecture

```
src/main/java/com/vaultpass
├── controller     # AuthController, VaultController, CategoryController,
│                  # PasswordGeneratorController, SecurityController,
│                  # TwoFactorAuthController
├── service        # AuthService, VaultService, CategoryService,
│                  # PasswordGeneratorService, EncryptionService,
│                  # RefreshTokenService, AuditService, UserSessionService,
│                  # TwoFactorAuthService, SuspiciousActivityDetector
├── repository     # Spring Data JPA (um por agregado)
├── entity         # User, Credential, Category, RefreshToken, AuditLog,
│                  # TwoFactorAuth, TwoFactorRecoveryCode, UserSession
├── dto            # auth/ credential/ category/ password/ security/
│                  # common/ (ApiErrorResponse) + mapper/ (MapStruct)
├── security       # JwtService, JwtAuthenticationFilter, SecurityConfig,
│                  # RateLimitingFilter, CustomUserPrincipal
├── validation     # @StrongPassword
├── exception      # GlobalExceptionHandler + exceções de domínio
├── util           # ClientIpResolver
└── config         # EncryptionConfig, RateLimitConfig, AsyncConfig, OpenApiConfig
```

Migrations Flyway (`src/main/resources/db/migration`, V1–V10) são a única fonte de verdade do schema — `ddl-auto: validate` em todos os profiles.

Diagramas dos fluxos de autenticação (com e sem 2FA) e do modelo de dados: [`docs/architecture.md`](./docs/architecture.md).

## Technologies

| Camada | Tecnologia |
|---|---|
| Linguagem / Runtime | Java 21, Spring Boot 4.1 (Spring Framework 7) |
| Web | Spring Web MVC |
| Persistência | Spring Data JPA, Hibernate, PostgreSQL 16, Flyway |
| Segurança | Spring Security, Argon2id (`spring-security-crypto`), JJWT (JWT), Bouncy Castle |
| Criptografia do cofre | AES-256-GCM (`javax.crypto`) |
| 2FA | `dev.samstevens.totp` (RFC 6238 TOTP + QR code) |
| Rate limiting | Bucket4j + Caffeine (em memória) |
| Mapeamento DTO | MapStruct |
| Boilerplate | Lombok |
| Documentação de API | springdoc-openapi / Swagger UI |
| Testes | JUnit 5, Mockito, AssertJ, Testcontainers, MockMvc, Awaitility |
| Build | Maven (via Maven Wrapper — não requer Maven instalado) |
| Infraestrutura | Docker, Docker Compose, GitHub Actions |

## Installation

Pré-requisitos: Docker + Docker Compose. Java/Maven **não** são necessários para rodar via container — o `mvnw` só é preciso para rodar testes localmente fora do Docker.

```bash
cp .env.example .env
# edite o .env com valores reais (gere segredos com: openssl rand -base64 32)
docker compose up -d
```

API sobe em `http://localhost:8080`, Postgres em `localhost:5433` (mapeado para 5433 no host para não colidir com outro Postgres local; a aplicação e outros containers continuam falando com o Postgres na porta interna 5432 via rede do Compose).

## Environment Variables

| Variável | Descrição | Obrigatória |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Conexão com o Postgres | Sim |
| `SPRING_PROFILES_ACTIVE` | Profile ativo (`dev`/`prod`) | Não (default `dev`) |
| `JWT_SECRET` | Chave HMAC do JWT, Base64 decodificando para ≥32 bytes (`openssl rand -base64 32`) | Sim |
| `MASTER_ENCRYPTION_KEY` | Chave AES-256 para o cofre e para os secrets de 2FA, Base64 de exatamente 32 bytes | Sim |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas no CORS (separadas por vírgula) | Não (default `http://localhost:3000`) |

Todas as variáveis usadas pela aplicação estão listadas em [`.env.example`](./.env.example) — nenhum valor real, apenas placeholders.

## API Documentation

Swagger UI: `http://localhost:8080/swagger-ui.html` · OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### Auth

```bash
# Cadastro
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Bruno","email":"bruno@example.com","password":"SenhaForte123!"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"bruno@example.com","password":"SenhaForte123!"}'

# Renovar access token (rotaciona o refresh token)
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refresh-token-do-login>"}'

# Logout (revoga o refresh token; basta possuir o token, não precisa do access token)
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" -d '{"refreshToken":"<refresh-token>"}'
```

A senha mestra exige no mínimo 12 caracteres com maiúscula, minúscula, número e símbolo. O `access_token` dura 15 minutos; o `refresh_token` dura 7 dias, é opaco (não é JWT), persistido como hash SHA-256 e **rotacionado a cada uso** — reutilizar um refresh token já trocado é tratado como roubo de sessão e revoga todos os tokens ativos daquele usuário.

### Vault

Todos os endpoints exigem `Authorization: Bearer <accessToken>`.

```bash
# Criar credencial (categoryId é opcional)
curl -X POST http://localhost:8080/api/v1/vault \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"title":"GitHub","username":"bruno","password":"Sup3rS3cret!","url":"https://github.com","notes":"conta principal"}'

# Listar (paginado, com busca e filtro por categoria)
curl -H "Authorization: Bearer <token>" "http://localhost:8080/api/v1/vault?search=github&categoryId={id}"

# Ver detalhe (sem a senha) / Revelar a senha em claro (decifra sob demanda)
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/vault/{id}
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/vault/{id}/password

# Atualizar (password null/omitido mantém a senha atual) / Excluir
curl -X PUT http://localhost:8080/api/v1/vault/{id} -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"title":"GitHub","username":"bruno","url":"https://github.com","notes":"atualizado"}'
curl -X DELETE -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/vault/{id}
```

A senha é cifrada com AES-256-GCM (IV aleatório por registro) antes de ir para o banco — nunca trafega em claro em `GET /vault` ou `GET /vault/{id}`, só no endpoint dedicado `/password`. Tentar acessar a credencial de outro usuário retorna `404` (nunca `403`, para não confirmar a um atacante que o ID existe).

### Categorias

Toda conta nova recebe automaticamente 7 categorias padrão (`SOCIAL, TRABALHO, ESTUDOS, FINANCEIRO, JOGOS, DESENVOLVIMENTO, OUTROS`).

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/categories
curl -X POST http://localhost:8080/api/v1/categories -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"name":"Streaming"}'
curl -X PUT http://localhost:8080/api/v1/categories/{id} -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"name":"Streaming Renomeado"}'
curl -X DELETE -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/categories/{id}
```

### Gerador de senhas

```bash
# Gerar senha (SecureRandom, nunca java.util.Random)
curl -X POST http://localhost:8080/api/v1/password/generate -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"length":24,"uppercase":true,"lowercase":true,"numbers":true,"symbols":true}'

# Calcular força (sempre POST, nunca GET — senha nunca vai para a URL/logs)
curl -X POST http://localhost:8080/api/v1/password/strength -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"password":"Xk9#mQ2$vLp8@wRt"}'
```

### Segurança da conta

```bash
# Historico de atividade (login, logout, alteracoes no cofre, bloqueios etc.)
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/security/activity

# Sessoes ativas (cada refresh token vira uma "sessao" rotulada por IP/User-Agent)
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/security/sessions
curl -X DELETE -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/security/sessions/{id}
curl -X DELETE -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/security/sessions
```

- **Rate limiting** por IP: `/auth/login` 5/min, `/auth/register` 3/hora, `/auth/refresh` 20/min — excedeu, `429` com header `Retry-After`.
- **Lockout de conta**: 5 tentativas de login incorretas seguidas bloqueiam a conta por 15 minutos (`423 Locked`), mesmo com a senha correta.
- **Auditoria assíncrona**: `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGIN_NEW_DEVICE`, `LOGOUT`, `PASSWORD_CREATED/UPDATED/DELETED/VIEWED`, `ACCOUNT_LOCKED`, `TOKEN_REUSE_DETECTED`.
- Revogar uma sessão revoga de fato o refresh token associado a ela — não é só cosmético.

### 2FA (TOTP)

```bash
# 1. Configurar (retorna o secret e o QR code em base64 — só aparece aqui)
curl -X POST http://localhost:8080/api/v1/auth/2fa/setup -H "Authorization: Bearer <token>"

# 2. Confirmar com o primeiro código do app autenticador (retorna 10 códigos de recuperação, só aparecem aqui)
curl -X POST http://localhost:8080/api/v1/auth/2fa/enable -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"code":"123456"}'

# 3. Login passa a devolver um desafio em vez de tokens: {"twoFactorRequired":true,"challengeToken":"..."}
curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"bruno@example.com","password":"SenhaForte123!"}'

# 4. Completar o login com o código do app (ou um código de recuperação, uso único)
curl -X POST http://localhost:8080/api/v1/auth/2fa/verify -H "Content-Type: application/json" \
  -d '{"challengeToken":"<challenge>","code":"123456"}'

# Desabilitar exige a senha mestra novamente
curl -X POST http://localhost:8080/api/v1/auth/2fa/disable -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"password":"SenhaForte123!"}'
```

O `challengeToken` é um JWT de 5 minutos com propósito exclusivo (`purpose: 2fa-challenge`) — nunca é aceito como access token em nenhum outro endpoint.

## Security

Argon2id (senha mestra) · AES-256-GCM (senhas do cofre e secrets TOTP) · JWT + refresh token rotativo com detecção de reuso · rate limiting · lockout de conta · auditoria · 2FA via TOTP. Checklist consolidado:

- [x] Nenhum segredo hardcoded ou versionado — tudo via variável de ambiente (`.env`, nunca commitado).
- [x] Senha mestra: Argon2id, irreversível, nunca logada (`@ToString.Exclude`).
- [x] Senhas do cofre e secrets de 2FA: AES-256-GCM, IV aleatório por registro (nunca reutilizado).
- [x] Nenhuma senha em claro fora dos endpoints explícitos de revelação (`/vault/{id}/password`).
- [x] JWT assinado com chave ≥256 bits, validada no boot da aplicação (falha o startup se fraca).
- [x] Refresh tokens armazenados como hash SHA-256, com rotação e detecção de reuso (revoga toda a sessão).
- [x] Rate limiting em `/auth/login`, `/auth/register`, `/auth/refresh`.
- [x] Lockout de conta após tentativas de login falhas consecutivas.
- [x] Ownership check (`user_id == authenticated_user.id`) em todo acesso a vault/categorias/sessões — retorna 404, nunca 403 (evita confirmar existência do recurso a um atacante).
- [x] Mensagens de erro de autenticação genéricas (sem enumeração de conta), com mitigação de timing attack.
- [x] CORS restrito a origens explícitas configuráveis.
- [x] Headers de segurança HTTP (`X-Content-Type-Options`, `X-Frame-Options`, sem header revelando stack).
- [x] 2FA: secret nunca reexposto após o setup, disable exige senha, recovery codes hasheados e de uso único.
- [x] Auditoria cobrindo todos os eventos sensíveis definidos.
- [x] Container roda como usuário não-root.
- [x] Expiração de tokens curta e configurável (access 15min / refresh 7 dias).

## Tests

```bash
./mvnw clean verify
```

63 testes unitários (JUnit + Mockito) + 22 testes de integração (Testcontainers com Postgres real, via Maven Failsafe) cobrindo cada fluxo de segurança: IDOR, timing attack, rotação/reuso de refresh token, lockout, rate limiting, 2FA completo (setup → enable → login → verify → recovery code) e auditoria.

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`) roda em todo push/PR para `main`: build + testes unitários e de integração (Testcontainers usa o Docker já disponível no runner) + build da imagem Docker de validação. `Dockerfile` multi-stage (build com Maven, runtime `eclipse-temurin:21-jre-alpine`, usuário não-root, `HEALTHCHECK` batendo em `/actuator/health`).

## Roadmap

Projeto completo, das 7 fases planejadas. Ver [`vaultpass-planejamento.md`](./vaultpass-planejamento.md) para o rascunho original que deu origem ao plano de execução fase a fase.
