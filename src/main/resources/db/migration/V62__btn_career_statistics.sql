-- Schema only. Counters are maintained by the application. No historical backfill.
CREATE TABLE user_career_events (
    user_id bigint NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    kind text NOT NULL,
    fingerprint uuid NOT NULL,
    observed_at timestamptz NOT NULL,
    reporter_id bigint,
    PRIMARY KEY(user_id, kind, fingerprint)
);
CREATE TABLE user_career_daily (
    user_id bigint NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    day date NOT NULL,
    kind text NOT NULL,
    amount bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(user_id, kind, day)
);
CREATE INDEX user_career_latest_observation ON user_career_events(user_id, observed_at DESC)
    WHERE kind = 'observation';
CREATE TABLE user_career_torrent_time (
    user_id bigint NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    torrent_id bigint NOT NULL,
    seeding tstzmultirange NOT NULL DEFAULT '{}',
    downloading tstzmultirange NOT NULL DEFAULT '{}',
    last_seen timestamptz NOT NULL,
    was_seeding boolean NOT NULL,
    PRIMARY KEY(user_id, torrent_id)
);
