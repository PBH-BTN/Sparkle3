package com.ghostchu.btn.sparkle.service.impl.allowlist;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.IUserappService;
import com.ghostchu.btn.sparkle.service.IUserappsHeartbeatService;
import com.ghostchu.btn.sparkle.service.btnability.IPAllowListRuleProvider;
import com.ghostchu.btn.sparkle.service.impl.AbstractAnalyseRuleServiceImpl;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
public class AnalyseBtnBypassServiceImpl extends AbstractAnalyseRuleServiceImpl implements IPAllowListRuleProvider {

    @Autowired
    @Qualifier("stringStringRedisTemplate")
    protected RedisTemplate<String, String> redisTemplate;
    @Autowired
    private IUserappService userappService;
    @Autowired
    private IUserappsHeartbeatService iUserappsHeartbeatService;
    @Autowired
    private IUserService iUserService;

    @Scheduled(cron = "${sparkle.analyse.btnbypass-userapps.schedule}")
    @Transactional
    public void analyseBtnBypass() {
        log.info("Starting analysis of BTN-Bypassing user applications...");
        OffsetDateTime start = OffsetDateTime.now().minusHours(1);
        OffsetDateTime end = OffsetDateTime.now();
        StringBuilder stringBuilder = new StringBuilder();
        List<Userapp> bypassedUserAppsId = userappService.list(new LambdaQueryWrapper<Userapp>()
                        .eq(Userapp::getBtnBypass, true)
                        .isNull(Userapp::getBannedAt))
                .stream().toList();
        long totalIpsAdded = 0;
        for (Userapp uapp : bypassedUserAppsId) {
            User user = iUserService.getById(uapp.getOwner());
            if(user == null) continue;
            var heartBeats = iUserappsHeartbeatService.fetchHeartBeatsByUserAppIdInTimeRange(uapp.getId(), start, end);
            for (UserappsHeartbeat heartBeat : heartBeats) {
                stringBuilder.append("#[BTN-Bypassing] 用户名称: ").append(user.getNickname()).append(" (UID=").append(user.getId()).append(", Email=").append(user.getEmail()).append("), 应用程序名称: ").append(uapp.getComment()).append(", 同步时间: ").append(heartBeat.getLastSeenAt().toString()).append("\n");
                stringBuilder.append(IPAddressUtil.getIPAddress(heartBeat.getIp().getHostAddress()).toNormalizedString());
                totalIpsAdded++;
            }
        }
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_VALUE.getKey(), stringBuilder.toString());
        redisTemplate.opsForValue().set(RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_VERSION.getKey(), Hashing.crc32c().hashString(stringBuilder.toString(), StandardCharsets.UTF_8).toString());
        log.info("Finished analysis of BTN-Bypassing user applications, total {} ips added.", totalIpsAdded);
    }

    @Override
    public Pair<@Nullable String, @Nullable String> getGeneratedContent() {
        var value = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_VALUE.getKey());
        var version = redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_VERSION.getKey());
        return Pair.of(version, value);
    }

    @Override
    public String getVersion() {
        return redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_VERSION.getKey());
    }

    @Override
    public String getContent() {
        return redisTemplate.opsForValue().get(RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_VALUE.getKey());
    }

}
