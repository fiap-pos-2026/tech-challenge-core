# ADR-005 — A Lambda de autenticação consulta o banco diretamente

**Status:** aceito  
**Data:** 2026-10  
**Origem:** [RFC-002](../rfc/RFC-002-autenticacao-cliente-cpf.md)

## Contexto

Para emitir o token, a Lambda precisa saber se o CPF pertence a um cliente cadastrado e ativo.
Os clientes são cadastrados pelo core, na tabela `customers` do RDS.

## Decisão

- A Lambda roda nas **subnets privadas** da mesma VPC do RDS, com security group próprio, e faz
  uma única consulta JDBC: `SELECT _uuid, _name, _active FROM customers` filtrando pelo documento
  (só dígitos, porque o core aceita CPF com ou sem máscara).
- A Lambda **só lê**, e isso é garantido pelo banco: ela conecta como `auth_lambda_reader`, usuário
  criado pelo Liquibase do core (changeset `025`) com `SELECT` apenas em `_uuid`, `_name`, `_active`,
  `_document_type` e `_document` de `customers`. A senha vem do secret `AUTH_DB_READER_PASSWORD`,
  igual nos dois repositórios.
- O status ativo/inativo do cliente é a coluna `_active`, criada pela migration `024` do core.
- Sem Spring Boot na Lambda (JDBC puro + biblioteca JOSE) para manter o cold start baixo.

## Alternativa descartada

Chamar uma API interna do core: exigiria endpoint de serviço com credencial própria, adicionaria
um salto de rede e faria o login do cliente depender da disponibilidade da aplicação.

## Consequências

- O login do cliente funciona mesmo com o core fora do ar (só depende do RDS).
- A Lambda fica acoplada ao **esquema** da tabela `customers`. Mudanças nessas colunas precisam
  ser coordenadas entre os dois repositórios (migration no core + consulta na Lambda), e os testes
  de integração da Lambda (Testcontainers) recriam a tabela para pegar quebras cedo.
- Mesmo que o código da Lambda tivesse uma falha de injeção, o usuário dela não consegue ler
  e-mail/telefone, outras tabelas nem escrever (coberto por teste de integração no core).
- O usuário de leitura precisa existir **antes** de a Lambda passar a usá-lo: deploy do core
  primeiro, depois o apply da Lambda. Sem o secret, a Lambda volta ao usuário da aplicação.
- A Lambda dentro da VPC cria ENIs nas subnets privadas; destruir o ambiente exige remover a Lambda
  antes da rede (ver ADR-001).
