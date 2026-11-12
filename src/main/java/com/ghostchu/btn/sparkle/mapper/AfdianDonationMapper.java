package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.AfdianDonation;
import org.apache.ibatis.annotations.Insert;

public interface AfdianDonationMapper extends BaseMapper<AfdianDonation> {
    @Insert("""
            INSERT INTO afdian_donations (
                out_trade_no, afdian_user_id, afdian_user_name,
                plan_id, plan_title, total_amount, show_amount, order_status,
                remark, created_at, synced_at, raw_data
            )
            VALUES (
                #{outTradeNo}, #{afdianUserId}, #{afdianUserName},
                #{planId}, #{planTitle}, #{totalAmount}, #{showAmount},
                #{orderStatus}, #{remark}, #{createdAt}, now(),
                CAST(#{rawData} AS jsonb)
            )
            ON CONFLICT (out_trade_no) DO UPDATE
            SET afdian_user_id = EXCLUDED.afdian_user_id,
                afdian_user_name = EXCLUDED.afdian_user_name,
                plan_id = EXCLUDED.plan_id,
                plan_title = EXCLUDED.plan_title,
                total_amount = EXCLUDED.total_amount,
                show_amount = EXCLUDED.show_amount,
                order_status = EXCLUDED.order_status,
                remark = EXCLUDED.remark,
                created_at = EXCLUDED.created_at,
                synced_at = now(),
                raw_data = EXCLUDED.raw_data
            """)
    int upsert(AfdianDonation donation);
}
