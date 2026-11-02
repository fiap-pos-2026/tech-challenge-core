# RFC-001 — Provedor de nuvem e serviços gerenciados

**Status:** aceita  
**Data:** 2026-10  
**Decisão registrada em:** [ADR-001](../adr/ADR-001-quatro-repositorios.md), [ADR-002](../adr/ADR-002-gateway-vpc-link-nlb.md)

## Contexto

A Fase 3 pede, na nuvem: API Gateway, uma função serverless de autenticação, banco gerenciado,
Kubernetes escalável e tudo provisionado com Terraform. Até a Fase 2 a aplicação rodava em
microk8s/Docker Compose, com o PostgreSQL dentro do cluster.

Restrições reais do grupo:

- O curso fornece créditos do **AWS Academy (Learner Lab)**. Não há cartão nem conta paga.
- No Learner Lab não dá para criar IAM Role (`iam:CreateRole` negado) nem provedor OIDC; as
  credenciais são temporárias e expiram a cada sessão (~4 h).
- Orçamento de cerca de US$ 50 para a fase inteira.

## Alternativas

| Critério | AWS | Azure | GCP |
|---|---|---|---|
| Créditos disponíveis para o grupo | **Sim (Academy)** | Não | Não |
| API Gateway + função serverless | API Gateway HTTP API + Lambda | API Management + Functions | API Gateway + Cloud Functions |
| PostgreSQL gerenciado | RDS | Flexible Server | Cloud SQL |
| Kubernetes gerenciado | EKS | AKS | GKE |
| Provider Terraform maduro | Sim | Sim | Sim |

Dentro da AWS, também comparamos onde rodar a aplicação:

| Opção | Prós | Contras |
|---|---|---|
| **EKS** | Reaproveita os manifests Kustomize e o HPA da Fase 2; atende "Kubernetes escalável" literalmente | Control plane cobrado por hora; IAM limitado no lab |
| ECS Fargate | Mais barato e simples | Reescreve o deploy; não é Kubernetes |
| EC2 + microk8s | Mais barato | Cluster não gerenciado; não evidencia nuvem gerenciada |

## Decisão

**AWS**, com **EKS** para a aplicação, **RDS PostgreSQL** para o banco, **Lambda (Java 21)** para a
autenticação e **API Gateway HTTP API** como porta de entrada. Região `us-east-1` (a do Learner Lab).

O fator decisivo foi ter crédito disponível. Entre os serviços AWS, o EKS mantém a continuidade
com a Fase 2 (mesmos manifests, mesmo HPA) e o HTTP API é mais barato e simples que o REST API
para o que precisamos (roteamento + integração com Lambda e VPC Link).

## Consequências

- Terraform dividido por responsabilidade (rede/banco, cluster/gateway, Lambda) — ver ADR-001.
- O IAM do lab obriga a usar as roles prontas (`LabRole`, `*LabEksClusterRole*`, `*LabEksNodeRole*`),
  descobertas por nome no Terraform.
- CI/CD usa chaves temporárias do lab em GitHub Secrets (sem OIDC), renovadas a cada sessão.
- Custo: NAT Gateway, control plane do EKS e nodes são os maiores gastos. O ambiente é destruído
  (`terraform destroy`) quando não está em uso e recriado pela pipeline.
- Desenvolvimento local continua em Docker Compose / microk8s, sem depender da nuvem.
