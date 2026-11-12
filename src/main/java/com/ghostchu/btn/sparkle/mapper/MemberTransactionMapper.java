package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.MemberTransaction;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface MemberTransactionMapper extends BaseMapper<MemberTransaction> {
    @Insert("""
            INSERT INTO member_transactions (
                provider, external_transaction_id, external_user_id,
                external_user_name, transaction_type, sku, product_name,
                amount, currency, amount_cny, amount_base, base_currency,
                transaction_status, occurred_at, raw_data, synced_at,
                license_consumed, remote_present, first_seen_at,
                last_reconciled_at, last_sync_run_id, last_changed_at
            )
            VALUES (
                #{provider}, #{externalTransactionId}, #{externalUserId},
                #{externalUserName}, #{transactionType}, #{sku}, #{productName},
                #{amount}, #{currency}, #{amountCny}, #{amountBase}, #{baseCurrency},
                #{transactionStatus}, #{occurredAt}, CAST(#{rawData} AS jsonb),
                now(), COALESCE(#{licenseConsumed}, FALSE),
                COALESCE(#{remotePresent}, TRUE), COALESCE(#{firstSeenAt}, now()),
                COALESCE(#{lastReconciledAt}, now()), #{lastSyncRunId},
                #{lastChangedAt}
            )
            ON CONFLICT (provider, external_transaction_id) DO UPDATE
            SET external_user_id = EXCLUDED.external_user_id,
                external_user_name = EXCLUDED.external_user_name,
                transaction_type = EXCLUDED.transaction_type,
                sku = EXCLUDED.sku,
                product_name = EXCLUDED.product_name,
                amount = EXCLUDED.amount,
                currency = EXCLUDED.currency,
                amount_cny = EXCLUDED.amount_cny,
                amount_base = EXCLUDED.amount_base,
                base_currency = EXCLUDED.base_currency,
                transaction_status = EXCLUDED.transaction_status,
                occurred_at = EXCLUDED.occurred_at,
                raw_data = EXCLUDED.raw_data,
                synced_at = now(),
                remote_present = COALESCE(EXCLUDED.remote_present, TRUE),
                first_seen_at = COALESCE(
                    member_transactions.first_seen_at,
                    EXCLUDED.first_seen_at,
                    now()
                ),
                last_reconciled_at = COALESCE(
                    EXCLUDED.last_reconciled_at,
                    now()
                ),
                last_sync_run_id = COALESCE(
                    EXCLUDED.last_sync_run_id,
                    member_transactions.last_sync_run_id
                ),
                last_changed_at = COALESCE(
                    EXCLUDED.last_changed_at,
                    member_transactions.last_changed_at
                )
            """)
    int upsert(MemberTransaction transaction);

    @Select("""
            SELECT COUNT(*)
            FROM member_transactions t
            JOIN member_platform_bindings b
              ON b.provider = t.provider
             AND b.external_user_id = t.external_user_id
            WHERE b.btn_user_id = #{userId}
              AND t.transaction_type = 'PRODUCT'
              AND UPPER(t.transaction_status) IN
                  ('2', 'PAID', 'COMPLETED', 'SUCCEEDED', 'SUCCESS', 'ACTIVE')
              AND t.remote_present = TRUE
              AND t.license_consumed = FALSE
            """)
    int countAvailableProductSlots(@Param("userId") long userId);

    @Select("""
            SELECT COALESCE(SUM(t.amount_base), 0)
            FROM member_transactions t
            JOIN member_platform_bindings b
              ON b.provider = t.provider
             AND b.external_user_id = t.external_user_id
            WHERE b.btn_user_id = #{userId}
              AND t.transaction_type = 'DONATION'
              AND UPPER(t.transaction_status) IN
                  ('2', 'PAID', 'COMPLETED', 'SUCCEEDED', 'SUCCESS', 'ACTIVE')
              AND t.remote_present = TRUE
            """)
    BigDecimal sumCompletedDonationBase(@Param("userId") long userId);

    @Select("""
            SELECT t.*
            FROM member_transactions t
            JOIN member_platform_bindings b
              ON b.provider = t.provider
             AND b.external_user_id = t.external_user_id
            WHERE b.btn_user_id = #{userId}
              AND t.transaction_type = 'PRODUCT'
              AND UPPER(t.transaction_status) IN
                  ('2', 'PAID', 'COMPLETED', 'SUCCEEDED', 'SUCCESS', 'ACTIVE')
              AND t.remote_present = TRUE
              AND t.license_consumed = FALSE
            ORDER BY t.occurred_at NULLS LAST, t.id
            LIMIT 1
            FOR UPDATE OF t
            """)
    MemberTransaction lockNextAvailableProduct(@Param("userId") long userId);

    @Update("""
            UPDATE member_transactions
            SET license_consumed = TRUE, license_consumed_at = now()
            WHERE id = #{id} AND license_consumed = FALSE
            """)
    int consumeLicenseSlot(@Param("id") long id);
}
