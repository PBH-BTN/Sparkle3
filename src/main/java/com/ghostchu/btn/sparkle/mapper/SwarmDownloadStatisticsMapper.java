package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.service.dto.SwarmDownloadCidrRowDto;
import org.apache.ibatis.annotations.Param;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;

public interface SwarmDownloadStatisticsMapper {
    List<SwarmDownloadCidrRowDto> selectCidrDownloads(
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt,
            @Param("ipv4Prefix") int ipv4Prefix,
            @Param("ipv6Prefix") int ipv6Prefix,
            @Param("ipVersion") Integer ipVersion,
            @Param("thresholdBytes") BigInteger thresholdBytes,
            @Param("limit") int limit
    );
}
