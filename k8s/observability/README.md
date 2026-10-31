# Observabilidade (New Relic)

Manifests e Helm values para o free tier. Conta/license key **não** ficam neste repo.

| Artefato | Função |
|---|---|
| `nri-bundle-values.yaml` | Infra + logs K8s (low data mode) |
| `install-nri-bundle.sh` | `helm upgrade --install` local |
| CI deploy | Mesmo Helm se `secrets.NEW_RELIC_LICENSE_KEY` existir |

Guia completo (dashboards NRQL, synthetic, secrets): plano privado `the-java-garage/docs/fase-3/new-relic-setup.md`.
