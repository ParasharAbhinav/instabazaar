#!/usr/bin/env bash
set -euo pipefail
BACKUP_DIR="${1:-./backups/$(date +%Y%m%d_%H%M%S)}"
mkdir -p "$BACKUP_DIR"

echo "[1/3] Dumping PostgreSQL database (custom compressed format)..."
docker exec instabazaar-db pg_dump -U "${POSTGRES_SUPERUSER:-postgres_admin}" -Fc "${POSTGRES_DB:-instabazaar}" > "$BACKUP_DIR/instabazaar-db.dump"

echo "[2/3] Archiving /app/var/media volume..."
docker run --rm --volumes-from instabazaar-backend -v "$(pwd)/$BACKUP_DIR:/backup" alpine:3.20 tar -czf /backup/instabazaar-media.tar.gz -C /app/var/media .

echo "[3/3] Generating SHA-256 integrity manifest..."
(cd "$BACKUP_DIR" && sha256sum instabazaar-db.dump instabazaar-media.tar.gz > SHA256SUMS.txt)
echo "Backup completed successfully at $BACKUP_DIR"
