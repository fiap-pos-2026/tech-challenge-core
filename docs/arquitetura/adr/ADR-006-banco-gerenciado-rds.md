# ADR-006 — Banco gerenciado: RDS PostgreSQL

**Status:** aceito  
**Data:** 2026-10

## Contexto

Até a Fase 2 o PostgreSQL rodava dentro do cluster (provisionado pelo Terraform local junto do microk8s). A Fase 3 exige banco gerenciado
na nuvem, provisionado via Terraform, com justificativa da escolha.

## Decisão

- **Amazon RDS for PostgreSQL 18**, instância `db.t4g.micro` em homolog, nas subnets privadas,
  sem acesso público, storage criptografado e backup automático.
- Security group aceita a porta 5432 apenas de dentro da VPC (nodes do EKS e Lambda).
- Esquema continua versionado pelo **Liquibase** no core, aplicado no start da aplicação.
- Senha só em GitHub Secret e no Secret do Kubernetes; endpoint descoberto pelo pipeline.

A justificativa do PostgreSQL em si (transações no estoque, locks, tipos numéricos, integridade
entre agregados) e o modelo ER estão no [README do core](../../../README.md#por-que-postgresql).

## Consequências

- Mesmo motor em dev (Docker Compose / Testcontainers) e na nuvem — sem diferença de dialeto.
- Backup, patch de versão menor e disponibilidade ficam com a AWS.
- Single-AZ em homolog por custo; `prod` deve ligar Multi-AZ e retenção de backup de 7 dias
  (já parametrizado no Terraform).
- Redis e Mailpit ainda rodam dentro do cluster em homolog; em `prod` estão previstos ElastiCache
  e um provedor de e-mail gerenciado.
