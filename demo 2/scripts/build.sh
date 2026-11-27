#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
./gradlew clean bootJar
for service in report-service gateway-service discovery-server file-storage-service; do
    ./gradlew -p "$service" clean bootJar
done
