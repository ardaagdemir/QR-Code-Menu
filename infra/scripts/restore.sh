#!/usr/bin/env bash
# Restores a PostgreSQL backup produced by backup.sh or the postgres-backup sidecar.
#
# DESTRUCTIVE: drops and recreates the target database before loading the dump -
# everything currently in it is lost. Requires typing the database name to confirm.
#
# Usage (from anywhere - resolves paths relative to this script):
#   ./scripts/restore.sh <path-to-backup.sql.gz> [dev|prod]
#
# Example:
#   ./scripts/restore.sh backups/qrmenu_manual_20260814T120000Z.sql.gz dev
set -euo pipefail

BACKUP_FILE="${1:?Usage: restore.sh <backup-file.sql.gz> [dev|prod]}"
ENV_NAME="${2:-dev}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(dirname "${SCRIPT_DIR}")"

case "${BACKUP_FILE}" in
  /*) ;;
  *) BACKUP_FILE="$(pwd)/${BACKUP_FILE}" ;;
esac

cd "${INFRA_DIR}"

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "Backup file not found: ${BACKUP_FILE}" >&2
  exit 1
fi

if [ "${ENV_NAME}" = "prod" ]; then
  ENV_FILE=".env.prod"
  COMPOSE_ARGS=(--env-file "${ENV_FILE}" -f docker-compose.prod.yml)
elif [ "${ENV_NAME}" = "dev" ]; then
  ENV_FILE=".env"
  COMPOSE_ARGS=(-f docker-compose.yml)
else
  echo "Unknown target '${ENV_NAME}', expected 'dev' or 'prod'." >&2
  exit 1
fi

if [ ! -f "${ENV_FILE}" ]; then
  echo "Missing infra/${ENV_FILE} - copy it from ${ENV_FILE}.example first." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

POSTGRES_DB="${POSTGRES_DB:-qrmenu}"
POSTGRES_USER="${POSTGRES_USER:-qrmenu}"

echo "This will DROP and recreate database '${POSTGRES_DB}' (${ENV_NAME} stack) and load:"
echo "  ${BACKUP_FILE}"
read -r -p "Type the database name to confirm: " CONFIRM
if [ "${CONFIRM}" != "${POSTGRES_DB}" ]; then
  echo "Confirmation did not match '${POSTGRES_DB}', aborting." >&2
  exit 1
fi

echo "Terminating other connections to '${POSTGRES_DB}'..."
docker compose "${COMPOSE_ARGS[@]}" exec -T postgres \
  psql -U "${POSTGRES_USER}" -d postgres -v ON_ERROR_STOP=1 -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${POSTGRES_DB}' AND pid <> pg_backend_pid();"

echo "Dropping and recreating '${POSTGRES_DB}'..."
docker compose "${COMPOSE_ARGS[@]}" exec -T postgres \
  psql -U "${POSTGRES_USER}" -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"${POSTGRES_DB}\";"
docker compose "${COMPOSE_ARGS[@]}" exec -T postgres \
  psql -U "${POSTGRES_USER}" -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"${POSTGRES_DB}\" OWNER \"${POSTGRES_USER}\";"

echo "Restoring from ${BACKUP_FILE}..."
gunzip -c "${BACKUP_FILE}" | docker compose "${COMPOSE_ARGS[@]}" exec -T postgres \
  psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -v ON_ERROR_STOP=1

echo "Restore complete: '${POSTGRES_DB}' now reflects ${BACKUP_FILE}."
