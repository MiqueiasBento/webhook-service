#!/usr/bin/env bash
set -euo pipefail

api_base="${API_BASE:-http://127.0.0.1:8080}"
receiver_url="${RECEIVER_URL:-http://127.0.0.1:8090/webhook}"
: "${API_KEY:?Defina API_KEY antes de executar a demonstração}"

create_destination() {
  curl --fail-with-body -sS -X POST "$api_base/api/v1/destinations" \
    -H 'Content-Type: application/json' \
    -H "X-API-Key: $API_KEY" \
    -d "{\"name\":\"$1\",\"url\":\"$receiver_url\"}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])'
}

sales_id="$(create_destination 'Logistica')"
schedule_id="$(create_destination 'Agenda')"

echo "Evento de vendas (uma tentativa):"
curl --fail-with-body -sS -X POST "$api_base/api/v1/events" \
  -H 'Content-Type: application/json' \
  -H "X-API-Key: $API_KEY" \
  -d "{\"destinationId\":$sales_id,\"eventType\":\"pedido_criado\",\"payload\":{\"orderId\":1523}}"
echo

echo "Evento de agendamento (falha 500 duas vezes antes do sucesso):"
curl --fail-with-body -sS -X POST "$api_base/api/v1/events" \
  -H 'Content-Type: application/json' \
  -H "X-API-Key: $API_KEY" \
  -d "{\"destinationId\":$schedule_id,\"eventType\":\"consulta_marcada\",\"payload\":{\"appointmentId\":77}}"
echo
