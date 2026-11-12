package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ghostchu.btn.sparkle.converter.JsonbStringTypeHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@TableName(value = "member_transactions", autoResultMap = true)
@Accessors(chain = true)
public class MemberTransaction {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String provider;
    @TableField("external_transaction_id")
    private String externalTransactionId;
    @TableField("external_user_id")
    private String externalUserId;
    @TableField("external_user_name")
    private String externalUserName;
    @TableField("transaction_type")
    private String transactionType;
    private String sku;
    @TableField("product_name")
    private String productName;
    private BigDecimal amount;
    private String currency;
    @TableField("amount_cny")
    private BigDecimal amountCny;
    @TableField("amount_base")
    private BigDecimal amountBase;
    @TableField("base_currency")
    private String baseCurrency;
    @TableField("transaction_status")
    private String transactionStatus;
    @TableField("occurred_at")
    private OffsetDateTime occurredAt;
    @TableField(value = "raw_data", typeHandler = JsonbStringTypeHandler.class)
    private String rawData;
    @TableField("synced_at")
    private OffsetDateTime syncedAt;
    @TableField("license_consumed")
    private Boolean licenseConsumed;
    @TableField("license_consumed_at")
    private OffsetDateTime licenseConsumedAt;
    @TableField("remote_present")
    private Boolean remotePresent;
    @TableField("first_seen_at")
    private OffsetDateTime firstSeenAt;
    @TableField("last_reconciled_at")
    private OffsetDateTime lastReconciledAt;
    @TableField("last_sync_run_id")
    private Long lastSyncRunId;
    @TableField("last_changed_at")
    private OffsetDateTime lastChangedAt;
}
