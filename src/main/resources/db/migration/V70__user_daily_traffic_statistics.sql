-- Final daily results only. No raw records, checkpoints or historical backfill.
CREATE TABLE user_traffic_daily (
    user_id bigint NOT NULL,
    day date NOT NULL,
    upload numeric NOT NULL DEFAULT 0,
    download numeric NOT NULL DEFAULT 0,
    upload_ack numeric NOT NULL DEFAULT 0,
    download_ack numeric NOT NULL DEFAULT 0,
    loss numeric NOT NULL DEFAULT 0,
    seeding numeric NOT NULL DEFAULT 0,
    downloading numeric NOT NULL DEFAULT 0,
    loss_ratio numeric GENERATED ALWAYS AS
        (CASE WHEN upload > 0 THEN round(loss * 100 / upload, 2) ELSE 0 END) STORED,
    PRIMARY KEY(user_id,day)
);
