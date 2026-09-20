# 🔐 VaultPass — Secure Password Management API

API REST de gerenciamento seguro de senhas em Java + Spring Boot, com cofre privado por usuário.

`Java 21` `Spring Boot 4` `PostgreSQL` `Spring Security` `JWT` `Docker`

## Features

- [x] Base de infraestrutura (Fase 1)
- [x] Cadastro, login e JWT (Fase 2)
- [x] Cofre de credenciais (CRUD + criptografia AES-256-GCM) (Fase 3)
- [x] Categorias, gerador de senhas e busca (Fase 4)
- [ ] Refresh token persistido, rate limiting e auditoria (Fase 5)
- [ ] 2FA (TOTP), sessões e detecção de atividade suspeita (Fase 6)
- [ ] Deploy, CI/CD e documentação final (Fase 7)

## Architecture

```
src/main/java/com/vaultpass
├── controller
├── service
├── repository
├── entity
├── dto
├── security
├── exception
└── config
```

## Technologies

Ver seção Features acima e o roadmap completo em [`vaultpass-planejamento.md`](./vaultpass-planejamento.md).

## Installation

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
| `JWT_SECRET` | Chave HMAC do JWT (≥32 bytes, Base64) | A partir da Fase 2 |
| `MASTER_ENCRYPTION_KEY` | Chave AES-256 para o cofre (32 bytes, Base64) | Sim |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas no CORS | Não |

## API Documentation

Swagger UI: `http://localhost:8080/swagger-ui.html`

### Auth (Fase 2)

```bash
# Cadastro
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Bruno","email":"bruno@example.com","password":"SenhaForte123!"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"bruno@example.com","password":"SenhaForte123!"}'

# Renovar access token
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refresh-token-do-login>"}'
```

A senha mestra exige no mínimo 12 caracteres com maiúscula, minúscula, número e símbolo. O `access_token` dura 15 minutos; o `refresh_token` (ainda stateless nesta fase — persistência e rotação chegam na Fase 5) dura 7 dias.

### Vault (Fase 3)

Todos os endpoints exigem `Authorization: Bearer <accessToken>`.

```bash
# Criar credencial
curl -X POST http://localhost:8080/api/v1/vault \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"title":"GitHub","username":"bruno","password":"Sup3rS3cret!","url":"https://github.com","notes":"conta principal"}'

# Listar (paginado)
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/vault

# Ver detalhe (sem a senha)
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/vault/{id}

# Revelar a senha em claro (decifra sob demanda)
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/vault/{id}/password

# Atualizar (password null/omitido mantém a senha atual)
curl -X PUT http://localhost:8080/api/v1/vault/{id} \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"title":"GitHub","username":"bruno","url":"https://github.com","notes":"atualizado"}'

# Excluir
curl -X DELETE -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/vault/{id}
```

A senha é cifrada com AES-256-GCM (IV aleatório por registro) antes de ir para o banco — nunca trafega em claro em `GET /vault` ou `GET /vault/{id}`, só no endpoint dedicado `/password`. Tentar acessar a credencial de outro usuário retorna `404` (nunca `403`, para não confirmar a um atacante que o ID existe).

### Categorias (Fase 4)

Toda conta nova recebe automaticamente 7 categorias padrão (`SOCIAL, TRABALHO, ESTUDOS, FINANCEIRO, JOGOS, DESENVOLVIMENTO, OUTROS`).

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/categories
curl -X POST http://localhost:8080/api/v1/categories -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"name":"Streaming"}'
curl -X PUT http://localhost:8080/api/v1/categories/{id} -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"name":"Streaming Renomeado"}'
curl -X DELETE -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/categories/{id}
```

Uma credencial pode ser associada a uma categoria via `categoryId` no corpo de `POST`/`PUT /vault`. Usar o ID de uma categoria de outro usuário retorna `404`.

### Gerador de senhas e busca (Fase 4)

```bash
# Gerar senha (SecureRandom, nunca java.util.Random)
curl -X POST http://localhost:8080/api/v1/password/generate -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"length":24,"uppercase":true,"lowercase":true,"numbers":true,"symbols":true}'

# Calcular força (sempre POST, nunca GET — senha nunca vai para a URL/logs)
curl -X POST http://localhost:8080/api/v1/password/strength -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"password":"Xk9#mQ2$vLp8@wRt"}'

# Buscar no cofre por título/usuário e/ou filtrar por categoria
curl -H "Authorization: Bearer <token>" "http://localhost:8080/api/v1/vault?search=github&categoryId={id}"
```

## Security

Ver checklist de segurança completa no plano do projeto. Resumo: Argon2id para senha mestra, AES-256-GCM para senhas do cofre, JWT com refresh token rotativo, 2FA via TOTP, rate limiting e auditoria de eventos sensíveis.

## Tests

```bash
./mvnw clean verify
```

## Roadmap

Ver [`vaultpass-planejamento.md`](./vaultpass-planejamento.md) para o rascunho original e o plano de execução fase a fase.
