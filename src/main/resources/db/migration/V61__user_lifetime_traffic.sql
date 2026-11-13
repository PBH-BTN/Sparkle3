-- Keep totals and high-water marks independently of raw swarm retention.
-- This backfill scans swarm_tracker and blocks writes: deploy in a maintenance window.
LOCK TABLE public.swarm_tracker IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE public.user_privacy_settings
    ADD COLUMN lifetime_traffic_visibility VARCHAR(16) NOT NULL DEFAULT 'HIDDEN'
        CHECK (lifetime_traffic_visibility IN ('SHOW', 'HIDDEN'));

CREATE INDEX userapps_heartbeat_lifetime_ip_idx
    ON public.userapps_heartbeat (ip, first_seen_at, last_seen_at);

CREATE TABLE public.user_lifetime_traffic (
    user_id BIGINT PRIMARY KEY REFERENCES public."user" (id) ON DELETE CASCADE,
    sent_self NUMERIC NOT NULL DEFAULT 0 CHECK (sent_self >= 0),
    received_self NUMERIC NOT NULL DEFAULT 0 CHECK (received_self >= 0),
    sent_ack NUMERIC NOT NULL DEFAULT 0 CHECK (sent_ack >= 0),
    received_ack NUMERIC NOT NULL DEFAULT 0 CHECK (received_ack >= 0),
    tracked_since TIMESTAMPTZ NOT NULL,
    history_partial BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A hash avoids retaining another copy of peer addresses and downloader names.
-- Include the client session's first_seen time so a new counter epoch starts at zero.
CREATE TABLE public.user_lifetime_traffic_checkpoint (
    user_id BIGINT NOT NULL REFERENCES public."user" (id) ON DELETE CASCADE,
    session_key UUID NOT NULL,
    uploaded BIGINT NOT NULL,
    downloaded BIGINT NOT NULL,
    PRIMARY KEY (user_id, session_key)
);

INSERT INTO public.user_lifetime_traffic (user_id, tracked_since, history_partial)
SELECT u.id, now(), TRUE FROM public."user" u
WHERE NOT EXISTS (SELECT 1 FROM public.user_account_deletion_request d
                  WHERE d.user_id = u.id AND d.status = 'COMPLETED');

CREATE FUNCTION public.accumulate_user_lifetime_traffic() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    owner_id BIGINT;
    fingerprint UUID;
    previous_upload BIGINT;
    previous_download BIGINT;
    upload_delta NUMERIC;
    download_delta NUMERIC;
BEGIN
    SELECT owner INTO owner_id FROM public.userapp WHERE id = NEW.userapps_id;
    IF owner_id IS NULL OR EXISTS (
        SELECT 1 FROM public.user_account_deletion_request
        WHERE user_id = owner_id AND status = 'COMPLETED'
    ) THEN RETURN NEW; END IF;

    fingerprint := md5(jsonb_build_array(NEW.userapps_id, NEW.user_downloader,
        NEW.torrent_id, host(NEW.peer_ip), NEW.peer_port,
        extract(epoch FROM NEW.first_time_seen))::text)::uuid;

    INSERT INTO public.user_lifetime_traffic_checkpoint
        (user_id, session_key, uploaded, downloaded)
    VALUES (owner_id, fingerprint, 0, 0)
    ON CONFLICT DO NOTHING;

    SELECT uploaded, downloaded INTO previous_upload, previous_download
    FROM public.user_lifetime_traffic_checkpoint
    WHERE user_id = owner_id AND session_key = fingerprint FOR UPDATE;

    upload_delta := GREATEST(NEW.to_peer_traffic::numeric - previous_upload, 0);
    download_delta := GREATEST(NEW.from_peer_traffic::numeric - previous_download, 0);
    IF upload_delta = 0 AND download_delta = 0 THEN RETURN NEW; END IF;

    UPDATE public.user_lifetime_traffic_checkpoint
    SET uploaded = GREATEST(uploaded, NEW.to_peer_traffic),
        downloaded = GREATEST(downloaded, NEW.from_peer_traffic)
    WHERE user_id = owner_id AND session_key = fingerprint;

    -- One acknowledgement per user even when several apps share an address.
    -- Reverse the directions when viewing the same traffic from the peer's side.
    INSERT INTO public.user_lifetime_traffic AS totals
        (user_id, sent_self, received_self, sent_ack, received_ack,
         tracked_since, history_partial, updated_at)
    SELECT contribution.user_id, SUM(contribution.sent_self), SUM(contribution.received_self),
           SUM(contribution.sent_ack), SUM(contribution.received_ack),
           GREATEST(u.register_at, NEW.first_time_seen), FALSE, now()
    FROM (
        SELECT owner_id AS user_id, upload_delta AS sent_self,
               download_delta AS received_self, 0::numeric AS sent_ack, 0::numeric AS received_ack
        UNION ALL
        SELECT DISTINCT ua.owner, 0::numeric, 0::numeric, download_delta, upload_delta
        FROM public.userapps_heartbeat heartbeat
        JOIN public.userapp ua ON ua.id = heartbeat.userapp_id
        WHERE heartbeat.ip = NEW.peer_ip
          AND heartbeat.first_seen_at <= NEW.last_time_seen
          AND heartbeat.last_seen_at >= NEW.first_time_seen
    ) contribution
    JOIN public."user" u ON u.id = contribution.user_id
    WHERE NOT EXISTS (SELECT 1 FROM public.user_account_deletion_request d
                      WHERE d.user_id = u.id AND d.status = 'COMPLETED')
    GROUP BY contribution.user_id, u.register_at
    ORDER BY contribution.user_id
    ON CONFLICT (user_id) DO UPDATE SET
        sent_self = totals.sent_self + EXCLUDED.sent_self,
        received_self = totals.received_self + EXCLUDED.received_self,
        sent_ack = totals.sent_ack + EXCLUDED.sent_ack,
        received_ack = totals.received_ack + EXCLUDED.received_ack,
        tracked_since = LEAST(totals.tracked_since, EXCLUDED.tracked_since),
        updated_at = EXCLUDED.updated_at;
    RETURN NEW;
END;
$$;

CREATE TRIGGER swarm_tracker_lifetime_traffic
AFTER INSERT OR UPDATE ON public.swarm_tracker
FOR EACH ROW EXECUTE FUNCTION public.accumulate_user_lifetime_traffic();

-- Seed high-water marks without rewriting the raw table.
INSERT INTO public.user_lifetime_traffic_checkpoint (user_id, session_key, uploaded, downloaded)
SELECT ua.owner, md5(jsonb_build_array(st.userapps_id, st.user_downloader,
           st.torrent_id, host(st.peer_ip), st.peer_port,
           extract(epoch FROM st.first_time_seen))::text)::uuid,
       GREATEST(st.to_peer_traffic, 0), GREATEST(st.from_peer_traffic, 0)
FROM public.swarm_tracker st
JOIN public.userapp ua ON ua.id = st.userapps_id
JOIN public.user_lifetime_traffic t ON t.user_id = ua.owner;

WITH retained AS MATERIALIZED (
    SELECT st.id, ua.owner, st.peer_ip, st.first_time_seen, st.last_time_seen,
           GREATEST(st.to_peer_traffic, 0)::numeric AS upload,
           GREATEST(st.from_peer_traffic, 0)::numeric AS download
    FROM public.swarm_tracker st
    JOIN public.userapp ua ON ua.id = st.userapps_id
), addresses AS MATERIALIZED (
    SELECT ua.owner, hb.ip, hb.first_seen_at, hb.last_seen_at
    FROM public.userapps_heartbeat hb JOIN public.userapp ua ON ua.id = hb.userapp_id
), acknowledgements AS (
    SELECT DISTINCT a.owner, r.id, r.download, r.upload, r.first_time_seen
    FROM retained r JOIN addresses a
      ON a.ip = r.peer_ip AND a.first_seen_at <= r.last_time_seen
     AND a.last_seen_at >= r.first_time_seen
), contributions AS (
    SELECT owner, upload AS sent_self, download AS received_self,
           0::numeric AS sent_ack, 0::numeric AS received_ack, first_time_seen
    FROM retained
    UNION ALL
    SELECT owner, 0::numeric, 0::numeric, download, upload, first_time_seen
    FROM acknowledgements
), totals AS (
    SELECT owner, SUM(sent_self) AS sent_self, SUM(received_self) AS received_self,
           SUM(sent_ack) AS sent_ack, SUM(received_ack) AS received_ack,
           MIN(first_time_seen) AS tracked_since
    FROM contributions GROUP BY owner
)
UPDATE public.user_lifetime_traffic t
SET sent_self = s.sent_self, received_self = s.received_self,
    sent_ack = s.sent_ack, received_ack = s.received_ack,
    tracked_since = GREATEST(u.register_at, LEAST(t.tracked_since, s.tracked_since))
FROM totals s JOIN public."user" u ON u.id = s.owner
WHERE t.user_id = s.owner;

CREATE FUNCTION public.initialize_user_lifetime_traffic() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO public.user_lifetime_traffic (user_id, tracked_since, history_partial)
    VALUES (NEW.id, NEW.register_at, FALSE);
    RETURN NEW;
END;
$$;

CREATE TRIGGER user_initialize_lifetime_traffic
AFTER INSERT ON public."user"
FOR EACH ROW EXECUTE FUNCTION public.initialize_user_lifetime_traffic();
