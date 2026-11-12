package com.ghostchu.btn.sparkle.controller.ui.user;

import com.ghostchu.btn.sparkle.constants.UserPrivacyLevel;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.BlurredAvatarDiskCache;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.util.ImageBlurUtil;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.WebRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@Slf4j
@Controller
public class UserAvatarController {
    private static final CacheControl AVATAR_CACHE_CONTROL =
            CacheControl.noCache().cachePrivate();
    private static final RestTemplate REST_TEMPLATE = new RestTemplate();
    private static final int GENERATION_LOCK_COUNT = 64;

    private final IUserService userService;
    private final BlurredAvatarDiskCache blurredAvatarDiskCache;
    private final Object[] generationLocks = new Object[GENERATION_LOCK_COUNT];

    public UserAvatarController(
            IUserService userService,
            BlurredAvatarDiskCache blurredAvatarDiskCache
    ) {
        this.userService = userService;
        this.blurredAvatarDiskCache = blurredAvatarDiskCache;
        for (int index = 0; index < generationLocks.length; index++) {
            generationLocks[index] = new Object();
        }
    }

    @NotNull
    @GetMapping("/user/avatar/{uid}")
    public ResponseEntity<?> proxyUserAvatar(@AuthenticationPrincipal SparkleUserDetails userDetails,
                                             @PathVariable("uid") Long userId,
                                             WebRequest webRequest) throws IOException {
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        User user = userService.getById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        if (userDetails.getUserId().longValue() == userId) {
            return redirectTo(user.getAvatar()); // 自己永远使用原始头像
        }
        UserPrivacyLevel privacyLevel = user.getPrivacyLevel();
        try {
            if (privacyLevel.isAllowOriginalAvatar()) {
                return redirectTo(user.getAvatar());
            } else if (privacyLevel.isAllowBlurredAvatar()) {
                return handleBlurAvatar(user, webRequest);
            } else {
                return redirectTo("/img/anonymous-user.png");
            }
        }catch (Exception e){
            log.warn("Unable to proxy avatar for userId {}, redirecting to anonymous avatar. Error: {}", userId, e.getMessage());
            return redirectTo("/img/anonymous-user.png");
        }
    }

    private @NotNull ResponseEntity<?> handleBlurAvatar(User user, WebRequest webRequest)
            throws IOException {
        byte[] avatarData = blurredAvatarDiskCache.get(user.getId(), user.getAvatar());
        if (avatarData != null) {
            return avatarResponse(avatarData, webRequest);
        }

        Object generationLock = generationLocks[
                Math.floorMod(Long.hashCode(user.getId()), generationLocks.length)
        ];
        synchronized (generationLock) {
            avatarData = blurredAvatarDiskCache.get(user.getId(), user.getAvatar());
            if (avatarData == null) {
                avatarData = generateBlurredAvatar(user);
                blurredAvatarDiskCache.put(user.getId(), user.getAvatar(), avatarData);
            }
        }
        return avatarResponse(avatarData, webRequest);
    }

    private byte[] generateBlurredAvatar(User user) throws IOException {
        ResponseEntity<byte @NotNull []> response =
                REST_TEMPLATE.getForEntity(user.getAvatar(), byte[].class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IOException("Unable to retrieve user avatar for userId "
                    + user.getId() + ". HTTP Status: " + response.getStatusCode());
        }
        try (InputStream is = new ByteArrayInputStream(response.getBody())) {
            BufferedImage bufferedImage = ImageIO.read(is);
            if (bufferedImage == null) {
                throw new IOException("Unsupported avatar image for userId " + user.getId());
            }
            BufferedImage blurredImage = ImageBlurUtil.blur(bufferedImage, 20);
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                ImageIO.write(blurredImage, "jpeg", os);
                return os.toByteArray();
            }
        }
    }

    private ResponseEntity<byte[]> avatarResponse(byte[] avatarData, WebRequest webRequest) {
        String etag = "\"" + Hashing.sha256().hashBytes(avatarData) + "\"";
        if (webRequest.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(AVATAR_CACHE_CONTROL)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(AVATAR_CACHE_CONTROL)
                .contentType(MediaType.IMAGE_JPEG)
                .body(avatarData);
    }

    private @NotNull ResponseEntity<?> redirectTo(@NotNull String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
