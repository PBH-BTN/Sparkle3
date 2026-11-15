CREATE TABLE public.ban_submission_replay_daily (
    day date NOT NULL,
    app_id bigint NOT NULL,
    replay_batches bigint NOT NULL,
    replays bigint NOT NULL,
    PRIMARY KEY (day, app_id)
);
