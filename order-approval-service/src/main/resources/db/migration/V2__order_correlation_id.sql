-- The request ID of the partner call that placed the order, from the order.accepted event. The Fulfilment
-- submitter runs outside any request, so it uses this ID for its logs, its call to Fulfilment and the events
-- it publishes. Null for orders received before this column existed.
alter table customer_order add column correlation_id varchar(64) collate utf8mb4_bin;
