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
@TableName(value = "afdian_donations", autoResultMap = true)
@Accessors(chain = true)
public class AfdianDonation {
    @TableId(value = "out_trade_no", type = IdType.INPUT)
    private String outTradeNo;
    @TableField("afdian_user_id")
    private String afdianUserId;
    @TableField("afdian_user_name")
    private String afdianUserName;
    @TableField("plan_id")
    private String planId;
    @TableField("plan_title")
    private String planTitle;
    @TableField("total_amount")
    private BigDecimal totalAmount;
    @TableField("show_amount")
    private BigDecimal showAmount;
    @TableField("order_status")
    private Integer orderStatus;
    private String remark;
    @TableField("created_at")
    private OffsetDateTime createdAt;
    @TableField("synced_at")
    private OffsetDateTime syncedAt;
    @TableField("raw_data")
    private String rawData;
    @TableField("license_consumed")
    private Boolean licenseConsumed;
    @TableField("license_consumed_at")
    private OffsetDateTime licenseConsumedAt;
}
