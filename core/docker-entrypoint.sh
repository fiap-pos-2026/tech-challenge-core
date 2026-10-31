#!/bin/sh
set -eu

# Ativa APM só quando a license key estiver no ambiente (Secret do cluster / CI).
if [ -n "${NEW_RELIC_LICENSE_KEY:-}" ] && [ -f /newrelic/newrelic.jar ]; then
  export NEW_RELIC_APP_NAME="${NEW_RELIC_APP_NAME:-tech-challenge-core-homolog}"
  # Agent log fora do stdout do pod (kubectl fica só com o log da app)
  export NEW_RELIC_LOG_FILE_NAME="${NEW_RELIC_LOG_FILE_NAME:-/tmp/newrelic-agent.log}"
  export NEW_RELIC_LOG_LEVEL="${NEW_RELIC_LOG_LEVEL:-warn}"
  # Encaminha logs da app para a UI New Relic sem forçar JSON no console
  export NEW_RELIC_APPLICATION_LOGGING_ENABLED="${NEW_RELIC_APPLICATION_LOGGING_ENABLED:-true}"
  export NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED="${NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED:-true}"
  JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -javaagent:/newrelic/newrelic.jar"
  export JAVA_TOOL_OPTIONS
fi

exec java -cp "app:app/lib/*" br.com.fiap.pos.tech_challenge.core.TechChallengeApplication "$@"
