#!/bin/sh
set -e

# Ensure uploads directory and DJL cache exist and are owned by medai user
UPLOADS_DIR="${STORAGE_LOCAL_PATH:-/app/uploads}"
mkdir -p "$UPLOADS_DIR" /home/medai/.djl.ai /home/medai/.spring-ai-onnx 2>/dev/null || true
chown -R medai:medai "$UPLOADS_DIR" /home/medai 2>/dev/null || true

# Execute java as unprivileged medai user
exec gosu medai java -jar /app/app.jar "$@"
