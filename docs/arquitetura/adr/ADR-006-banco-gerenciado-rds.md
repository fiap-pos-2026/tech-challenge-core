# ADR-006 — Banco gerenciado: RDS PostgreSQL

**Status:** aceito  
**Data:** 2026-10

## Contexto

Até a Fase 2 o PostgreSQL rodava dentro do cluster (provisionado pelo Terraform local junto do microk8s). A Fase 3 exige banco gerenciado
na nuvem, provisionado via Terraform, com justificativa da escolha.

Critérios usados, em ordem de peso:

1. **Transação ACID entre agregados.** A aprovação do orçamento muda o status da OS e baixa o estoque na
   mesma transação, com `SELECT ... FOR UPDATE` no produto.
2. **Integridade referencial.** OS, cliente, veículo, orçamento e estoque se ligam por FK (ver o ER no README).
3. **Nenhuma reescrita.** O esquema já existe em 24 changesets Liquibase, e os testes usam Testcontainers com Postgres.
4. **Disponível no AWS Academy** e provisionável por Terraform, com custo de lab.
5. **Acesso pela Lambda** de autenticação, dentro da VPC e com usuário de privilégio mínimo.

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| PostgreSQL dentro do EKS (como na Fase 2) | Não é banco gerenciado; backup, patch e disco ficariam com o time. |
| Amazon Aurora PostgreSQL | Compatível, mas a menor instância custa bem mais que um `db.t4g.micro`, sem ganho para o volume da oficina. |
| RDS MySQL / SQL Server | Exigiria migrar tipos (`UUID`, `TIMESTAMPTZ`, `NUMERIC`), `gen_random_uuid()` e os changesets; nenhum ganho em troca. |
| DynamoDB | Leitura da OS por chave seria simples, mas perderíamos a transação entre OS e estoque e as FKs; as regras de integridade iriam para o código. |

## Decisão

- **Amazon RDS for PostgreSQL 18**, instância `db.t4g.micro` em homolog, nas subnets privadas,
  sem acesso público, storage criptografado e backup automático.
- Security group aceita a porta 5432 apenas de dentro da VPC (nodes do EKS e Lambda).
- Esquema continua versionado pelo **Liquibase** no core, aplicado no start da aplicação.
- Senha só em GitHub Secret e no Secret do Kubernetes; endpoint descoberto pelo pipeline.

A justificativa do PostgreSQL em si (transações no estoque, locks, tipos numéricos, integridade
entre agregados), o modelo ER, a explicação dos relacionamentos e os ajustes feitos no modelo
estão no [README do core](../../../README.md#por-que-postgresql).

## Consequências

- Mesmo motor em dev (Docker Compose / Testcontainers) e na nuvem — sem diferença de dialeto.
- Backup, patch de versão menor e disponibilidade ficam com a AWS.
- Single-AZ em homolog por custo; `prod` deve ligar Multi-AZ e retenção de backup de 7 dias
  (já parametrizado no Terraform).
- Redis e Mailpit ainda rodam dentro do cluster em homolog; em `prod` estão previstos ElastiCache
  e um provedor de e-mail gerenciado.
