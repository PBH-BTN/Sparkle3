ALTER TABLE "public"."user"
    ADD COLUMN "privacy_mode" bool NOT NULL DEFAULT false;

CREATE INDEX "user_privacy_mode_idx" ON "public"."user" USING btree (
                                                                     "privacy_mode"
    );