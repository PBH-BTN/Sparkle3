package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.mapper.UserappMapper;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.IUserappService;
import io.sentry.Sentry;
import jakarta.mail.Message;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
public class UserappServiceImpl extends ServiceImpl<UserappMapper, Userapp> implements IUserappService {
    @Qualifier("stringLongRedisTemplate")
    @Autowired
    private RedisTemplate<String, Long> userAppsRedisTemplate;
    @Value("${sparkle.userapp.auto-account.allow-register}")
    private boolean autoAccountAllowRegister;
    @Value("${sparkle.userapp.auto-account.allow-login}")
    private boolean autoAccountAllowLogin;
    @Value("${sparkle.userapp.auto-account.holder-uid}")
    private long autoAccountHolderUid;
    @Autowired
    private JavaMailSender javaMailSender;
    @Autowired
    private IUserService userService;

    @Nullable
    @Transactional
    public Userapp loginViaCredential(@NotNull String appId, @NotNull String appSecret, @Nullable String installationId, @NotNull InetAddress loginIp, @Nullable String userAgent) {
        Userapp userApp;
        if (("example-app-id".equals(appId) || appId.isBlank())
                && ("example-app-secret".equals(appSecret) || appSecret.isBlank())
                && (installationId != null && !installationId.isBlank())) {
            // auto account logic
            Userapp autoAccountUserApp = baseMapper.selectOne(new QueryWrapper<Userapp>().eq("owner", autoAccountHolderUid).eq("installation_id", installationId));
            if (autoAccountUserApp == null) {
                if (!autoAccountAllowRegister) return null;
                Userapp newRegister = new Userapp();
                newRegister.setOwner(autoAccountHolderUid);
                newRegister.setAppId("AUTOACCOUNT-" + UUID.randomUUID());
                newRegister.setAppSecret("AUTOACCOUNT-" + UUID.randomUUID());
                newRegister.setComment("Auto Account for installation ID: " + installationId);
                newRegister.setCreateIp(loginIp);
                newRegister.setCreatedAt(OffsetDateTime.now());
                newRegister.setInstallationId(installationId);
                if (baseMapper.insert(newRegister) <= 0)
                    throw new IllegalStateException("Failed to create userapp for user");
                userApp = newRegister;
                log.info("Auto registered new userapp for installation ID {}", installationId);
            } else {
                if (!autoAccountAllowLogin) return null;
                userApp = autoAccountUserApp;
            }
        } else {
            userApp = baseMapper.selectOne(new QueryWrapper<Userapp>().eq("app_id", appId).eq("app_secret", appSecret));
        }
        if (userApp != null) {
            this.baseMapper.updateUserAppLastThing(userApp.getId(), userAgent);
        }
        // TODO: delete after emerg alert
        if (userAgent != null && userAgent.contains("PeerBanHelper/9.3.0") && userApp != null) {
            var ownerId = userApp.getOwner();
            var user = userService.getById(ownerId);
            if(user != null) {
                Boolean success = userAppsRedisTemplate.opsForValue().setIfAbsent("pbh-930-security-alert-testscope:" + ownerId, System.currentTimeMillis());
                if (success == Boolean.TRUE) {
                    try {
                        var mimeMessage = javaMailSender.createMimeMessage();
                        mimeMessage.setRecipients(Message.RecipientType.TO, "ghostchu@pbh-btn.com"); // testing
                        mimeMessage.setSubject("[SparkleBTN] 您使用的 PeerBanHelper (v9.3.0) 存在安全风险，建议您及时升级");
                        mimeMessage.setText("""
                                您好 %s，
                                  您之所以收到这封邮件，是因为 SparkleBTN 系统已检测到您正在使用存在已知安全问题的 PeerBanHelper 版本 (%s)。
                                  我们已发布紧急安全更新，并修复了相关安全漏洞，请您及时更新！您可以从下面的链接或者 PeerBanHelper 自带的更新程序进行更新：
                                  https://github.com/PBH-BTN/PeerBanHelper/releases/tag/v9.3.1
                                  对您带来的不便，我们致以诚挚歉意。
                                """);
                        javaMailSender.send(mimeMessage);
                    }catch (Exception e){
                        Sentry.captureException(e);
                        log.warn("Failed to send security alert email to user ID {}", ownerId, e);
                    }
                }
            }
        }
        return userApp;
    }

    @Nullable
    public Long getUserAppLastAccess(long userAppId) {
        var userApp = baseMapper.selectOne(new QueryWrapper<Userapp>().eq("id", userAppId));
        if (userApp == null) return null;
        return userApp.getLastSeenAt().toInstant().toEpochMilli();
    }

    @Override
    public @NotNull List<Userapp> getUserAppsByUserId(long userId) {
        return baseMapper.selectList(new QueryWrapper<Userapp>().eq("owner", userId));
    }

    @Override
    public @Nullable Userapp getUserAppByAppId(String appId) {
        return baseMapper.selectOne(new QueryWrapper<Userapp>().eq("app_id", appId));
    }

    @Override
    @Transactional
    public @NotNull Userapp resetUserApplicationSecret(long id) {
        Userapp userapp = baseMapper.selectById(id);
        if (userapp == null)
            throw new IllegalStateException("Userapp for reset application secret not found");
        userapp.setAppSecret(UUID.randomUUID().toString());
        if (baseMapper.updateById(userapp) <= 0)
            throw new IllegalStateException("Failed to reset userapp secret");
        return userapp;
    }

    @Override
    @Transactional
    public boolean deleteUserAppById(long id) {
        return baseMapper.deleteById(id) > 0;
    }


    @Transactional
    @Override
    public @NotNull Userapp createUserAppForUser(Long userId, String comment, InetAddress creatorIp) {
        Userapp userapp = new Userapp();
        userapp.setOwner(userId);
        userapp.setAppId(UUID.randomUUID().toString());
        userapp.setAppSecret(UUID.randomUUID().toString());
        userapp.setComment(comment);
        userapp.setCreateIp(creatorIp);
        userapp.setCreatedAt(OffsetDateTime.now());
        if (baseMapper.insert(userapp) <= 0)
            throw new IllegalStateException("Failed to create userapp for user");
        return userapp;
    }

    @Transactional
    @Override
    public void updateUserAppLastSeen(long id, String userAgent) {
        baseMapper.updateUserAppLastThing(id, userAgent);
    }
}
