#!/usr/bin/env bash
# Provision and start the Remote Agent Docker gateway without interactive prompts.
set -Eeuo pipefail

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$ROOT_DIR/.env"
ENV_TEMPLATE="$ROOT_DIR/.env.example"
CADDY_TEMPLATE="$ROOT_DIR/Caddyfile.example"
CADDY_FILE="$ROOT_DIR/Caddyfile"

MODE="local"
DOMAIN=""
PORT="8080"
PORT_SET=false
FORCE_CADDY=false
START_SERVICES=true
DRY_RUN=false

usage() {
  cat <<'USAGE'
Usage: ./setup-docker.sh [options]

Provision the gateway configuration, generate missing pairing secrets, and start Docker Compose.

Options:
  --mode local|tls      Start only the gateway (local) or gateway plus Caddy HTTPS proxy (tls). Default: local.
  --domain HOSTNAME     Required with --mode tls. Replaces agent.example.com in Caddyfile.
  --port PORT           Host port for the gateway health endpoint. Default: 8080.
  --force-caddy         Regenerate Caddyfile even if it already exists.
  --no-start            Generate configuration only; do not invoke Docker.
  --dry-run             Generate configuration and print the Compose command without invoking Docker.
  -h, --help            Show this help text.

Environment variables:
  PAIRING_CODE, TOKEN_SECRET, GOOGLE_API_KEY, GROQ_API_KEY, OPENROUTER_API_KEY
  may be supplied before running the script. Existing non-placeholder values in .env are preserved.

Examples:
  ./setup-docker.sh --mode local
  GROQ_API_KEY=... ./setup-docker.sh --mode tls --domain agent.example.com
USAGE
}

die() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

note() {
  printf '%s\n' "$*"
}

require_file() {
  [[ -f "$1" ]] || die "Required file is missing: ${1#$ROOT_DIR/}"
}

is_placeholder() {
  local value="${1:-}"
  [[ -z "$value" || "$value" == replace-with-* || "$value" == '<'*'>' ]]
}

random_hex() {
  local bytes="$1"
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "$bytes"
  elif command -v python3 >/dev/null 2>&1; then
    python3 -c "import secrets; print(secrets.token_hex($bytes))"
  else
    die "Install openssl or python3 so secure setup secrets can be generated."
  fi
}

read_env_value() {
  local key="$1"
  [[ -f "$ENV_FILE" ]] || return 0
  awk -F= -v key="$key" '$1 == key { value=substr($0, length(key)+2) } END { print value }' "$ENV_FILE"
}

upsert_env_value() {
  local key="$1"
  local value="$2"
  local tmp_file
  tmp_file="$(mktemp "$ROOT_DIR/.env.tmp.XXXXXX")"
  awk -v key="$key" -v value="$value" '
    $0 ~ "^" key "=" { print key "=" value; found=1; next }
    { print }
    END { if (!found) print key "=" value }
  ' "$ENV_FILE" > "$tmp_file"
  mv "$tmp_file" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
}

ensure_env_file() {
  if [[ ! -f "$ENV_FILE" ]]; then
    umask 077
    cp "$ENV_TEMPLATE" "$ENV_FILE"
    chmod 600 "$ENV_FILE"
    note "Created .env from .env.example."
  fi

  local pairing_code token_secret existing_value key
  pairing_code="$(read_env_value PAIRING_CODE)"
  token_secret="$(read_env_value TOKEN_SECRET)"

  if is_placeholder "$pairing_code"; then
    pairing_code="${PAIRING_CODE:-$(random_hex 24)}"
    upsert_env_value PAIRING_CODE "$pairing_code"
    note "Generated a one-time pairing code."
  fi
  if is_placeholder "$token_secret"; then
    token_secret="${TOKEN_SECRET:-$(random_hex 32)}"
    upsert_env_value TOKEN_SECRET "$token_secret"
    note "Generated a gateway token secret."
  fi

  if [[ "$PORT_SET" == true ]]; then
    upsert_env_value AGENT_PORT "$PORT"
  else
    existing_value="$(read_env_value AGENT_PORT)"
    if [[ -n "$existing_value" ]]; then
      PORT="$existing_value"
    else
      upsert_env_value AGENT_PORT "$PORT"
    fi
  fi

  for key in GOOGLE_API_KEY GROQ_API_KEY OPENROUTER_API_KEY; do
    existing_value="$(read_env_value "$key")"
    if [[ -z "$existing_value" && -n "${!key:-}" ]]; then
      upsert_env_value "$key" "${!key}"
      note "Stored $key from the current environment."
    fi
  done
}

