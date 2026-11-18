CREATE TABLE btn_bypass_conflict_alerts (
    recipient_id bigint NOT NULL,
    application_id bigint NOT NULL,
    match_fingerprint varchar(64) NOT NULL,
    PRIMARY KEY (recipient_id, application_id, match_fingerprint)
);
