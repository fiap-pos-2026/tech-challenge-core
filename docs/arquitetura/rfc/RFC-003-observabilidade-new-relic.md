# RFC-003 — Ferramenta de observabilidade

**Status:** aceita  
**Data:** 2026-10-31

## Contexto

A Fase 3 pede:

- latência das APIs, CPU/memória dos pods, health/uptime;
- logs estruturados com correlação entre requisições;
- alertas para falhas no processamento de ordens de serviço;
- dashboards de volume diário de OS, tempo médio por status e erros de integração.

O grupo não tem orçamento para ferramenta paga. As opções sugeridas no enunciado são Datadog e New Relic.

## Alternativas

| Critério | Datadog (free, após o trial de 14 dias) | New Relic (free) |
|---|---|---|
| APM / traces | **Não** (pago) | **Sim** |
| Kubernetes (nodes/pods) | Poucos hosts, retenção curta | Sim, cobrado por GB ingerido |
| Logs com correlação | Limitado / pago | Sim |
| Dashboards e alertas | Restritos | Sim (NRQL) |
| Limite | Hosts | 100 GB/mês de ingestão, 1 usuário full + usuários basic |
| Cartão de crédito | Trial exige cuidado com upgrade | Não exige |

## Decisão

**New Relic free tier.** O ponto decisivo é o APM: o enunciado pede latência e acompanhamento
das APIs, e o Datadog gratuito não inclui APM depois do trial.

Como foi implementado:

- **APM:** agente Java embutido na imagem do core, ativado quando há license key no Secret do cluster.
- **Kubernetes:** Helm chart `nri-bundle` no namespace `newrelic` (`lowDataMode`).
- **Logs:** console em texto com `correlationId` (filtro HTTP que lê/gera `X-Correlation-Id`);
  o agente encaminha os logs da aplicação para o New Relic.
- **Métricas de negócio:** logs estruturados `service_order.opened`, `service_order.status_transition`
  (com `durationMs`) e `service_order.integration_error`, consultados por NRQL.
- **Dashboard e alertas:** criados por script (NerdGraph), não à mão — dashboard de OS/APIs/infra
  e política de alerta com condições para erros de APM, falha nas APIs de OS, falha de e-mail e health.

## Consequências

- License key só em GitHub Secret / K8s Secret. Sem a chave, o core sobe normalmente, sem agente.
- Para não estourar os 100 GB/mês, a coleta de logs fica restrita ao namespace da aplicação e o
  agente usa modo de baixo volume.
- As métricas Micrometer continuam no `/actuator/metrics`, mas **não** são exportadas ao New Relic;
  os painéis de negócio usam os logs estruturados. Exportar via registry é uma melhoria possível.
- Só um usuário full no plano gratuito; os demais entram como basic (veem e montam dashboards,
  mas não criam monitores Synthetic).
- CPF, JWT e senhas não podem aparecer em log.
