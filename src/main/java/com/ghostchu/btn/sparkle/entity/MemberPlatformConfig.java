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
@TableName("member_platform_configs")
@Accessors(chain = true)
public class MemberPlatformConfig {
    @TableId(value = "provider", type = IdType.INPUT)
    private String provider;
    private Boolean enabled;
    @TableField("api_url")
    private String apiUrl;
    @TableField("account_id")
    private String accountId;
    @TableField("access_token")
    private String accessToken;
    @TableField("auth_header")
    private String authHeader;
    @TableField("auth_scheme")
    private String authScheme;
    @TableField("webhook_token")
    private String webhookToken;
    private String currency;
    @TableField("exchange_rate")
    private BigDecimal exchangeRate;
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
    @TableField("updated_by")
    private Long updatedBy;
}
