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
import java.util.UUID;

@Getter
@Setter
@TableName(value = "pbh_licenses", autoResultMap = true)
@Accessors(chain = true)
public class PbhLicense {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("license_id")
    private UUID licenseId;
    @TableField("btn_user_id")
    private Long btnUserId;
    private String provider;
    @TableField("external_user_id")
    private String externalUserId;
    @TableField("afdian_user_id")
    private String afdianUserId;
    @TableField("source_type")
    private String sourceType;
    @TableField("source_order_no")
    private String sourceOrderNo;
    @TableField("plan_id")
    private String planId;
    @TableField("consumed_amount")
    private BigDecimal consumedAmount;
    @TableField("license_key")
    private String licenseKey;
    @TableField(value = "license_payload", typeHandler = JsonbStringTypeHandler.class)
    private String licensePayload;
    @TableField("issued_at")
    private OffsetDateTime issuedAt;
    @TableField("start_at")
    private OffsetDateTime startAt;
    @TableField("expire_at")
    private OffsetDateTime expireAt;
    @TableField("revoked_at")
    private OffsetDateTime revokedAt;
    @TableField("revoked_reason")
    private String revokedReason;
}
