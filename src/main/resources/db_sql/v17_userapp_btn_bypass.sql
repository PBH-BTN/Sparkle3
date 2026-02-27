ALTER TABLE "public"."userapp"
    ADD COLUMN "btn_bypass" bool NOT NULL DEFAULT FALSE;

CREATE INDEX "userapp_btn_bypass" ON "public"."userapp" USING btree (
                                                                     "btn_bypass" NULLS LAST
    );