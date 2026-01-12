DROP INDEX "public"."user_privacy_mode_idx";

ALTER TABLE "public"."user"
    DROP COLUMN "privacy_mode",
    ADD COLUMN "privacy_level" int4 NOT NULL DEFAULT 2;

CREATE INDEX "user_privacy_level_idx" ON "public"."user" USING btree (
                                                                      "privacy_level"
    );