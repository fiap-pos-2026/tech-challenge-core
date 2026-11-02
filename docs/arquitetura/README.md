# Documentação de arquitetura — Fase 3

Decisões do sistema da oficina na nuvem. As **RFCs** registram o debate (alternativas e critérios);
os **ADRs** registram a decisão que ficou valendo e o que muda por causa dela.

Os diagramas (componentes, sequências e ER) estão no [README do core](../../README.md#visão-de-componentes-na-nuvem-fase-3).

## RFCs

| # | Tema | Status |
|---|---|---|
| [RFC-001](rfc/RFC-001-nuvem-aws.md) | Provedor de nuvem e serviços gerenciados (AWS: EKS, RDS, Lambda, HTTP API) | aceita |
| [RFC-002](rfc/RFC-002-autenticacao-cliente-cpf.md) | Autenticação do cliente por CPF (Lambda + JWT validado no core) | aceita |
| [RFC-003](rfc/RFC-003-observabilidade-new-relic.md) | Ferramenta de observabilidade (New Relic vs Datadog) | aceita |
| [RFC-004](rfc/RFC-004-notificacoes.md) | Notificações: SMTP na aplicação vs função serverless | aceita (manter na app) |

## ADRs

| # | Decisão | Status |
|---|---|---|
| [ADR-001](adr/ADR-001-quatro-repositorios.md) | Separação em quatro repositórios + remote state | aceito |
| [ADR-002](adr/ADR-002-gateway-vpc-link-nlb.md) | API Gateway ↔ aplicação via VPC Link + NLB interno | aceito |
| [ADR-003](adr/ADR-003-hpa.md) | Escalabilidade horizontal com HPA | aceito |
| [ADR-004](adr/ADR-004-jwt-cliente-validado-no-core.md) | Formato do JWT de cliente e validação no core (`AUTH_MODE=BOTH`) | aceito |
| [ADR-005](adr/ADR-005-lambda-le-banco-direto.md) | Lambda de autenticação lê o banco diretamente | aceito |
| [ADR-006](adr/ADR-006-banco-gerenciado-rds.md) | Banco gerenciado: RDS PostgreSQL | aceito |

## Repositórios

| Repo | Papel |
|---|---|
| [tech-challenge-core](https://github.com/fiap-pos-2026/tech-challenge-core) | Aplicação + Kustomize + deploy no EKS |
| [tech-challenge-auth-lambda](https://github.com/fiap-pos-2026/tech-challenge-auth-lambda) | Lambda de login por CPF → JWT |
| [tech-challenge-db-infra](https://github.com/fiap-pos-2026/tech-challenge-db-infra) | VPC + RDS PostgreSQL |
| [tech-challenge-k8s-infra](https://github.com/fiap-pos-2026/tech-challenge-k8s-infra) | EKS + API Gateway |
