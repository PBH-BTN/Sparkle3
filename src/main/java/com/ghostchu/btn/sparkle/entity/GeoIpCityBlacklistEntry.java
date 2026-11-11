package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;

@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("geoip_city_blacklist_entries")
public class GeoIpCityBlacklistEntry {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("country_code")
    private String countryCode;

    private String province;

    private String city;

    private Boolean enabled;

    @TableField("ipv4_cidr_count")
    private Integer ipv4CidrCount;

    @TableField("ipv6_cidr_count")
    private Integer ipv6CidrCount;

    @TableField("last_generated_at")
    private OffsetDateTime lastGeneratedAt;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    @TableField("created_by")
    private Long createdBy;

    @TableField("updated_by")
    private Long updatedBy;
}
