package com.ghostchu.btn.sparkle.service.impl.allowlist;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.RedisKeyConstant;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.IUserappService;
import com.ghostchu.btn.sparkle.service.IUserappsHeartbeatService;
import com.ghostchu.btn.sparkle.service.AnalyseModuleConfigService;
import com.ghostchu.btn.sparkle.service.btnability.IPAllowListRuleProvider;
import com.ghostchu.btn.sparkle.service.impl.AbstractAnalyseRuleServiceImpl;
import com.ghostchu.btn.sparkle.util.IPAddressUtil;
import com.ghostchu.btn.sparkle.util.SuspensionStatus;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    @Autowired
    private AnalyseModuleConfigService moduleConfigService;
    @Autowired
    private ObjectMapper objectMapper;

    public void analyseBtnBypass() {
        if (!moduleConfigService.isEnabled("btnbypass-userapps")) {
            return;
        }
        log.info("Starting analysis of BTN-Bypassing user applications...");
        OffsetDateTime start = OffsetDateTime.now().minusHours(1);
        OffsetDateTime end = OffsetDateTime.now();
        StringBuilder stringBuilder = new StringBuilder();
        Map<String, Set<Long>> applicationsByIp = new LinkedHashMap<>();
        List<Userapp> bypassedUserAppsId = userappService.list(new LambdaQueryWrapper<Userapp>()
                        .eq(Userapp::getBtnBypass, true)
                        .apply(SuspensionStatus.NOT_SUSPENDED_SQL))
                .stream().toList();
        long totalIpsAdded = 0;
        for (Userapp uapp : bypassedUserAppsId) {
            User user = iUserService.getById(uapp.getOwner());
            if(user == null) continue;
            var heartBeats = iUserappsHeartbeatService.fetchHeartBeatsByUserAppIdInTimeRange(uapp.getId(), start, end);
            for (UserappsHeartbeat heartBeat : heartBeats) {
                String address = IPAddressUtil.getIPAddress(heartBeat.getIp().getHostAddress()).toCompressedString();
                stringBuilder.append("#[BTN-允许列表] 用户名称: ").append(user.getNickname()).append(" (UID=").append(user.getId()).append("), 应用程序名称: ").append(uapp.getComment()).append(", 同步时间: ").append(heartBeat.getLastSeenAt().toString()).append("; 如需更多信息，请参见：https://bbs.pbh-btn.com/d/58").append("\n");
                stringBuilder.append(address).append("\n");
                applicationsByIp.computeIfAbsent(address, ignored -> new LinkedHashSet<>()).add(uapp.getId());
                totalIpsAdded++;
            }
        }
        String content = stringBuilder.toString();
        try {
            redisTemplate.opsForValue().multiSet(Map.of(
                    RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_VALUE.getKey(), content,
                    RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_VERSION.getKey(),
                    Hashing.crc32c().hashString(content, StandardCharsets.UTF_8).toString(),
                    RedisKeyConstant.ANALYSE_BTN_BYPASS_USERAPPS_OWNERS.getKey(),
                    objectMapper.writeValueAsString(applicationsByIp)
            ));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode BTN Bypass application associations");
        }
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
