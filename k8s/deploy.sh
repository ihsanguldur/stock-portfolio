#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$REPO_ROOT"

echo "=== Building images in minikube's docker daemon ==="
eval $(minikube docker-env)
for svc in auth-service wallet-service stock-service portfolio-service transaction-service audit-service; do
  echo "Building $svc:local..."
  docker build -t "$svc:local" -f "$svc/Dockerfile" .
done

echo "=== Applying namespace ==="
kubectl apply -f "$SCRIPT_DIR/namespace.yaml"

echo "=== Applying secrets/config ==="
kubectl apply -f "$SCRIPT_DIR/secrets.yaml" -f "$SCRIPT_DIR/configmap.yaml" -f "$SCRIPT_DIR/postgres-init-configmap.yaml"

echo "=== Applying infra (postgres/redis/kafka) ==="
kubectl apply -f "$SCRIPT_DIR/postgres.yaml" -f "$SCRIPT_DIR/redis.yaml" -f "$SCRIPT_DIR/kafka.yaml"

echo "=== Applying app services ==="
kubectl apply -f "$SCRIPT_DIR/auth-service.yaml" -f "$SCRIPT_DIR/wallet-service.yaml" -f "$SCRIPT_DIR/stock-service.yaml" \
               -f "$SCRIPT_DIR/portfolio-service.yaml" -f "$SCRIPT_DIR/transaction-service.yaml" -f "$SCRIPT_DIR/audit-service.yaml"

kubectl get pods -n portfolio