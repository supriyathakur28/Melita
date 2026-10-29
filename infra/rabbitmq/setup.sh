#!/bin/sh
# One-shot setup through the RabbitMQ management API, run by the rabbitmq-setup compose service:
#  - one user per service, allowed only on its own exchange and queues (least privilege);
#  - demo queues standing in for Fulfilment and Care, so every event can be seen in the management UI.
set -eu

API="http://rabbitmq:15672/api"
AUTH="admin:${RABBITMQ_ADMIN_PASSWORD}"

put() {
  curl -fsS -u "$AUTH" -H 'content-type: application/json' -X PUT "$API/$1" -d "$2"
}

user() {
  put "users/$1" "{\"password\":\"$2\",\"tags\":\"\"}"
  put "permissions/%2F/$1" "{\"configure\":\"$3\",\"write\":\"$4\",\"read\":\"$5\"}"
}

# Producers declare the exchanges; each consumer declares and reads only its own queues.
# Backslashes are doubled because the patterns are embedded in JSON strings.
user order-intake "$ORDER_INTAKE_RABBITMQ_PASSWORD" \
  '^orders\\.events.*$' '^orders\\.events$' '^$'
user order-management "$ORDER_MANAGEMENT_RABBITMQ_PASSWORD" \
  '^(orders\\.events.*|order-management\\..*)$' '^(orders\\.events.*|order-management\\..*)$' '^(orders\\.events.*|order-management\\..*)$'
user notification "$NOTIFICATION_RABBITMQ_PASSWORD" \
  '^(orders\\.events.*|notification\\..*)$' '^(orders\\.events.*|notification\\..*)$' '^(orders\\.events.*|notification\\..*)$'

put "exchanges/%2F/orders.events" '{"type":"topic","durable":true}'
for queue in fulfilment.order-events care.order-events; do
  put "queues/%2F/$queue" '{"durable":true,"arguments":{"x-queue-type":"quorum"}}'
  curl -fsS -u "$AUTH" -H 'content-type: application/json' -X POST \
    "$API/bindings/%2F/e/orders.events/q/$queue" -d '{"routing_key":"order.#"}'
done

echo "RabbitMQ users, permissions and demo queues are set up."
