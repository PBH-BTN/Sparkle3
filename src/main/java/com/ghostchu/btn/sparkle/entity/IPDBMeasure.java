package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ghostchu.btn.sparkle.converter.IPAddressTypeHandler;
import com.ghostchu.btn.sparkle.converter.InetAddressTypeHandler;
import com.ghostchu.btn.sparkle.service.impl.IPDBMeasureServiceImpl;
import inet.ipaddr.IPAddress;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * <p>
 *
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
@Getter
@Setter
@ToString
@TableName(value = "ipdb_measure", autoResultMap = true)
@Accessors(chain = true)
public class IPDBMeasure implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("ip")
    private InetAddress ip;

    @TableField(value = "belong_cidr", typeHandler = IPAddressTypeHandler.class)
    private IPAddress belongCidr;

    @TableField("measure_id")
    private String measureId;

    @TableField("measure_at")
    private OffsetDateTime measureAt;

    @TableField("measure_success")
    private Boolean measureSuccess;

    @TableField("result")
    private IPDBMeasureServiceImpl.MeasurementResultWrapper result;
}
