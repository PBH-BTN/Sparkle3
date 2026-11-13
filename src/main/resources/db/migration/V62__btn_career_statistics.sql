-- Maintenance window: serialize backfill with live source writes.
LOCK TABLE swarm_tracker, ban_history, client_discovery IN SHARE ROW EXCLUSIVE MODE;
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
CREATE FUNCTION career_count_event() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO user_career_daily(user_id, day, kind, amount)
    VALUES(NEW.user_id, (NEW.observed_at AT TIME ZONE 'UTC')::date, NEW.kind, 1)
    ON CONFLICT(user_id, kind, day) DO UPDATE SET amount = user_career_daily.amount + 1;
    RETURN NEW;
END $$;
CREATE TRIGGER career_event_count AFTER INSERT ON user_career_events
    FOR EACH ROW EXECUTE FUNCTION career_count_event();

CREATE FUNCTION career_source_event() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    reporter bigint;
    moment timestamptz;
    started timestamptz;
    key uuid;
    event_kind text;
BEGIN
    IF TG_TABLE_NAME = 'client_discovery' THEN
        SELECT owner INTO reporter FROM userapp WHERE id = NEW.found_userapps_id;
        moment := NEW.found_at;
        key := md5('feature:' || NEW.hash::text)::uuid;
        event_kind := 'feature';
    ELSE
        SELECT owner INTO reporter FROM userapp WHERE id = NEW.userapps_id;
        IF TG_TABLE_NAME = 'ban_history' THEN
            moment := NEW.populate_time;
            started := moment;
            key := md5(jsonb_build_array('ban', NEW.userapps_id, NEW.torrent_id,
                host(NEW.peer_ip), NEW.peer_port, extract(epoch FROM moment), NEW.module_name, NEW.rule)::text)::uuid;
            event_kind := 'ban';
        ELSE
            moment := NEW.last_time_seen;
            started := NEW.first_time_seen;
            key := md5(jsonb_build_array('swarm', NEW.userapps_id, NEW.user_downloader,
                NEW.torrent_id, host(NEW.peer_ip), NEW.peer_port, extract(epoch FROM started))::text)::uuid;
            event_kind := 'swarm';
        END IF;
    END IF;
    IF reporter IS NULL OR EXISTS (SELECT 1 FROM user_account_deletion_request
        WHERE user_id = reporter AND status = 'COMPLETED') THEN RETURN NEW; END IF;
    INSERT INTO user_career_events(user_id, kind, fingerprint, observed_at)
    VALUES(reporter, event_kind, key, moment) ON CONFLICT DO NOTHING;

    IF TG_TABLE_NAME <> 'client_discovery' THEN
        INSERT INTO user_career_events(user_id, kind, fingerprint, observed_at, reporter_id)
        SELECT DISTINCT ua.owner, 'observation', md5(key::text || extract(epoch FROM moment)::text)::uuid, moment, reporter
        FROM userapps_heartbeat hb JOIN userapp ua ON ua.id = hb.userapp_id
        WHERE hb.ip = NEW.peer_ip AND ua.owner <> reporter
          AND hb.first_seen_at <= moment AND hb.last_seen_at >= started
          AND NOT EXISTS (SELECT 1 FROM user_account_deletion_request d
                          WHERE d.user_id = ua.owner AND d.status = 'COMPLETED')
        ON CONFLICT(user_id, kind, fingerprint) DO UPDATE
            SET observed_at = GREATEST(user_career_events.observed_at, EXCLUDED.observed_at);
    END IF;
    IF TG_TABLE_NAME = 'swarm_tracker' THEN
        INSERT INTO user_career_torrent_time AS activity(user_id, torrent_id, last_seen, was_seeding)
        VALUES(reporter, NEW.torrent_id, moment, NEW.user_progress >= 1)
        ON CONFLICT(user_id,torrent_id) DO UPDATE SET
            seeding = activity.seeding + CASE WHEN activity.was_seeding AND EXCLUDED.last_seen > activity.last_seen
                THEN tstzmultirange(tstzrange(activity.last_seen, LEAST(EXCLUDED.last_seen, activity.last_seen + interval '5 minutes'),'[)')) ELSE '{}'::tstzmultirange END,
            downloading = activity.downloading + CASE WHEN NOT activity.was_seeding AND EXCLUDED.last_seen > activity.last_seen
                THEN tstzmultirange(tstzrange(activity.last_seen, LEAST(EXCLUDED.last_seen, activity.last_seen + interval '5 minutes'),'[)')) ELSE '{}'::tstzmultirange END,
            was_seeding = CASE WHEN EXCLUDED.last_seen > activity.last_seen THEN EXCLUDED.was_seeding ELSE activity.was_seeding END,
            last_seen = GREATEST(activity.last_seen,EXCLUDED.last_seen);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER career_ban AFTER INSERT ON ban_history FOR EACH ROW EXECUTE FUNCTION career_source_event();
CREATE TRIGGER career_feature AFTER INSERT ON client_discovery FOR EACH ROW EXECUTE FUNCTION career_source_event();
CREATE TRIGGER career_swarm AFTER INSERT OR UPDATE ON swarm_tracker FOR EACH ROW EXECUTE FUNCTION career_source_event();

-- Set-based backfill: never rewrite compressed/raw source tables.
WITH sources AS (
    SELECT ua.owner, 'swarm'::text AS kind,
        md5(jsonb_build_array('swarm', st.userapps_id, st.user_downloader,st.torrent_id,
            host(st.peer_ip),st.peer_port,extract(epoch FROM st.first_time_seen))::text)::uuid AS key,
        st.last_time_seen AS moment, st.first_time_seen AS started, st.peer_ip
    FROM swarm_tracker st JOIN userapp ua ON ua.id = st.userapps_id
    UNION ALL
    SELECT ua.owner, 'ban',
        md5(jsonb_build_array('ban',bh.userapps_id,bh.torrent_id,host(bh.peer_ip),bh.peer_port,
            extract(epoch FROM bh.populate_time),bh.module_name,bh.rule)::text)::uuid,
        bh.populate_time, bh.populate_time, bh.peer_ip
    FROM ban_history bh JOIN userapp ua ON ua.id = bh.userapps_id
), events AS (
    SELECT owner, kind, key, moment, NULL::bigint AS reporter FROM sources
    UNION ALL
    SELECT DISTINCT ua.owner, 'observation', md5(s.key::text || extract(epoch FROM s.moment)::text)::uuid,
        s.moment, s.owner FROM sources s JOIN userapps_heartbeat hb
        ON hb.ip=s.peer_ip AND hb.first_seen_at <= s.moment AND hb.last_seen_at >= s.started
        JOIN userapp ua ON ua.id=hb.userapp_id WHERE ua.owner<>s.owner
    UNION ALL
    SELECT ua.owner, 'feature', md5('feature:' || cd.hash::text)::uuid, cd.found_at, NULL::bigint
    FROM client_discovery cd JOIN userapp ua ON ua.id=cd.found_userapps_id
)
INSERT INTO user_career_events(user_id,kind,fingerprint,observed_at,reporter_id)
SELECT DISTINCT owner,kind,key,moment,reporter FROM events e
WHERE NOT EXISTS (SELECT 1 FROM user_account_deletion_request d WHERE d.user_id=e.owner AND d.status='COMPLETED')
ON CONFLICT DO NOTHING;
-- No historical activity intervals can be inferred reliably from current peer progress.
