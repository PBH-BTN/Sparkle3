-- Reclassify migrated Afdian orders using the configured product plan list.
-- Consumed product rows remain product slots even if configuration later changes.
WITH afdian_product_plans AS (
    SELECT COALESCE(
        string_to_array(
            NULLIF(trim(config_value), ''),
            ','
        ),
        ARRAY[]::text[]
    ) AS plan_ids
    FROM public.app_config_entries
    WHERE config_key = 'sparkle.afdian.license-plan-ids'
)
UPDATE public.member_transactions tx
SET transaction_type = CASE
        WHEN tx.license_consumed
            OR trim(tx.sku) = ANY(plans.plan_ids)
            THEN 'PRODUCT'
        ELSE 'DONATION'
    END
FROM afdian_product_plans plans
WHERE tx.provider = 'AFDIAN';
