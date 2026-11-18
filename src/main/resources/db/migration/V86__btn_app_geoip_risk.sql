CREATE TABLE btn_app_geoip_observations (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id bigint NOT NULL,
    observed_at timestamptz NOT NULL,
    address_family smallint NOT NULL,
    country varchar(2) NOT NULL,
    province text NOT NULL,
    processed boolean NOT NULL DEFAULT false
);

CREATE INDEX btn_app_geoip_observations_app_time_idx
    ON btn_app_geoip_observations (application_id, observed_at, id);
CREATE INDEX btn_app_geoip_observations_pending_idx
    ON btn_app_geoip_observations (id) WHERE NOT processed;
CREATE INDEX btn_app_geoip_observations_time_idx
    ON btn_app_geoip_observations (observed_at, id);

CREATE TABLE btn_app_geoip_deliveries (
    application_id bigint NOT NULL,
    recipient_id bigint NOT NULL,
    address_family smallint NOT NULL,
    last_alert_at timestamptz NOT NULL,
    PRIMARY KEY (application_id, recipient_id, address_family)
);

CREATE INDEX btn_app_geoip_deliveries_time_idx ON btn_app_geoip_deliveries (last_alert_at);
