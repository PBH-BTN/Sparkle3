CREATE TABLE public.ban_submission_fingerprint (
    event_day timestamptz NOT NULL,
    fingerprint uuid NOT NULL,
    PRIMARY KEY (event_day, fingerprint)
);

SELECT create_hypertable(
    'public.ban_submission_fingerprint',
    'event_day',
    chunk_time_interval => INTERVAL '1 day',
    create_default_indexes => FALSE
);

CREATE TABLE public.ban_submission_target (
    app_id bigint NOT NULL,
    target uuid NOT NULL,
    peer_network varchar(64) NOT NULL,
    peer_port integer NOT NULL,
    module_name varchar(128) NOT NULL,
    rule_name varchar(128),
    first_seen timestamptz NOT NULL,
    last_seen timestamptz NOT NULL,
    window_seen timestamptz,
    window_ban_at timestamptz,
    latest_ban_at timestamptz,
    windows integer NOT NULL,
    peak_windows integer NOT NULL,
    detected boolean NOT NULL DEFAULT false,
    replays bigint NOT NULL,
    suppressed bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (app_id, target)
);

CREATE INDEX ban_submission_target_state_expiry_idx
    ON public.ban_submission_target (last_seen)
    WHERE NOT detected AND replays = 0;

CREATE INDEX ban_submission_target_events_idx
    ON public.ban_submission_target (last_seen DESC, app_id, target)
    WHERE detected OR replays > 0;

CREATE INDEX ban_submission_target_app_events_idx
    ON public.ban_submission_target (app_id, last_seen DESC, target)
    WHERE detected OR replays > 0;
