package com.ghostchu.btn.sparkle.util;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 用户工具类
 * 提供便捷的方法获取当前登录用户信息
 */
@Component
public class UserHolder {

    private static IUserService userService;

    @Autowired
    public void setUserService(IUserService userService) {
        UserHolder.userService = userService;
    }

    /**
     * 获取当前登录的用户 ID
     *
     * @return 用户 ID，如果未登录则返回 null
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SparkleUserDetails userDetails) {
            return userDetails.getUserId();
        }

        return null;
    }

    /**
     * 获取当前登录的用户实体（从数据库实时查询）
     * 每次调用都会从数据库查询最新数据
     *
     * @return User 实体，如果未登录则返回 null
     */
    public static User getCurrentUser() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }

        return userService.getById(userId);
    }

    /**
     * 获取当前登录的用户详情（从 SecurityContext 获取）
     * 这个方法不会查询数据库，返回的是登录时缓存的用户信息
     *
     * @return SparkleUserDetails，如果未登录则返回 null
     */
    public static SparkleUserDetails getCurrentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SparkleUserDetails userDetails) {
            return userDetails;
        }

        return null;
    }

    /**
     * 检查当前是否已登录
     *
     * @return true 如果已登录，否则返回 false
     */
    public static boolean isAuthenticated() {
        return getCurrentUserId() != null;
    }
}
