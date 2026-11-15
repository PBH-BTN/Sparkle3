package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.ui.dto.TrafficStatisticsModels.DailyTraffic;
import com.ghostchu.btn.sparkle.controller.ui.dto.TrafficStatisticsModels.NetworkTrafficAccess;
import com.ghostchu.btn.sparkle.controller.ui.dto.TrafficStatisticsModels.TrafficPage;
import com.ghostchu.btn.sparkle.mapper.UserTrafficStatisticsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserTrafficStatisticsService {
    private static final int NETWORK_WINDOW_DAYS = 30;
    private final UserTrafficStatisticsMapper mapper;

    public TrafficPage daily(long uid, long page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        long total = mapper.count(uid);
        long pages = total / size + (total % size == 0 ? 0 : 1);
        if (page > pages) return new TrafficPage(List.of(),page,size,total,pages);
        var records = mapper.daily(uid,(page-1)*size,size).stream().map(UserTrafficStatisticsService::toDailyTraffic).toList();
        return new TrafficPage(records,page,size,total,pages);
    }

    public List<DailyTraffic> network(NetworkTrafficAccess access) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return mapper.network(today.minusDays(NETWORK_WINDOW_DAYS - 1), today, access.traffic(), access.totalLoss())
                .stream().map(UserTrafficStatisticsService::toDailyTraffic).toList();
    }

    private static DailyTraffic toDailyTraffic(Map<String, String> row) {
        var metrics = new LinkedHashMap<>(row);
        metrics.remove("day");
        return new DailyTraffic(row.get("day"), metrics);
    }
}
