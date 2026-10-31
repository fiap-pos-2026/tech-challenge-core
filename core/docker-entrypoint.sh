#!/bin/sh
set -eu

# Ativa APM só quando a license key estiver no ambiente (Secret do cluster / CI).
if [ -n "${NEW_RELIC_LICENSE_KEY:-}" ] && [ -f /newrelic/newrelic.jar ]; then
  export NEW_RELIC_APP_NAME="${NEW_RELIC_APP_NAME:-tech-challenge-core-homolog}"
  export NEW_RELIC_LOG="${NEW_RELIC_LOG:-stdout}"
  export NEW_RELIC_LOG_FILE_NAME="${NEW_RELIC_LOG_FILE_NAME:-STDOUT}"
  JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -javaagent:/newrelic/newrelic.jar"
  export JAVA_TOOL_OPTIONS
fi

exec java -cp "app:app/lib/*" br.com.fiap.pos.tech_challenge.core.TechChallengeApplication "$@"
