ALTER TABLE public.points_ledger
    ADD COLUMN IF NOT EXISTS manual_points BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN public.points_ledger.manual_points
    IS 'Signed administrator adjustment included in points but excluded from earned fixed, one-time, and dynamic totals.';

DROP FUNCTION IF EXISTS public.sparkle_apply_point_categories(
    BIGINT, DATE, JSONB
);

CREATE OR REPLACE FUNCTION public.sparkle_apply_point_categories(
    p_user_id BIGINT,
    p_point_day DATE,
    p_items JSONB
) RETURNS TABLE(result_category TEXT, result_points BIGINT)
LANGUAGE plpgsql
AS $$
DECLARE
    item JSONB;
    v_category TEXT;
    v_requested BIGINT;
    v_cap BIGINT;
    previous_awarded BIGINT;
    next_awarded BIGINT;
BEGIN
    FOR item IN SELECT value FROM jsonb_array_elements(p_items)
    LOOP
        v_category := item ->> 'category';
        v_requested := COALESCE((item ->> 'points')::BIGINT, 0);
        v_cap := CASE
            WHEN item ? 'cap' AND item ->> 'cap' IS NOT NULL
                THEN (item ->> 'cap')::BIGINT
            ELSE NULL
        END;
        IF v_category IS NULL OR v_requested <= 0 THEN
            CONTINUE;
        END IF;

        INSERT INTO public.point_daily_category (
            user_id, point_day, category, awarded, updated_at
        )
        VALUES (p_user_id, p_point_day, v_category, 0, NOW())
        ON CONFLICT (user_id, point_day, category) DO NOTHING;

        SELECT pdc.awarded
        INTO previous_awarded
        FROM public.point_daily_category pdc
        WHERE pdc.user_id = p_user_id
          AND pdc.point_day = p_point_day
          AND pdc.category = v_category
        FOR UPDATE;

        next_awarded := previous_awarded + v_requested;
        IF v_cap IS NOT NULL THEN
            next_awarded := LEAST(v_cap, next_awarded);
        END IF;

        IF next_awarded > previous_awarded THEN
            UPDATE public.point_daily_category pdc
            SET awarded = next_awarded,
                updated_at = NOW()
            WHERE pdc.user_id = p_user_id
              AND pdc.point_day = p_point_day
              AND pdc.category = v_category;
        END IF;

        result_category := v_category;
        result_points := GREATEST(0, next_awarded - previous_awarded);
        RETURN NEXT;
    END LOOP;
END;
$$;

DROP FUNCTION IF EXISTS public.sparkle_claim_point_awards(BIGINT, JSONB);

CREATE OR REPLACE FUNCTION public.sparkle_claim_point_awards(
    p_user_id BIGINT,
    p_items JSONB
) RETURNS TABLE(result_category TEXT, result_points BIGINT)
LANGUAGE plpgsql
AS $$
DECLARE
    item JSONB;
    claimed_category TEXT;
    claimed_points BIGINT;
BEGIN
    FOR item IN SELECT value FROM jsonb_array_elements(p_items)
    LOOP
        claimed_category := item ->> 'category';
        claimed_points := COALESCE((item ->> 'points')::BIGINT, 0);
        IF claimed_category IS NULL OR claimed_points <= 0 THEN
            CONTINUE;
        END IF;

        INSERT INTO public.point_claims (
            user_id, claim_type, reference, category, points, claimed_at
        )
        VALUES (
            p_user_id,
            item ->> 'claimType',
            item ->> 'reference',
            claimed_category,
            claimed_points,
            NOW()
        )
        ON CONFLICT (user_id, claim_type, reference) DO NOTHING;

        IF FOUND THEN
            result_category := claimed_category;
            result_points := claimed_points;
            RETURN NEXT;
        END IF;
    END LOOP;
END;
$$;

CREATE OR REPLACE FUNCTION public.sparkle_touch_point_app_activity(
    p_userapp_id BIGINT,
    p_user_id BIGINT,
    p_now TIMESTAMPTZ,
    p_gap_minutes INTEGER
) RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    previous_online_since TIMESTAMPTZ;
    previous_last_event TIMESTAMPTZ;
    previous_awarded_hours INTEGER;
    elapsed_hours INTEGER;
    online_delta BIGINT;
BEGIN
    INSERT INTO public.point_app_activity (
        userapp_id, user_id, online_since_at, last_event_at,
        awarded_online_hours, updated_at
    )
    VALUES (p_userapp_id, p_user_id, p_now, p_now, 0, p_now)
    ON CONFLICT (userapp_id) DO NOTHING;

    SELECT
        paa.online_since_at,
        paa.last_event_at,
        paa.awarded_online_hours
    INTO
        previous_online_since,
        previous_last_event,
        previous_awarded_hours
    FROM public.point_app_activity paa
    WHERE paa.userapp_id = p_userapp_id
    FOR UPDATE;

    IF previous_last_event < p_now
            - (GREATEST(p_gap_minutes, 1) * INTERVAL '1 minute') THEN
        previous_online_since := p_now;
        previous_awarded_hours := 0;
    END IF;

    elapsed_hours := GREATEST(
        0,
        FLOOR(EXTRACT(EPOCH FROM (p_now - previous_online_since)) / 3600)::INTEGER
    );
    online_delta := GREATEST(0, elapsed_hours - previous_awarded_hours);

    UPDATE public.point_app_activity paa
    SET user_id = p_user_id,
        online_since_at = previous_online_since,
        last_event_at = p_now,
        awarded_online_hours = GREATEST(previous_awarded_hours, elapsed_hours),
        updated_at = p_now
    WHERE paa.userapp_id = p_userapp_id;

    RETURN online_delta;
END;
$$;
