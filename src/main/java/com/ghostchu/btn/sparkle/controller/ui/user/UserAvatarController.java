package com.ghostchu.btn.sparkle.controller.ui.user;

import com.ghostchu.btn.sparkle.constants.UserPrivacyLevel;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.util.ImageBlurUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Controller
public class UserAvatarController {
    @Autowired
    private IUserService userService;
    @Autowired
    @Qualifier("stringStringRedisTemplate")
    private RedisTemplate<String, String> stringStringRedisTemplate;

    @NotNull
    @GetMapping("/user/avatar/{uid}")
    public ResponseEntity<?> proxyUserAvatar(@AuthenticationPrincipal SparkleUserDetails userDetails,
                                             @PathVariable("uid") Long userId) throws IOException {
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        User user = userService.getById(userId);
        if (userDetails.getUserId().longValue() == userId) {
            return redirectTo(user.getAvatar()); // 自己永远使用原始头像
        }
        UserPrivacyLevel privacyLevel = user.getPrivacyLevel();
        try {
            if (privacyLevel.isAllowOriginalAvatar()) {
                return redirectTo(user.getAvatar());
            } else if (privacyLevel.isAllowBlurredAvatar()) {
                return handleBlurAvatar(user);
            } else {
                return redirectTo("/img/anonymous-user.png");
            }
        }catch (Exception e){
            log.warn("Unable to proxy avatar for userId {}, redirecting to anonymous avatar. Error: {}", userId, e.getMessage());
            return redirectTo("/img/anonymous-user.png");
        }
    }

    private @NotNull ResponseEntity<?> handleBlurAvatar(User user) throws IOException {
        byte[] avatarData;
        String avatarRedisKey = "user:avatar:blur:" + user.getId();
        var redisCachedData = stringStringRedisTemplate.opsForValue().get(avatarRedisKey);
        if (redisCachedData != null) {
            avatarData = redisCachedData.getBytes(StandardCharsets.ISO_8859_1);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(avatarData);
        }
        // HTTP代理头像，隐藏原始地址
        ResponseEntity<byte @NotNull []> response = new RestTemplate().getForEntity(user.getAvatar(), byte[].class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            avatarData = response.getBody();
        } else {
            throw new IOException("Unable to retrieve user avatar for userId " + user.getId() + ". HTTP Status: " + response.getStatusCode());
        }
        try (InputStream is = new ByteArrayInputStream(avatarData)) {
            BufferedImage bufferedImage = ImageIO.read(is);
            BufferedImage blurredImage = ImageBlurUtil.blur(bufferedImage, Math.max(bufferedImage.getHeight(), bufferedImage.getWidth()));
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                ImageIO.write(blurredImage, "jpeg", os);
                avatarData = os.toByteArray();
            }
            stringStringRedisTemplate.opsForValue().set("user:avatar:blur:" + user.getId(), new String(avatarData, StandardCharsets.ISO_8859_1), Duration.ofHours(1));
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(avatarData);
        }
    }

    private @NotNull ResponseEntity<?> redirectTo(@NotNull String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
