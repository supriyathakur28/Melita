-- Fulfilment orders: the final order created from an approved order. One row per Idempotency-Key: a retry
-- with the same key and body is answered from here, also after a restart. The ID number is encrypted by the
-- application (AES-GCM).

create table fulfilment_order (
    id                            char(36) character set ascii collate ascii_bin not null primary key,
    fulfilment_reference          varchar(32)  collate utf8mb4_bin not null,
    idempotency_key               varchar(64)  collate utf8mb4_bin not null,
    request_hash                  char(64) character set ascii not null,
    order_id                      char(36) character set ascii collate ascii_bin not null,
    partner_id                    varchar(64)  collate utf8mb4_bin not null,
    external_reference            varchar(64)  collate utf8mb4_bin not null,
    status                        varchar(16)  not null,
    customer_first_name           varchar(100) not null,
    customer_last_name            varchar(100) not null,
    customer_date_of_birth        date         not null,
    customer_email                varchar(254) not null,
    customer_mobile_number        varchar(16)  not null,
    id_document_type              varchar(16)  not null,
    id_document_number            varchar(128) not null,  -- encrypted: v1:<base64>
    customer_address_line1        varchar(100) not null,
    customer_address_line2        varchar(100),
    customer_address_locality     varchar(100) not null,
    customer_address_postcode     varchar(8)   not null,
    installation_date             date,
    installation_time_slot        varchar(16),
    installation_address_line1    varchar(100),
    installation_address_line2    varchar(100),
    installation_address_locality varchar(100),
    installation_address_postcode varchar(8),
    preferred_slot_expired        boolean,
    received_at                   datetime(6)  not null,
    -- The reference handed back to callers and used to look an order up.
    constraint uk_fulfilment_order_reference unique (fulfilment_reference),
    -- Idempotency: one fulfilment order per key.
    constraint uk_fulfilment_order_idempotency_key unique (idempotency_key),
    -- One fulfilment order per platform order, whatever key a caller uses.
    constraint uk_fulfilment_order_order_id unique (order_id),
    constraint ck_fulfilment_order_status check (status in ('SCHEDULED')),
    constraint ck_fulfilment_order_installation check (
        (installation_date is null and installation_time_slot is null and installation_address_line1 is null)
        or (installation_date is not null and installation_time_slot is not null and installation_address_line1 is not null))
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table fulfilment_order_item (
    id                  bigint auto_increment primary key,
    fulfilment_order_id char(36) character set ascii collate ascii_bin not null,
    product_code        varchar(32)  not null,
    package_code        varchar(64)  not null,
    package_name        varchar(100) not null,
    constraint fk_fulfilment_order_item_order foreign key (fulfilment_order_id) references fulfilment_order (id),
    constraint uk_fulfilment_order_item_product unique (fulfilment_order_id, product_code)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
