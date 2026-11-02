# RFC-004 — Notificações ao cliente: SMTP na aplicação vs. função serverless

**Status:** aceita (manter na aplicação nesta fase)  
**Data:** 2026-10

## Contexto

O enunciado cita funções serverless para autenticação **e notificações**. A autenticação foi
para a Lambda (RFC-002). As notificações já existiam desde a Fase 1:

- a cada mudança de status da OS (inclusive a abertura), o cliente recebe um e-mail;
- a aprovação do orçamento e o aceite da entrega usam um código OTP enviado por e-mail.

Hoje isso é feito pelo próprio core: um listener `AFTER_COMMIT` do evento de mudança de status
envia o e-mail por SMTP. Falha de envio é registrada (log `service_order.integration_error`,
alerta no New Relic) e **não** desfaz a transição. Em homolog o SMTP é um Mailpit dentro do cluster.

## Alternativas

| Opção | Prós | Contras |
|---|---|---|
| **Manter SMTP no core (atual)** | Já testado; e-mail sai logo após o commit; OTP e status no mesmo fluxo; nenhum serviço novo | Envio síncrono na mesma requisição; core depende do SMTP |
| Core publica em SNS/SQS → Lambda envia via SES | Desacopla o envio; retentativa pela fila | SES começa em *sandbox* (só destinatários verificados); mais Terraform, IAM e custo; no lab o IAM é restrito |
| EventBridge → Lambda | Desacoplado e orientado a eventos | Mesmo problema do SES; muda o contrato de eventos |

## Decisão

**Manter o envio de e-mail no core nesta fase.** O requisito obrigatório e detalhado do enunciado
é a autenticação serverless; a notificação já funciona, tem tratamento de falha e está monitorada.
Mover para serverless agora traria risco ao fluxo de OTP (aprovação e entrega) sem ganho funcional
visível para a entrega.

## Consequências

- O envio continua acoplado à requisição que muda o status; uma lentidão no SMTP aumenta a latência
  dessa chamada (visível no APM).
- Falhas de e-mail geram log estruturado, contam no dashboard e disparam alerta.
- **Caminho de evolução:** o envio já passa por uma porta (`MailPort`). Trocar o adapter SMTP por um
  que publique numa fila (SQS) consumida por uma Lambda com SES não muda o domínio nem os casos de uso.
