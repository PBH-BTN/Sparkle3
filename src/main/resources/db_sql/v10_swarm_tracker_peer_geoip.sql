ALTER TABLE "public"."swarm_tracker"
    ADD COLUMN "peer_geoip" jsonb DEFAULT NULL;

CREATE INDEX "swarm_tracker_peer_geoip_idx" ON "public"."swarm_tracker" USING gin (
                                                                                   "peer_geoip"
    ) WITH (GIN_PENDING_LIST_LIMIT = 4096);