prepare_tls_file() {
  [[ "$MODE" == "tls" ]] || return 0
  [[ -n "$DOMAIN" ]] || die "--domain is required with --mode tls."
  [[ "$DOMAIN" != *://* && "$DOMAIN" != */* && "$DOMAIN" != *' '* ]] || die "--domain must be a hostname only, without protocol or path."

  if [[ -f "$CADDY_FILE" && "$FORCE_CADDY" != true ]]; then
    note "Preserving existing Caddyfile. Use --force-caddy to regenerate it."
    return 0
  fi

  sed "s/agent\.example\.com/$DOMAIN/g" "$CADDY_TEMPLATE" > "$CADDY_FILE"
  chmod 644 "$CADDY_FILE"
  note "Prepared Caddyfile for $DOMAIN."
}

configure_compose() {
  if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose)
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose)
  else
    die "Docker Compose was not found. Install Docker with the Compose plugin, then rerun this script."
  fi
}

wait_for_gateway() {
  local attempts=30
  local health_url="http://127.0.0.1:${PORT}/health"
  local attempt
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if command -v curl >/dev/null 2>&1 && curl --fail --silent --show-error --max-time 3 "$health_url" >/dev/null; then
      note "Gateway health check passed: $health_url"
      return 0
    fi
    if "${COMPOSE[@]}" exec -T agent-api python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/health', timeout=2)" >/dev/null 2>&1; then
      note "Gateway health check passed inside the container."
      return 0
    fi
    sleep 2
  done

  "${COMPOSE[@]}" ps >&2 || true
  "${COMPOSE[@]}" logs --tail=80 agent-api >&2 || true
  die "Gateway did not become healthy within $((attempts * 2)) seconds."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      [[ $# -ge 2 ]] || die "--mode requires local or tls."
      MODE="$2"
      shift 2
      ;;
    --domain)
      [[ $# -ge 2 ]] || die "--domain requires a hostname."
      DOMAIN="$2"
      shift 2
      ;;
    --port)
      [[ $# -ge 2 ]] || die "--port requires a TCP port number."
      PORT="$2"
      PORT_SET=true
      shift 2
      ;;
    --force-caddy)
      FORCE_CADDY=true
      shift
      ;;
    --no-start)
      START_SERVICES=false
      shift
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown option: $1. Use --help for usage."
      ;;
  esac
done

[[ "$MODE" == "local" || "$MODE" == "tls" ]] || die "--mode must be local or tls."
[[ "$PORT" =~ ^[1-9][0-9]{0,4}$ ]] && (( PORT <= 65535 )) || die "--port must be between 1 and 65535."

cd "$ROOT_DIR"
require_file "$ENV_TEMPLATE"
require_file "$CADDY_TEMPLATE"
require_file "$ROOT_DIR/docker-compose.yml"
ensure_env_file
prepare_tls_file

PAIRING_CODE_DISPLAY="$(read_env_value PAIRING_CODE)"

if [[ "$START_SERVICES" == false ]]; then
  note "Configuration is ready; Docker was not started (--no-start)."
elif [[ "$DRY_RUN" == true ]]; then
  if [[ "$MODE" == "tls" ]]; then
    note "Dry run: docker compose --profile tls up --build -d agent-api caddy"
  else
    note "Dry run: docker compose up --build -d agent-api"
  fi
else
  configure_compose
  docker info >/dev/null 2>&1 || die "Docker is installed but its daemon is unavailable. Start Docker, then rerun this script."
  if [[ "$MODE" == "tls" ]]; then
    "${COMPOSE[@]}" --profile tls up --build -d agent-api caddy
  else
    "${COMPOSE[@]}" up --build -d agent-api
  fi
  wait_for_gateway
fi

note ""
note "Setup complete."
if [[ "$MODE" == "tls" ]]; then
  note "Android gateway URL: https://$DOMAIN"
  note "TLS note: DNS for $DOMAIN must resolve to this host and ports 80/443 must be reachable."
else
  note "Local health URL: http://127.0.0.1:$PORT/health"
  note "Android pairing requires a private HTTPS mesh endpoint or --mode tls; the Android client rejects cleartext HTTP."
fi
note "Pairing code: $PAIRING_CODE_DISPLAY"
note "Secrets file: $ENV_FILE (mode 600; do not commit it)"
