CREATE TABLE content_moderation_dictionary (
    id integer PRIMARY KEY CHECK (id = 1),
    version bigint NOT NULL DEFAULT 0,
    enabled boolean NOT NULL DEFAULT false,
    words text NOT NULL DEFAULT ''
);

INSERT INTO content_moderation_dictionary (id) VALUES (1);
