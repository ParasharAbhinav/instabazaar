#!/usr/bin/env bash
set -euo pipefail
BACKUP_DIR="${1:?Usage: ./scripts/restore-volumes.sh <backup_directory>}"

echo "[1/3] Verifying backup archive SHA-256 integrity..."
(cd "$BACKUP_DIR" && sha256sum -c SHA256SUMS.txt)

echo "[2/3] Restoring PostgreSQL database from $BACKUP_DIR/instabazaar-db.dump..."
docker exec -i instabazaar-db pg_restore -U "${POSTGRES_SUPERUSER:-postgres_admin}" -d "${POSTGRES_DB:-instabazaar}" --clean --if-exists < "$BACKUP_DIR/instabazaar-db.dump"

echo "[3/3] Restoring /app/var/media volume from $BACKUP_DIR/instabazaar-media.tar.gz..."
docker run --rm --volumes-from instabazaar-backend -v "$(cd "$BACKUP_DIR" && pwd):/backup:ro" alpine:3.20 sh -c "rm -rf /app/var/media/* && tar -xzf /backup/instabazaar-media.tar.gz -C /app/var/media"

echo "Restore completed and verified successfully from $BACKUP_DIR"