-- Order receipts, idempotency records and the transactional outbox.
-- Intake keeps a receipt only: the full order (with personal data) travels in the outbox event, whose
-- payload is cleared once published.

create table accepted_order (
    id                 char(36) character set ascii collate ascii_bin not null primary key,
    partner_id         varchar(64) not null,
    external_reference varchar(64) not null,
    status             varchar(16) not null,
    accepted_at        datetime(6) not null,
    -- Business duplicate check: a partner's own order reference is unique.
    constraint uk_accepted_order_partner_reference unique (partner_id, external_reference),
    constraint ck_accepted_order_status check (status in ('ACCEPTED'))
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table idempotency_record (
    partner_id      varchar(64) not null,
    idempotency_key varchar(64) not null,
    -- SHA-256 (hex) of the canonical request body: same key with a different body is rejected.
    request_hash    char(64) character set ascii not null,
    order_id        char(36) character set ascii collate ascii_bin not null,
    created_at      datetime(6) not null,
    primary key (partner_id, idempotency_key),
    constraint fk_idempotency_record_order foreign key (order_id) references accepted_order (id),
    -- Nightly purge of records older than the 30-day replay window.
    index ix_idempotency_record_created_at (created_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table outbox_event (
    id             bigint auto_increment primary key,
    event_id       char(36) character set ascii not null,
    event_type     varchar(64) not null,
    routing_key    varchar(64) not null,
    correlation_id varchar(64),
    payload        json,
    created_at     datetime(6) not null,
    published_at   datetime(6),
    constraint uk_outbox_event_event_id unique (event_id),
    -- The relay's scan: unpublished rows, oldest first.
    index ix_outbox_event_unpublished (published_at, id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
