package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("currency_exchange_rates")
@Accessors(chain = true)
public class CurrencyExchangeRate {
    @TableId(value = "base_currency", type = IdType.INPUT)
    private String baseCurrency;
    @TableField("quote_currency")
    private String quoteCurrency;
    @TableField("rate_to_base")
    private BigDecimal rateToBase;
    @TableField("fetched_at")
    private OffsetDateTime fetchedAt;
    private String source;
}
