# Order events: contract for consumers

Any system can follow the order lifecycle by binding its own queue to the `orders.events` exchange.
Inside the platform, order-approval and care consume it. This page is the contract; the Java records live in `order-events-contract/`.

## Binding a queue

| Item | Value |
|---|---|
| Exchange | `orders.events` (topic, durable) |
| Routing key | the event type, e.g. `order.accepted`; bind `order.#` for everything |
| Your queue | owned and declared by you; durable quorum queue with your own dead-letter queue recommended |

Inside the platform, `order-approval.order-accepted` and `care.order-events` are bound this way. Any other
team binds its own queue the same way.

## Delivery semantics

- **At-least-once.** An event can occasionally arrive twice, e.g. when the publisher crashes between the
  broker's confirm and recording it. Deduplicate on `eventId` (also the AMQP `message_id`).
- **No ordering guarantee.** Do not rely on the order of arrival. Use `occurredAt` and the order's status
  if you need to reason about sequence.
- **Schema evolution.** Fields are only added within a `schemaVersion`. Ignore fields you do not know. A
  breaking change gets a new `schemaVersion`, announced in advance.

## Envelope

Every message body is a JSON envelope:

```json
{
  "eventId": "8d0c3f6e-5d1b-4b8e-9f63-0a6f8e1c2b4d",
  "eventType": "order.accepted",
  "schemaVersion": 1,
  "occurredAt": "2026-10-29T10:15:30.123456Z",
  "correlationId": "6f1c2a9e-…",
  "data": { }
}
```

AMQP properties repeat the metadata:
- `message_id` = `eventId`
- `type` = `eventType`
- `content_type` = `application/json`
- delivery mode persistent
- header `x-correlation-id` = `correlationId`, the request ID of the call that caused the event; quote it
  when reporting a problem.

## Events

| Event | Published by | When | Personal data |
|---|---|---|---|
| `order.accepted` | order-intake | An order passed validation and was accepted | **Yes**: the full order |
| `order.approval-requested` | order-approval | The order contains a product or package that needs an agent's approval | No |
| `order.approved` | order-approval | An agent approved a held order | No |
| `order.rejected` | order-approval | An agent rejected a held order (final) | No |
| `order.submitted` | order-approval | Fulfilment accepted the order | No |
| `order.fulfilment-failed` | order-approval | Fulfilment refused the order, or retries were exhausted; needs manual follow-up | No |

Only `order.accepted` carries personal data, because Fulfilment and Care need it. Handle it accordingly:
restrict access to your queue and do not log the payload. Later events refer to the order by `orderId`.

### `order.accepted`

`installation` is present only when a product needs a technician visit (Internet, TV, Telephony). Its
address is always resolved, even when the partner asked to reuse the customer's address.

```json
{
  "orderId": "3f2c8e1a-8d4b-4c71-9a55-0e6b2f1d7c90",
  "partnerId": "erp-partner",
  "externalReference": "ERP-2026-000123",
  "customer": {
    "firstName": "Maria", "lastName": "Borg", "dateOfBirth": "1990-04-12",
    "email": "maria.borg@example.com", "mobileNumber": "+35679123456",
    "identityDocument": { "type": "ID_CARD", "number": "123456M" },
    "address": { "line1": "12, Triq il-Kbira", "line2": null, "locality": "Mosta", "postcode": "MST 1234" }
  },
  "installation": {
    "address": { "line1": "12, Triq il-Kbira", "line2": null, "locality": "Mosta", "postcode": "MST 1234" },
    "preferredDate": "2026-11-09", "timeSlot": "MORNING"
  },
  "items": [
    { "product": "INTERNET", "packageCode": "INTERNET_1GBPS", "packageName": "Internet 1Gbps" },
    { "product": "MOBILE", "packageCode": "MOBILE_POSTPAID", "packageName": "Mobile post-paid" }
  ],
  "acceptedAt": "2026-10-29T10:15:30.123456Z"
}
```

### `order.approval-requested`

```json
{
  "orderId": "3f2c8e1a-…", "externalReference": "ERP-2026-000123",
  "approvalReasons": ["PACKAGE:MOBILE_POSTPAID"],
  "items": [ { "product": "MOBILE", "packageCode": "MOBILE_POSTPAID", "packageName": "Mobile post-paid" } ]
}
```

### `order.approved`

`preferredSlotExpired` is true when the preferred installation date had already passed at approval; a new
date must be agreed with the customer.

```json
{ "orderId": "3f2c8e1a-…", "externalReference": "ERP-2026-000123", "decidedBy": "agent1",
  "decidedAt": "2026-10-29T11:02:00Z", "comment": "Customer identity checked", "preferredSlotExpired": false }
```

### `order.rejected`

```json
{ "orderId": "3f2c8e1a-…", "externalReference": "ERP-2026-000123", "decidedBy": "agent1",
  "decidedAt": "2026-10-29T11:02:00Z", "reason": "Customer failed the credit check" }
```

### `order.submitted`

```json
{ "orderId": "3f2c8e1a-…", "externalReference": "ERP-2026-000123", "fulfilmentReference": "FUL-1A2B3C4D",
  "submittedAt": "2026-10-29T11:02:05Z" }
```

### `order.fulfilment-failed`

`lastError` is a short code and message, never a raw response body.

```json
{ "orderId": "3f2c8e1a-…", "externalReference": "ERP-2026-000123", "attempts": 5,
  "lastError": "retries exhausted: HTTP 503 from Fulfilment" }
```
