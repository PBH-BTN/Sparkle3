package com.ghostchu.btn.sparkle.security;

import com.ghostchu.btn.sparkle.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Sparkle 用户详情实现
 * 同时实现 UserDetails 和 OAuth2User 接口
 * 将我们的 User 实体与 Spring Security 集成
 */
@RequiredArgsConstructor
@Getter
public class SparkleUserDetails implements UserDetails, OAuth2User {

    /**
     * Sparkle 用户实体（数据库中的用户记录）
     */
    private final User user;

    /**
     * OAuth2 用户信息（来自 GitHub 等第三方平台）
     */
    private final OAuth2User oauth2User;

    @Override
    public Map<String, Object> getAttributes() {
        return oauth2User != null ? oauth2User.getAttributes() : Map.of();
    }

    @Override
    public @NotNull Collection<? extends GrantedAuthority> getAuthorities() {
        // 可以根据 User 实体的角色或权限来返回权限列表
        // 目前返回基本的 USER 角色
        return List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("user"),
                new SimpleGrantedAuthority(user.getRole())
        );
    }

    @Override
    public String getPassword() {
        // OAuth2 登录不需要密码
        return null;
    }

    @Override
    public String getUsername() {
        // 使用用户 ID 作为用户名
        return String.valueOf(user.getId());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // 检查用户是否被封禁
        return user.getBannedAt() == null;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // 检查用户是否被封禁
        return user.getBannedAt() == null;
    }

    @Override
    public String getName() {
        // OAuth2User 接口要求的方法，返回用户的显示名称
        return user.getNickname();
    }

    /**
     * 获取用户 ID
     */
    public Long getUserId() {
        return user.getId();
    }
}
