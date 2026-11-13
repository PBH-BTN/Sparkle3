CREATE INDEX IF NOT EXISTS user_score_bytes_ranking_idx
    ON public."user" (score_bytes DESC, id ASC);
