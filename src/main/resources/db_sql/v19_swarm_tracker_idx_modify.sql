DROP INDEX "public"."swarm_tracker_unique_idx";

CREATE UNIQUE INDEX "swarm_tracker_unique_idx" ON "public"."swarm_tracker" USING btree (
                                                                                        "userapps_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
                                                                                        "user_downloader" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
                                                                                        "torrent_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
                                                                                        "peer_ip" "pg_catalog"."inet_ops" ASC NULLS LAST
    );