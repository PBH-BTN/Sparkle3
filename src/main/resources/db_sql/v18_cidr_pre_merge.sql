CREATE INDEX idx_ban_history_peer_cidr ON ban_history (
                                                       (CASE
                                                            WHEN family(peer_ip::inet) = 4 THEN host(peer_ip::inet)
                                                            WHEN family(peer_ip::inet) = 6
                                                                THEN set_masklen(peer_ip::inet, 52)::text
                                                           END)
    );

CREATE INDEX idx_swarm_tracker_peer_cidr ON swarm_tracker (
                                                       (CASE
                                                            WHEN family(peer_ip::inet) = 4 THEN host(peer_ip::inet)
                                                            WHEN family(peer_ip::inet) = 6
                                                                THEN set_masklen(peer_ip::inet, 52)::text
                                                           END)
    );