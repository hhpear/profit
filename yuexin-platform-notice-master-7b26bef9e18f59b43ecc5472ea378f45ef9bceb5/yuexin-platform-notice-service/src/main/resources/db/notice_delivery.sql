-- Apply once to the Notice shared database before enabling reliable delivery.
CREATE TABLE notice_delivery (
 delivery_id VARCHAR(64) PRIMARY KEY,
 idempotency_key VARCHAR(191) NOT NULL UNIQUE,
 application VARCHAR(64) NOT NULL,
 data_day CHAR(10) NOT NULL,
 mode VARCHAR(16) NOT NULL,
 recipient_id VARCHAR(128) NOT NULL,
 binding_sha CHAR(64) NOT NULL,
 request_json LONGTEXT NOT NULL,
 state VARCHAR(24) NOT NULL,
 message_id VARCHAR(128), chat_id VARCHAR(128), error_code VARCHAR(128),
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_notice_slot(application,data_day,mode,recipient_id)
);
