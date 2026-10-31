# ADR-003 — Escalabilidade horizontal com HPA

**Status:** aceito  
**Data:** 2026-10 (vem da Fase 2, mantido no EKS)

## Contexto

A Fase 2 já exigia escalabilidade automática; na Fase 3 a aplicação passa a rodar no EKS.
A oficina tem picos previsíveis (abertura de OS pela manhã, entregas no fim do dia) e o crédito
do lab é limitado, então não dá para deixar muitas réplicas fixas.

## Decisão

- **HorizontalPodAutoscaler** (`autoscaling/v2`) no Deployment `core`, definido no `k8s/base`:
  - mínimo **1** e máximo **3** réplicas em homolog (o esboço de `prod` prevê 2–6);
  - alvo de **70% de CPU** e **80% de memória** sobre os *requests*.
- Requests de `250m` CPU / `512Mi` e limites de `1` CPU / `1Gi` por pod, para o HPA ter base de
  cálculo e o pod não derrubar o node.
- `startupProbe` com folga (até 4 minutos) no overlay `homolog`, porque o primeiro start roda o
  Liquibase contra o RDS e não deve ser confundido com travamento.
- O node group do EKS vai de 1 a 4 nodes `t3.medium` (`tech-challenge-k8s-infra`).

## Consequências

- A aplicação precisa ser sem estado: sessão está no JWT, a blacklist de tokens (logout) fica no
  Redis e os dados no RDS — qualquer réplica atende qualquer requisição.
- O HPA depende do **metrics-server**, que o EKS **não** instala por padrão e que hoje não é
  provisionado por nenhum dos repositórios. Sem ele o HPA fica sem métrica (`<unknown>`) e não
  escala. **Pendente:** instalar o add-on `metrics-server` do EKS no `tech-challenge-k8s-infra`
  (ou aplicar o manifest oficial no deploy do core). CPU e memória dos pods já aparecem no New Relic.
- O node group não tem Cluster Autoscaler (exigiria IAM Role, bloqueada no lab); o máximo de
  réplicas foi escolhido para caber nos nodes existentes.
- Liquibase roda no start de cada réplica, mas só uma aplica as mudanças (lock do próprio Liquibase).
