#!/bin/sh
# One-shot setup through the RabbitMQ management API, run by the rabbitmq-setup compose service: one user
# per service, allowed only on its own exchange and queues (least privilege). Each service declares its own
# queues in code.
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
user order-approval "$ORDER_APPROVAL_RABBITMQ_PASSWORD" \
  '^(orders\\.events.*|order-approval\\..*)$' '^(orders\\.events.*|order-approval\\..*)$' '^(orders\\.events.*|order-approval\\..*)$'
# care only consumes: it may bind its queues (read on the exchanges) and declare them with the dead-letter
# exchange (RabbitMQ requires write on the DLX for that), but never publish to orders.events.
user care "$CARE_RABBITMQ_PASSWORD" \
  '^(orders\\.events.*|care\\..*)$' '^(orders\\.events\\.dlx|care\\..*)$' '^(orders\\.events.*|care\\..*)$'

echo "RabbitMQ users and permissions are set up."
