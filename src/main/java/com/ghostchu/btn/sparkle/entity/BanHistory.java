package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ghostchu.btn.sparkle.converter.JsonbTypeHandler;
import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
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
@Accessors(chain = true)
@TableName(value = "ban_history", autoResultMap = true)
public class BanHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "insert_time")
    private OffsetDateTime insertTime;

    @TableField(value = "populate_time")
    private OffsetDateTime populateTime;

    @TableField("userapps_id")
    private Long userappsId;

    @TableField("torrent_id")
    private Long torrentId;

    @TableField("peer_ip")
    private InetAddress peerIp;

    @TableField("peer_port")
    private Integer peerPort;

    @TableField("peer_id")
    private String peerId;

    @TableField("peer_client_name")
    private String peerClientName;

    @TableField("peer_progress")
    private Double peerProgress;

    @TableField("peer_flags")
    private String peerFlags;

    @TableField(value = "peer_geoip", typeHandler = JsonbTypeHandler.class)
    private IPGeoData peerGeoip;

    @TableField("reporter_progress")
    private Double reporterProgress;

    @TableField("to_peer_traffic")
    private Long toPeerTraffic;

    @TableField("from_peer_traffic")
    private Long fromPeerTraffic;

    @TableField("module_name")
    private String moduleName;

    @TableField("rule")
    private String rule;

    @TableField("description")
    private String description;

    @TableField(value = "structured_data", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> structuredData;
}
