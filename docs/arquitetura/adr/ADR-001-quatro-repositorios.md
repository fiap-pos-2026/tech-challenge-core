# ADR-001 — Separar o sistema em quatro repositórios

**Status:** aceito  
**Data:** 2026-10

## Contexto

Até a Fase 2 havia um único repositório com aplicação, manifests Kubernetes e Terraform local.
A Fase 3 exige quatro repositórios, cada um com CI/CD próprio, e as partes têm ciclos de vida bem
diferentes: a rede e o banco mudam raramente, o cluster de vez em quando, a aplicação a todo commit.

## Decisão

| Repositório | Conteúdo | Pipeline |
|---|---|---|
| `tech-challenge-db-infra` | VPC, subnets, NAT, RDS PostgreSQL, security group do banco | Terraform: plan no PR, apply na `main` |
| `tech-challenge-k8s-infra` | EKS, node group, API Gateway HTTP API, VPC Link | Terraform: plan no PR, apply na `main` |
| `tech-challenge-auth-lambda` | Lambda de autenticação por CPF + rotas `/auth` no Gateway | build/testes + Terraform (`infra/`) |
| `tech-challenge-core` | Aplicação Spring Boot + manifests Kustomize | build/testes → imagem no ECR → deploy no EKS |

Os repositórios de infra se comunicam por **remote state** do Terraform (S3 + lock em DynamoDB):
o `k8s-infra` lê a VPC e o SG do RDS do `db-infra`; o `auth-lambda` lê os dois. O core não lê state:
o pipeline descobre o endpoint do RDS, o cluster e o Gateway na conta a cada deploy.

**Ordem de criação:** `db-infra` → `k8s-infra` → `auth-lambda` → deploy do `core` → re-apply do
`k8s-infra` (para ligar a rota `/core` ao NLB criado pelo deploy).

As rotas `/auth` ficam no repositório da Lambda, não no `k8s-infra`, para evitar dependência
circular: o `iss` do token é a URL do Gateway, então o Gateway precisa existir antes da Lambda.

## Consequências

- Uma mudança na aplicação não roda Terraform de infraestrutura, e vice-versa.
- Cada repositório tem segredos só do que precisa (a chave privada do JWT de cliente fica apenas
  no repo da Lambda).
- O acoplamento entre repos fica explícito nos outputs do Terraform e na ordem de apply,
  que está documentada nos READMEs.
- Destruir o ambiente exige a ordem inversa, senão ENIs (Lambda, NLB) prendem as subnets privadas.
