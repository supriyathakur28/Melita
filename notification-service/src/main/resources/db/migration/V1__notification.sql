-- Notification log: one row per email. The unique key is the consumer's dedupe; the status records delivery.

create table notification (
    id         bigint auto_increment primary key,
    event_id   char(36) character set ascii not null,
    template   varchar(32)  not null,
    order_id   char(36) character set ascii collate ascii_bin not null,
    recipient  varchar(254) not null,
    status     varchar(16)  not null,
    attempts   int          not null default 0,
    last_error varchar(255),
    created_at datetime(6)  not null,
    sent_at    datetime(6),
    constraint uk_notification_event_template unique (event_id, template),
    constraint ck_notification_status check (status in ('PENDING', 'SENT', 'FAILED')),
    -- Failed or stuck notifications, oldest first (support view and alerting).
    index ix_notification_status_created (status, created_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
