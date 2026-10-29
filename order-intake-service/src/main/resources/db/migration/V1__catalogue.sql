-- Product catalogue: reference data, changed only by releases. Read on every order (cached in Redis).

create table product (
    code                  varchar(32)  not null primary key,
    name                  varchar(100) not null,
    requires_installation boolean      not null,
    active                boolean      not null default true
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table product_package (
    code         varchar(64)  not null primary key,
    product_code varchar(32)  not null,
    name         varchar(100) not null,
    active       boolean      not null default true,
    constraint fk_product_package_product foreign key (product_code) references product (code)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

insert into product (code, name, requires_installation) values
    ('INTERNET', 'Internet', true),
    ('TV', 'TV', true),
    ('TELEPHONY', 'Telephony', true),
    ('MOBILE', 'Mobile', false);

insert into product_package (code, product_code, name) values
    ('INTERNET_250MBPS', 'INTERNET', 'Internet 250Mbps'),
    ('INTERNET_1GBPS', 'INTERNET', 'Internet 1Gbps'),
    ('TV_90_CHANNELS', 'TV', 'TV with 90 channels'),
    ('TV_140_CHANNELS', 'TV', 'TV with 140 channels'),
    ('TELEPHONY_FREE_ONNET', 'TELEPHONY', 'Telephony with free on-net calls'),
    ('TELEPHONY_UNLIMITED', 'TELEPHONY', 'Telephony with unlimited calls'),
    ('MOBILE_PREPAID', 'MOBILE', 'Mobile prepaid'),
    ('MOBILE_POSTPAID', 'MOBILE', 'Mobile post-paid');
