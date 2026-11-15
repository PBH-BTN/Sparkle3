ALTER TABLE public.ban_submission_target
    ADD COLUMN detection_hits bigint NOT NULL DEFAULT 0;
