ALTER TABLE user_lifetime_traffic_checkpoint
    ADD COLUMN upload_delta bigint DEFAULT 0,
    ADD COLUMN download_delta bigint DEFAULT 0;
