#!/usr/bin/env bash
# recipes/shim.template.sh — ponteiro fino para a implementacao compartilhada.
set -euo pipefail
exec "$HOME/Data/Projects/agent-sandbox/recipes/suspend.sh" "$(cd "$(dirname "$0")/../.." && pwd)"
