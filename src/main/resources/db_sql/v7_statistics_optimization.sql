-- Add indexes to improve performance of User Swarm Statistics Update

-- Index for querying swarm_tracker by userapps_id (Self Report)
CREATE INDEX IF NOT EXISTS "swarm_tracker_userapps_id_idx" ON "public"."swarm_tracker" USING btree (
  "userapps_id"
);

-- Index for querying swarm_tracker by peer_ip (Other Ack)
-- Using GIST for inet containment operations (<<=)
CREATE INDEX IF NOT EXISTS "swarm_tracker_peer_ip_gist_idx" ON "public"."swarm_tracker" USING gist (
  "peer_ip" inet_ops
);

-- Index for filtering by time range
CREATE INDEX IF NOT EXISTS "swarm_tracker_time_seen_idx" ON "public"."swarm_tracker" USING btree (
  "first_time_seen",
  "last_time_seen"
);

