# 🔐 Projeto: VaultPass

Um sistema de gerenciamento seguro de senhas, desenvolvido em **Java + Spring Boot**, onde cada usuário possui um cofre privado para armazenar credenciais, gerar senhas e organizar seus acessos.

## 1. Objetivo

Criar uma API REST que permita ao usuário:

* criar uma conta;
* fazer login;
* possuir um cofre privado;
* armazenar credenciais;
* editar e excluir credenciais;
* pesquisar credenciais;
* organizar senhas por categorias;
* gerar senhas fortes;
* controlar sessões;
* registrar atividades de segurança.

O foco principal do projeto será **segurança**, e não apenas CRUD.

---

# 2. Stack

### Backend

```text
Java 21
Spring Boot
Spring Web
Spring Security
Spring Data JPA
Hibernate
Maven
```

### Banco

```text
PostgreSQL
Flyway
```

### Segurança

```text
JWT
BCrypt ou Argon2
AES-256-GCM
Refresh Token
Rate Limiting
```

### Infraestrutura

```text
Docker
Docker Compose
GitHub Actions
```

### Documentação

```text
Swagger / OpenAPI
README.md
```

### Testes

```text
JUnit
Mockito
Spring Boot Test
Testcontainers
```

---

# 3. Funcionalidades

## 👤 Usuários

O sistema terá três operações principais inicialmente:

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
```

O cadastro:

```json
{
  "name": "Bruno",
  "email": "bruno@email.com",
  "password": "senha-mestra"
}
```

Depois do login, o servidor fornece um access token.

---

# 4. Cofre de senhas

Cada usuário possui seu próprio Vault.

Exemplo:

```text
Meu Cofre

├── Google
├── GitHub
├── Discord
├── Instagram
├── Steam
└── Banco
```

Uma credencial poderia possuir:

```json
{
  "title": "GitHub",
  "username": "usuario@email.com",
  "password": "senha-super-secreta",
  "url": "https://github.com",
  "notes": "Conta principal"
}
```

Endpoints:

```text
GET    /api/vault
POST   /api/vault
GET    /api/vault/{id}
PUT    /api/vault/{id}
DELETE /api/vault/{id}
```

---

# 5. Categorias

O usuário poderá organizar as credenciais:

```text
SOCIAL
TRABALHO
ESTUDOS
FINANCEIRO
JOGOS
DESENVOLVIMENTO
OUTROS
```

Exemplo:

```text
GitHub
Categoria: DESENVOLVIMENTO

Discord
Categoria: SOCIAL

Steam
Categoria: JOGOS
```

---

# 6. Gerador de senhas

Uma das funcionalidades mais legais.

Endpoint:

```text
POST /api/password/generate
```

Entrada:

```json
{
  "length": 24,
  "uppercase": true,
  "lowercase": true,
  "numbers": true,
  "symbols": true
}
```

Resposta:

```json
{
  "password": "X7@pL9#qR2!mK8$zT4&nW6"
}
```

Também pode existir um cálculo de força:

```text
Muito fraca
Fraca
Moderada
Forte
Muito forte
```

---

# 7. Segurança da senha-mestra

Essa é uma parte **fundamental**.

A senha utilizada para fazer login **não deve ser armazenada diretamente no banco**.

Por exemplo:

```text
senha digitada
      ↓
Argon2
      ↓
hash
      ↓
PostgreSQL
```

Mesmo que alguém consiga visualizar o banco, não encontrará a senha original.

---

# 8. Criptografia das senhas armazenadas

Aqui existe uma diferença importante.

A senha de login pode ser armazenada como **hash**, porque você não precisa recuperar a senha original.

Já as senhas do cofre precisam ser **recuperáveis** para serem exibidas ao usuário.

Portanto:

```text
Senha de login
      ↓
HASH
      ↓
Banco
```

Enquanto:

```text
Senha do GitHub
      ↓
CRIPTOGRAFIA
      ↓
Banco
```

Quando o usuário solicita a senha:

```text
Banco
 ↓
senha criptografada
 ↓
descriptografia
 ↓
usuário
```

Para isso, você poderia utilizar **AES-256-GCM**.

---

# 9. Arquitetura

Eu faria algo próximo de:

```text
src/main/java/com/vaultpass
│
├── controller
│   ├── AuthController
│   ├── VaultController
│   ├── CategoryController
│   └── PasswordGeneratorController
│
├── service
│   ├── AuthService
│   ├── VaultService
│   ├── CategoryService
│   ├── PasswordGeneratorService
│   └── EncryptionService
│
├── repository
│   ├── UserRepository
│   ├── CredentialRepository
│   └── CategoryRepository
│
├── entity
│   ├── User
│   ├── Credential
│   └── Category
│
├── dto
│   ├── auth
│   ├── credential
│   └── user
│
├── security
│   ├── JwtService
│   ├── SecurityConfig
│   └── JwtAuthenticationFilter
│
├── exception
│
└── config
```

Isso já deixa o projeto com uma aparência bastante profissional.

---

# 10. Banco de dados

Inicialmente:

```text
users
│
├── id
├── name
├── email
├── password_hash
├── created_at
└── updated_at


