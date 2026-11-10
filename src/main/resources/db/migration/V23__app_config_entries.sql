CREATE TABLE IF NOT EXISTS "public"."app_config_entries" (
    "config_key" varchar(512) NOT NULL,
    "config_value" text NOT NULL,
    "value_type" varchar(32) NOT NULL DEFAULT 'string',
    "config_group" varchar(128) NOT NULL DEFAULT 'other',
    "source" varchar(32) NOT NULL DEFAULT 'yaml',
    "sensitive" bool NOT NULL DEFAULT false,
    "hot_reload" bool NOT NULL DEFAULT false,
    "updated_at" timestamptz NOT NULL DEFAULT now(),
    "updated_by" int8,
    CONSTRAINT "app_config_entries_pkey" PRIMARY KEY ("config_key")
);

CREATE INDEX IF NOT EXISTS "app_config_entries_group_idx"
    ON "public"."app_config_entries" ("config_group");
