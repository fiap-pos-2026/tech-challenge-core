# ADR-004 — Formato do JWT de cliente e validação no core (`AUTH_MODE=BOTH`)

**Status:** aceito  
**Data:** 2026-10  
**Origem:** [RFC-002](../rfc/RFC-002-autenticacao-cliente-cpf.md)

## Contexto

Dois tipos de usuário acessam o core: funcionários (login e senha) e clientes (CPF via Lambda).
Os tokens vêm de emissores diferentes, assinados com chaves diferentes, e precisam conviver
nas mesmas rotas da aplicação.

## Decisão

### Formato do token de cliente (emitido pela Lambda)

| Campo | Valor |
|---|---|
| Algoritmo | RS256, com `kid` no header |
| `iss` | URL do Gateway + `/auth` |
| `aud` | `tech-challenge-core` |
| `sub` | UUID público do cliente (nunca o CPF) |
| `role` | `CUSTOMER` |
| `name` | nome do cliente |
| `iat` / `exp` / `jti` | emissão, expiração em 30 min, id do token |

Dados pessoais (CPF, e-mail, telefone) **não** entram no token. A chave pública é publicada em
`/auth/.well-known/jwks.json` e `/auth/.well-known/openid-configuration`.

### Validação no core

- `AUTH_MODE=BOTH` é o padrão. Um decoder composto tenta as duas validações em sequência:
  1. token de funcionário — chave RSA do core, `iss = tech-challenge-app`;
  2. se falhar, token de cliente — chave buscada no JWKS da Lambda, `iss` = URL do Gateway `/auth`
     e `aud = tech-challenge-core`.

  Só é recusado se as duas falharem. Depois de decodificado, o `iss` define se o token segue o
  fluxo de cliente (`ROLE_CUSTOMER`) ou o de funcionário (papel do usuário cadastrado).
- `/api/customer/**` exige `ROLE_CUSTOMER`; o cliente só vê os próprios dados porque o UUID vem do
  `sub`, nunca da requisição.
- Rotas de funcionário continuam com `ADMIN`/`ATTENDANT`/`MECHANIC`; token de cliente nelas → 403.
- Endpoints públicos (status da OS, aprovação de orçamento e aceite de entrega por OTP, health)
  continuam sem token.
- `INTERNAL` e `LAMBDA` existem só para teste de regressão de um emissor isolado.

### Sem JWT Authorizer no API Gateway

O Gateway apenas roteia. Um authorizer em `/core` não reconheceria o token de funcionário e
bloquearia o login e os endpoints públicos. A exigência do enunciado (rotas sensíveis acessíveis
só com o token da Lambda) é cumprida pela validação no core.

## Consequências

- Uma única aplicação aplica regras de papel para os dois públicos, com testes automatizados.
- Pares RSA separados por emissor e por ambiente; as chaves `dev-*.pem` do repositório servem só
  para desenvolvimento local e testes.
- O `iss` do cliente muda quando o Gateway é recriado; o pipeline do core atualiza issuer e JWKS
  a cada deploy.
- Requisições com token inválido chegam até o pod (e são recusadas lá), o que um authorizer
  evitaria; o volume esperado não justifica a complexidade extra.
