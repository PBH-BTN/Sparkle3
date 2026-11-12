package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ghostchu.btn.sparkle.entity.CurrencyExchangeRate;
import org.apache.ibatis.annotations.Insert;

public interface CurrencyExchangeRateMapper extends BaseMapper<CurrencyExchangeRate> {
    @Insert("""
            INSERT INTO currency_exchange_rates (
                base_currency, quote_currency, rate_to_base, fetched_at, source
            )
            VALUES (
                #{baseCurrency}, #{quoteCurrency}, #{rateToBase}, #{fetchedAt}, #{source}
            )
            ON CONFLICT (base_currency, quote_currency) DO UPDATE
            SET rate_to_base = EXCLUDED.rate_to_base,
                fetched_at = EXCLUDED.fetched_at,
                source = EXCLUDED.source
            """)
    int upsert(CurrencyExchangeRate rate);
}