credentials
│
├── id
├── user_id
├── category_id
├── title
├── username
├── encrypted_password
├── url
├── notes
├── created_at
└── updated_at


categories
│
├── id
├── user_id
├── name
└── created_at
```

Depois podemos adicionar:

```text
refresh_tokens
audit_logs
user_sessions
two_factor_auth
```

---

# 11. Auditoria

Uma funcionalidade muito boa para demonstrar preocupação com segurança.

Registrar eventos como:

```text
LOGIN_SUCCESS
LOGIN_FAILED
PASSWORD_CREATED
PASSWORD_UPDATED
PASSWORD_DELETED
PASSWORD_VIEWED
LOGOUT
```

Por exemplo:

```text
20/09/2026 14:30
LOGIN_SUCCESS
IP: xxx.xxx.xxx.xxx
```

O usuário poderia visualizar:

```text
Atividade recente

✓ Login realizado
✓ Nova credencial criada
✓ Credencial GitHub visualizada
✓ Senha alterada
```

---

# 12. Controle de acesso

Um usuário **nunca pode acessar o cofre de outro usuário**.

Por exemplo:

```text
Usuário A
   ↓
GET /api/vault/15
```

O backend deve verificar:

```text
credential.user_id == authenticated_user.id
```

Caso contrário:

```text
403 Forbidden
```

Essa é uma regra de segurança importante para demonstrar no projeto.

---

# 13. Refresh Token

Em vez de manter o usuário autenticado indefinidamente:

```text
Access Token
    ↓
curta duração
```

e:

```text
Refresh Token
    ↓
usado para obter novo Access Token
```

Fluxo:

```text
LOGIN
  ↓
Access Token + Refresh Token
  ↓
API
  ↓
Access Token expira
  ↓
Refresh Token
  ↓
Novo Access Token
```

---

# 14. 2FA — versão avançada

Depois que o sistema básico estiver funcionando, adicionar:

```text
Google Authenticator
       ↓
TOTP
       ↓
código de 6 dígitos
```

Login:

```text
Email
   ↓
Senha
   ↓
Código 2FA
   ↓
Acesso ao Vault
```

---

# 15. Docker

O projeto poderia iniciar com:

```bash
docker compose up -d
```

E levantar:

```text
VaultPass API
PostgreSQL
```

Por exemplo:

```text
localhost:8080 → API
localhost:5432 → PostgreSQL
```

---

# 16. Swagger

Documentar todos os endpoints:

```text
/api/auth
/api/vault
/api/categories
/api/password
/api/security
```

Assim alguém que entrar no seu GitHub consegue executar a API facilmente.

---

# 17. Testes

Não deixaria os testes para o final.

Criaria testes para:

```text
AuthService
VaultService
EncryptionService
PasswordGeneratorService
```

E testes de integração:

```text
POST /register
POST /login
POST /vault
GET /vault
DELETE /vault
```

Uma meta interessante seria ter **boa cobertura dos serviços e das regras de segurança**, sem buscar cobertura de 100% artificialmente.

---

# 18. Roadmap

Eu desenvolveria em etapas:

### Fase 1 — Base

```text
✓ Criar projeto Spring Boot
✓ Configurar PostgreSQL
✓ Docker
✓ JPA
✓ Flyway
```

### Fase 2 — Usuários

```text
✓ Cadastro
✓ Login
✓ BCrypt/Argon2
✓ JWT
✓ Spring Security
```

### Fase 3 — Vault

```text
✓ Criar credencial
✓ Listar credenciais
✓ Editar
✓ Excluir
✓ Buscar
```

### Fase 4 — Recursos

```text
✓ Categorias
✓ Gerador de senhas
✓ Validação de força
✓ Pesquisa
```

### Fase 5 — Segurança

```text
✓ Criptografia AES-256-GCM
✓ Refresh Token
✓ Rate limiting
✓ Auditoria
```

### Fase 6 — Avançado

```text
✓ 2FA
✓ Sessões
✓ Detecção de atividades suspeitas
✓ Testcontainers
```

### Fase 7 — Deploy

```text
✓ GitHub Actions
✓ Docker
✓ Deploy
✓ README profissional
```

---

# 19. Estrutura final do GitHub

Eu deixaria o repositório mais ou menos assim:

```text
vaultpass/
│
├── src/
├── .github/
│   └── workflows/
│       └── ci.yml
│
├── docker/
├── docs/
│
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── .env.example
├── .gitignore
└── README.md
```

No README:

```text
# 🔐 VaultPass

Secure Password Management API

Java 21
Spring Boot
PostgreSQL
Spring Security
JWT
Docker
```

Depois:

```text
## Features
## Architecture
## Technologies
## Installation
## Environment Variables
## API Documentation
## Security
## Tests
## Roadmap
```

### 🎯 O resultado

No final, você teria um projeto que demonstra:

**Java → Spring Boot → REST API → PostgreSQL → JPA → JWT → Spring Security → criptografia → Docker → testes → CI/CD → documentação.**

Para um projeto pessoal de GitHub, essa é uma ótima escolha porque dá para começar relativamente simples e ir aumentando a complexidade sem precisar abandonar o projeto.
