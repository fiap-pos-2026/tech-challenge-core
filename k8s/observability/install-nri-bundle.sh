#!/usr/bin/env bash
# Instala/atualiza o New Relic nri-bundle no EKS homolog.
# Requer: helm, kubectl, NEW_RELIC_LICENSE_KEY
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
VALUES="${ROOT}/k8s/observability/nri-bundle-values.yaml"
CLUSTER_NAME="${NEW_RELIC_CLUSTER_NAME:-tech-challenge-homolog-eks}"
NAMESPACE="${NEW_RELIC_K8S_NAMESPACE:-newrelic}"

if [[ -z "${NEW_RELIC_LICENSE_KEY:-}" ]]; then
  echo "Defina NEW_RELIC_LICENSE_KEY (não versionar)." >&2
  exit 1
fi

helm repo add newrelic https://helm-charts.newrelic.com >/dev/null
helm repo update newrelic >/dev/null

helm upgrade --install newrelic-bundle newrelic/nri-bundle \
  --namespace "${NAMESPACE}" \
  --create-namespace \
  -f "${VALUES}" \
  --set global.licenseKey="${NEW_RELIC_LICENSE_KEY}" \
  --set global.cluster="${CLUSTER_NAME}" \
  --wait --timeout 10m

echo "nri-bundle instalado no namespace ${NAMESPACE} (cluster=${CLUSTER_NAME})."
echo "Restrinja logs ao namespace tech-challenge na UI se o volume subir (lowDataMode já ativo)."
