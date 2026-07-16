#!/bin/bash
set -e

for db in auth_service_db wallet_service_db stock_service_db portfolio_service_db transaction_service_db audit_service_db; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -c "CREATE DATABASE $db"
done
