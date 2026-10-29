-- The order aggregate and its lifecycle. Identity documents are encrypted by the application (AES-GCM).

create table customer_order (
    id                         char(36) character set ascii collate ascii_bin not null primary key,
    partner_id                 varchar(64)  not null,
    external_reference         varchar(64)  not null,
    status                     varchar(32)  not null,
    customer_first_name        varchar(100) not null,
    customer_last_name         varchar(100) not null,
    customer_date_of_birth     date         not null,
    customer_email             varchar(254) not null,
    customer_mobile_number     varchar(16)  not null,
    id_document_type           varchar(16)  not null,
    id_document_number         varchar(128) not null,  -- encrypted: v1:<base64>
    customer_address_line1     varchar(100) not null,
    customer_address_line2     varchar(100),
    customer_address_locality  varchar(100) not null,
    customer_address_postcode  varchar(8)   not null,
    installation_date          date,
    installation_time_slot     varchar(16),
    installation_address_line1    varchar(100),
    installation_address_line2    varchar(100),
    installation_address_locality varchar(100),
    installation_address_postcode varchar(8),
    approval_required          boolean      not null,
    approval_reasons           varchar(500),
    decided_by                 varchar(100),
    decided_at                 datetime(6),
    decision_note              varchar(500),
    fulfilment_reference       varchar(64),
    attempts                   int          not null default 0,
    next_attempt_at            datetime(6),
    last_error                 varchar(255),
    version                    bigint       not null,
    received_at                datetime(6)  not null,
    updated_at                 datetime(6)  not null,
    constraint uk_customer_order_partner_reference unique (partner_id, external_reference),
    constraint ck_customer_order_status check (status in
        ('PENDING_APPROVAL', 'READY_FOR_FULFILMENT', 'REJECTED', 'SUBMITTED', 'FULFILMENT_FAILED')),
    -- Installation details are all present or all absent.
    constraint ck_customer_order_installation check (
        (installation_date is null and installation_time_slot is null and installation_address_line1 is null)
        or (installation_date is not null and installation_time_slot is not null and installation_address_line1 is not null)),
    -- Agents' pending-approval list, oldest first.
    index ix_customer_order_status_received (status, received_at),
    -- The Fulfilment submitter's scan: READY orders whose next attempt is due.
    index ix_customer_order_status_next_attempt (status, next_attempt_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table order_item (
    id           bigint auto_increment primary key,
    order_id     char(36) character set ascii collate ascii_bin not null,
    product_code varchar(32)  not null,
    package_code varchar(64)  not null,
    package_name varchar(100) not null,
    constraint fk_order_item_order foreign key (order_id) references customer_order (id),
    constraint uk_order_item_product unique (order_id, product_code)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table order_status_history (
    id          bigint auto_increment primary key,
    order_id    char(36) character set ascii collate ascii_bin not null,
    from_status varchar(32),
    to_status   varchar(32)  not null,
    actor       varchar(100) not null,
    reason      varchar(500),
    occurred_at datetime(6)  not null,
    constraint fk_order_status_history_order foreign key (order_id) references customer_order (id),
    index ix_order_status_history_order (order_id, occurred_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- Inbox: one row per consumed message, written in the same transaction as its effect.
create table processed_message (
    message_id   varchar(64) not null primary key,
    processed_at datetime(6) not null
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
    index ix_outbox_event_unpublished (published_at, id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
