# ADR-002 — Comunicação API Gateway ↔ aplicação: VPC Link + NLB interno

**Status:** aceito  
**Data:** 2026-10

## Contexto

O API Gateway é a porta de entrada pública. A aplicação roda em pods no EKS, em subnets privadas.
Precisávamos levar `/core/**` do Gateway até os pods sem expor a aplicação diretamente na internet.

## Alternativas consideradas

| Opção | Por que não / por que sim |
|---|---|
| Service `LoadBalancer` público (NLB/ALB internet-facing) + Gateway apontando para a URL pública | A aplicação ficaria acessível sem passar pelo Gateway |
| Ingress com AWS Load Balancer Controller (ALB) | Exige criar IAM Role para o controller (IRSA) — bloqueado no AWS Academy |
| **Service `LoadBalancer` com NLB interno + VPC Link do HTTP API** | Usa só recursos que o lab permite; o NLB não tem IP público |

## Decisão

- O core expõe um Service do tipo `LoadBalancer` com as anotações de **NLB interno**
  (overlay `homolog`), criado pelo cloud controller do próprio EKS.
- O `tech-challenge-k8s-infra` cria um **VPC Link** (com security group próprio) e a integração
  `HTTP_PROXY` do HTTP API para o listener do NLB.
- As rotas `ANY /core` e `ANY /core/{proxy+}` são criadas por **discovery**: o Terraform procura o
  NLB pela tag `kubernetes.io/service-name=tech-challenge/core`. Se o NLB ainda não existe, a rota
  não é criada e o apply segue (por isso o re-apply depois do primeiro deploy do core).
- Os nodes liberam o tráfego vindo da VPC na porta do NodePort/aplicação.

## Consequências

- A aplicação só é alcançável pela internet através do Gateway.
- O caminho é: Gateway → VPC Link → NLB → NodePort → pod. O NLB não termina TLS; o TLS público
  fica no Gateway.
- O path base da aplicação continua `/core`, igual ao local, então collections e Swagger funcionam
  trocando só a URL base.
- Há um passo manual de ordem na primeira subida do ambiente (deploy do core antes do re-apply).
