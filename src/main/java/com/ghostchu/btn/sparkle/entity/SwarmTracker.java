package com.ghostchu.btn.sparkle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@TableName(value = "swarm_tracker", autoResultMap = true)
public class SwarmTracker implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("userapps_id")
    private Long userappsId;

    @TableField("user_downloader")
    private String userDownloader;

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

    @TableField("from_peer_traffic")
    private Long fromPeerTraffic;

    @TableField("to_peer_traffic")
    private Long toPeerTraffic;

    @TableField("from_peer_traffic_offset")
    private Long fromPeerTrafficOffset;

    @TableField("to_peer_traffic_offset")
    private Long toPeerTrafficOffset;

    @TableField("flags")
    private String flags;

    @TableField(value = "first_time_seen")
    private OffsetDateTime firstTimeSeen;

    @TableField(value = "last_time_seen")
    private OffsetDateTime lastTimeSeen;

    @JsonProperty("user_progress")
    private double userProgress;

    @TableField(value = "peer_geoip", typeHandler = JsonbTypeHandler.class)
    private IPGeoData peerGeoip;
}
