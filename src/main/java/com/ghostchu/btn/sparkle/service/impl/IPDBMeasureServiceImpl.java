package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.IPDBMeasure;
import com.ghostchu.btn.sparkle.mapper.IPDBMeasureMapper;
import com.ghostchu.btn.sparkle.service.IIPDBMeasureService;
import com.ghostchu.btn.sparkle.util.GlobalpingApiClient;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import inet.ipaddr.IPAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
@Service
@Slf4j
public class IPDBMeasureServiceImpl extends ServiceImpl<IPDBMeasureMapper, IPDBMeasure> implements IIPDBMeasureService {
    @Autowired
    private GlobalpingApiClient globalpingApiClient;
    @Value("${sparkle.measure.expired-after}")
    private long expiredAfter;
    @Value("${sparkle.measure.enable}")
    private boolean enabled;
    @Value("${sparkle.measure.prefix-length.ipv4}")
    private int ipv4PL;
    @Value("${sparkle.measure.prefix-length.ipv6}")
    private int ipv6PL;

    @Scheduled(cron = "${sparkle.measure.globalping-start-measure-cron}")
    public void startPendingMeasures() {
        if (!enabled) return;
        var unstartedMeasures = findUnstartedMeasures(50);
        long ts = 0;
        for (IPDBMeasure unstartedMeasure : unstartedMeasures) {
            try {
                var measurementRequest = new GlobalpingApiClient.MeasurementRequest();
                measurementRequest.setTarget(unstartedMeasure.getIp().getHostAddress());
                measurementRequest.setType("mtr");
                measurementRequest.setLocations(new ArrayList<>() {{
                    add(new LinkedHashMap<>() {{
                        put("country", "CN");
                    }});
                }});
                if (System.currentTimeMillis() - ts < 500) {
                    Thread.sleep(Math.max(0, 500 - (System.currentTimeMillis() - ts)));
                }
                var measurementResponse = globalpingApiClient.createMeasurement(measurementRequest);
                ts = System.currentTimeMillis();
                unstartedMeasure.setMeasureId(measurementResponse.getId());
                save(unstartedMeasure);
            }catch (GlobalpingApiClient.RateLimitExceededException ele){
                log.warn("Rate limit exceeded when starting measurement for IP {}", unstartedMeasure.getIp());
                break;
            } catch (Exception e) {
                log.warn("Unable to start measurement for IP {}", unstartedMeasure.getIp(), e);
            }
        }
    }

    @Scheduled(cron = "${sparkle.measure.globalping-result-retrieve-cron}")
    public void retrieveStartedMeasureResults() {
        if (!enabled) return;
        var unfinishedMeasures = findStartedUnfinishedMeasures(50);
        long ts = 0;
        for (IPDBMeasure unfinishedMeasure : unfinishedMeasures) {
            try {
                if (unfinishedMeasure.getMeasureId() == null) {
                    continue;
                }
                if (System.currentTimeMillis() - ts < 500) {
                    Thread.sleep(Math.max(0, 500 - (System.currentTimeMillis() - ts)));
                }
                var measurement = globalpingApiClient.getMeasurement(unfinishedMeasure.getMeasureId());
                ts = System.currentTimeMillis();
                if ("in-progress".equals(measurement.getStatus()) || "too_many_requests".equals(measurement.getStatus())) {
                    continue;
                }
                unfinishedMeasure.setMeasureAt(OffsetDateTime.now());
                unfinishedMeasure.setMeasureSuccess("finished".equals(measurement.getStatus()));
                if (measurement.getResults() != null && !measurement.getResults().isEmpty()) {
                    unfinishedMeasure.setResult(new MeasurementResultWrapper(measurement.getResults()));
                }
                save(unfinishedMeasure);
            } catch (GlobalpingApiClient.RateLimitExceededException ele){
                log.warn("Rate limit exceeded when retrieve measurement", ele);
                break;
            } catch (Exception e) {
                log.warn("Unable to retrieve measurement result for measure id {}", unfinishedMeasure.getMeasureId(), e);
            }
        }
    }

    @Override
    public boolean scheduleMeasure(@NotNull InetAddress address) {
        if (!enabled) return false;
        var closestMeasure = findClosestMeasure(address.getHostAddress());
        if (closestMeasure == null || isMeasureExpired(closestMeasure)) {
            IPAddress ipAddress = IPAddressUtil.getIPAddress(address.getHostAddress());
            IPAddress cidr = ipAddress;
            if (ipAddress.isIPv4()) {
                cidr = IPAddressUtil.toPrefixBlockAndZeroHost(ipAddress, ipv4PL);
            } else if (ipAddress.isIPv6()) {
                cidr = IPAddressUtil.toPrefixBlockAndZeroHost(ipAddress, ipv6PL);
            }
            return save(new IPDBMeasure()
                    .setIp(address)
                    .setBelongCidr(cidr)
                    .setMeasureId(null)
                    .setMeasureSuccess(null)
                    .setResult(null));
        } else {
            return false;
        }
    }

    public boolean isMeasureExpired(@NotNull IPDBMeasure measure) {
        if (measure.getMeasureAt() == null) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime finishedAt = measure.getMeasureAt();
        long millisBetween = java.time.Duration.between(finishedAt, now).toMillis();
        return millisBetween >= expiredAfter;
    }

    @Override
    public @Nullable IPDBMeasure findClosestSuccessMeasure(@NotNull String address) {
        return baseMapper.findClosestSuccessMeasure(address);
    }

    @Override
    public @NotNull List<IPDBMeasure> findMeasures(@NotNull String address) {
        return baseMapper.findMeasures(address);
    }

    @Override
    public IPDBMeasure findClosestMeasure(@NotNull String hostAddress) {
        return baseMapper.findClosestMeasure(hostAddress);
    }

    public @NotNull List<IPDBMeasure> findUnstartedMeasures(int limit) {
        return baseMapper.selectList(
               new QueryWrapper<IPDBMeasure>()
                        .isNull("measure_id")
                        .last("LIMIT " + limit)
        );
    }

    public @NotNull List<IPDBMeasure> findStartedUnfinishedMeasures(int limit) {
        return baseMapper.findStartedUnfinishedMeasures(limit);
    }

    @AllArgsConstructor
    @Data
    public static class MeasurementResultWrapper {
        private List<GlobalpingApiClient.MeasurementResultItem> root;
    }
}
