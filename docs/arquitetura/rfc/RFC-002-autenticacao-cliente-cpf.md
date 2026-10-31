# RFC-002 — Autenticação do cliente por CPF

**Status:** aceita  
**Data:** 2026-10  
**Decisão registrada em:** [ADR-004](../adr/ADR-004-jwt-cliente-validado-no-core.md), [ADR-005](../adr/ADR-005-lambda-le-banco-direto.md)

## Contexto

O enunciado pede que o cliente se identifique pelo **CPF** numa função serverless que:

1. valida o CPF;
2. consulta se o cliente existe (e está ativo);
3. devolve um JWT para consumir as APIs protegidas pelo API Gateway.

O core já tinha autenticação JWT própria para **funcionários** (`POST /api/signin`, papéis
`ADMIN`/`ATTENDANT`/`MECHANIC`, chave RSA do core). As duas formas de login precisam conviver.

## Perguntas que esta RFC responde

1. Onde emitir o token do cliente?
2. Onde validar o token nas rotas protegidas?
3. Como a função descobre se o cliente existe?

## Alternativas

### 1. Emissão do token

| Opção | Avaliação |
|---|---|
| **Lambda própria (Java 21) assinando RS256** | Atende o enunciado literalmente; mesma biblioteca JOSE do core |
| Amazon Cognito | Exige cadastro de usuário/senha no Cognito, não autentica só por CPF; IAM do lab limita |
| Endpoint novo no core | Não é serverless — não atende o requisito |

### 2. Validação nas rotas

| Opção | Avaliação |
|---|---|
| **Resource Server no core (Spring Security)** | Já existia para funcionários; aceita os dois emissores no mesmo decoder; regras por papel ficam junto do código |
| JWT Authorizer nativo do API Gateway | Um authorizer único em `/core` bloquearia `/api/signin`, o health e os endpoints públicos de OTP, e não conhece o token de funcionário |
| Lambda Authorizer customizado | Mais uma função, mais latência e cold start, e a regra por papel continuaria no core |

### 3. Consulta do cliente

| Opção | Avaliação |
|---|---|
| **Lambda lê a tabela `customers` direto (somente `SELECT`)** | Uma ida ao banco, sem acoplar a Lambda à disponibilidade do core |
| Lambda chama uma API interna do core | Exige endpoint interno sem autenticação ou credencial de serviço; login do cliente cai junto com o core |

## Decisão

- A **Lambda** valida formato e dígitos do CPF, consulta `customers` e emite um JWT **RS256**
  com `role=CUSTOMER`, `sub` = UUID do cliente, validade de 30 minutos. A chave pública é publicada
  em `/auth/.well-known/jwks.json`.
- O **core** valida o token (assinatura via JWKS, `iss`, `aud`, `exp`, papel). Em `AUTH_MODE=BOTH`
  aceita os tokens de funcionário e de cliente.
- O **API Gateway** só roteia (`/auth` → Lambda, `/core` → EKS). Não usamos JWT Authorizer.
- Cliente inexistente e cliente inativo recebem a **mesma** resposta (`401 INVALID_CREDENTIALS`)
  para não revelar quais CPFs estão cadastrados.

## Consequências

- O token não carrega CPF, e-mail nem telefone (JWT é assinado, não cifrado).
- Rotas do cliente ficam isoladas em `/api/customer/**`; token de cliente em rota de funcionário → 403.
- O `iss` do token é a URL do Gateway, que muda a cada recriação do ambiente; o deploy do core
  lê o valor atual da conta a cada execução.
- Rotação de chave: trocar o secret da Lambda e o `kid`; o core relê o JWKS sem redeploy.
- Se no futuro o Gateway precisar barrar tokens antes da aplicação, o endpoint OIDC já publicado
  permite ligar um authorizer só nas rotas `/core/api/customer/*`.
