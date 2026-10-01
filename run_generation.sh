#!/usr/bin/env bash
# run_generation.sh — rule-generation run(s) with a prepared config, without touching earlier runs.
#
# Configs: .env.<RUN>-<engine> (copies of .env with only provider, model, key, engine and
# output paths changed; outputs in NEW folders named after RUN, CLEAN=false).
# The original .env is restored when the script ends, even on failure.
#
# Usage: RUN=gpt-5-4-mini-run2 ./run_generation.sh [javaparser|spoon ...]   (default: both)
#        RUN=gpt-5-4-mini-run2 TEST=<commit-hash> ./run_generation.sh javaparser
#          → single benchmark case, outputs in <RUN>-test folders (never mixed with full runs)
set -uo pipefail
cd "$(dirname "$0")"

RUN=${RUN:?set RUN, e.g. RUN=gpt-5-4-mini-run2}
TEST=${TEST:-}
ENGINES=("$@")
[[ $# -eq 0 ]] && ENGINES=(javaparser spoon)
JAVA25=$HOME/.sdkman/candidates/java/25.0.2-oracle/bin/java   # the jar is built for Java 25
BACKUP=.env.before-$RUN

for eng in "${ENGINES[@]}"; do
    f=.env.$RUN-$eng
    [[ -f $f ]] || { echo "missing $f"; exit 1; }
    grep -q '^LLM_API_KEY=PON_AQUI' "$f" && { echo "Put your API key in $f (LLM_API_KEY=...)"; exit 1; }
done

# DeepSeek only: check the model is available for the key
first=.env.$RUN-${ENGINES[0]}
if [[ $(sed -n 's/^LLM_PROVIDER=//p' "$first") == deepseek ]]; then
    key=$(sed -n 's/^LLM_API_KEY=//p' "$first")
    model=$(sed -n 's/^LLM_MODEL=//p' "$first")
    models=$(curl -s -H "Authorization: Bearer $key" https://api.deepseek.com/models)
    echo "$models" | grep -q "\"id\":\"$model\"" || { echo "Model '$model' not available: $models"; exit 1; }
fi

cp -p .env "$BACKUP"
trap 'cp -p "$BACKUP" .env && rm -f "$BACKUP" && echo "original .env restored"' EXIT
mkdir -p logs

for eng in "${ENGINES[@]}"; do
    if [[ -n $TEST ]]; then
        echo "[$(date '+%F %T')] $RUN TEST run: $eng, case $TEST"
        sed -e "s#^SPECIFIC_FILE=.*#SPECIFIC_FILE=$TEST#" \
            -e "s#/$RUN\\b#/$RUN-test#g" ".env.$RUN-$eng" > .env
        logf="logs/$RUN-test-$eng.txt"
    else
        echo "[$(date '+%F %T')] $RUN run: $eng"
        cp -p ".env.$RUN-$eng" .env
        logf="logs/$RUN-$eng.txt"
    fi
    grep -E '^(OUTPUT_DIR|JSON_OUTPUT|SPECIFIC_FILE|LLM_PROVIDER|LLM_MODEL|RULE_GENERATOR)=' .env
    "$JAVA25" -jar core/target/core-cli-1.0.0-SNAPSHOT.jar 2>&1 | tee -a "$logf"
    echo "[$(date '+%F %T')] finished $eng (exit ${PIPESTATUS[0]})"
done
