package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SupporterRoleMapper {
    Long findBoundUser(@Param("provider") String provider, @Param("externalUserId") String externalUserId);

    List<Long> findCandidates(@Param("after") long after, @Param("limit") int limit);

    boolean needsCnyRates();

    int grantEligible(@Param("userIds") List<Long> userIds, @Param("threshold") BigDecimal threshold);
}
