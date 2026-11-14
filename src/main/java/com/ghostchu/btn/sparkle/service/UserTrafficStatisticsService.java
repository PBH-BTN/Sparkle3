package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.ui.dto.TrafficStatisticsModels.DailyTraffic;
import com.ghostchu.btn.sparkle.controller.ui.dto.TrafficStatisticsModels.TrafficPage;
import com.ghostchu.btn.sparkle.mapper.UserTrafficStatisticsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserTrafficStatisticsService {
    private final UserTrafficStatisticsMapper mapper;

    public TrafficPage daily(long uid, long page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        long total = mapper.count(uid);
        long pages = total / size + (total % size == 0 ? 0 : 1);
        if (page > pages) return new TrafficPage(List.of(),page,size,total,pages);
        var records = mapper.daily(uid,(page-1)*size,size).stream().map(row -> {
            var metrics = new LinkedHashMap<>(row);
            metrics.remove("day");
            return new DailyTraffic(row.get("day"),metrics);
        }).toList();
        return new TrafficPage(records,page,size,total,pages);
    }
}
