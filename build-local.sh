#!/bin/bash
set -e

LOG_FILE="build-$(date +%Y%m%d-%H%M%S).log"
exec > >(tee -a "$LOG_FILE") 2>&1

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

log "=== 开始本地构建 ==="

log "Step 1/2: mvn package -DskipTests ..."
mvn package -DskipTests
log "Maven 打包完成"

log "Step 2/2: docker compose up -d --build ..."
docker compose up -d --build
log "容器启动完成"

log "=== 构建完成，日志文件: $LOG_FILE ==="
