CREATE INDEX idx_ban_history_module_time_traffic
    ON ban_history (module_name, populate_time)
    INCLUDE (peer_ip, userapps_id, to_peer_traffic, from_peer_traffic);