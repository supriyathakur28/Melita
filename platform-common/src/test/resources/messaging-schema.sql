create table if not exists outbox_event (
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
);

create table if not exists processed_message (
    message_id   varchar(64) not null primary key,
    processed_at datetime(6) not null
);
