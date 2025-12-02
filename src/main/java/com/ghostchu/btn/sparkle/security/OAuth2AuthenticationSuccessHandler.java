package com.ghostchu.btn.sparkle.security;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.service.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

/**
 * GitHub OAuth2 认证成功处理器
 * 在用户通过 GitHub OAuth 授权后处理用户信息
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final IUserService userService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestClient restClient = RestClient.create();

    @Override
    public void onAuthenticationSuccess(@NotNull HttpServletRequest request,
                                        @NotNull HttpServletResponse response,
                                        @NotNull Authentication authentication) throws IOException {
        try {
            assert authentication.getPrincipal() != null;
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            Integer githubId = oAuth2User.getAttribute("id");
            String login = oAuth2User.getAttribute("login");
            String email = oAuth2User.getAttribute("email");
            String avatarUrl = oAuth2User.getAttribute("avatar_url");
            String name = oAuth2User.getAttribute("name");

            if (githubId == null) throw new IllegalArgumentException("GitHub OAuth 登录失败，无法获取用户 ID");
            if (login == null || login.isBlank())
                throw new IllegalArgumentException("GitHub OAuth 登录失败，无法获取用户名");

            // 如果 email 为 null，尝试从 GitHub API 获取
            if (email == null || email.isBlank()) {
                email = fetchPrimaryEmailFromGitHub(authentication);
                if (email == null || email.isBlank()) {
                    throw new IllegalArgumentException("GitHub OAuth 登录失败，无法获取用户邮箱。请在 GitHub 设置中将邮箱设为公开或授予 user:email 权限");
                }
            }

            if (avatarUrl == null || avatarUrl.isBlank())
                avatarUrl = "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png";
            if (name == null || name.isBlank()) name = login;
            log.info("GitHub OAuth 鉴权成功: login={}, githubId={}, email={}", login, githubId, email);
            User user = userService.userGithubOAuthLogin(authentication, InetAddress.ofLiteral(request.getRemoteAddr()), githubId.longValue(), login, avatarUrl, name, email);
            log.info("GitHub OAuth 登录成功: login={}, githubId={}, email={}", login, githubId, email);

            // 创建 SparkleUserDetails 包装我们的 User 实体
            SparkleUserDetails userDetails = new SparkleUserDetails(user, oAuth2User);

            // 创建新的 Authentication 对象
            UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    authentication.getCredentials(),
                    userDetails.getAuthorities()
            );
            newAuth.setDetails(authentication.getDetails());

            // 更新 SecurityContext
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(newAuth);
            SecurityContextHolder.setContext(context);

            // 将 SecurityContext 保存到 Session
            request.getSession().setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context
            );

            // 重定向到首页
            response.sendRedirect("/");
        } catch (Exception e) {
            log.error("GitHub OAuth 登录处理失败，注销用户会话", e);
            // 清除 SecurityContext
            SecurityContextHolder.clearContext();
            // 使注销处理器清理会话
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            // 使当前会话失效
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            // 重定向到错误页面或登录页面
            // response.sendRedirect("/login?error=oauth_failed");
        }
    }

    /**
     * 从 GitHub API 获取用户的主要邮箱
     * 当用户的邮箱设置为私有时，需要通过此 API 获取
     */
    @SuppressWarnings("unchecked")
    private String fetchPrimaryEmailFromGitHub(Authentication authentication) {
        try {
            // 获取 OAuth2 授权客户端
            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                    "github",
                    authentication.getName()
            );

            if (authorizedClient == null) {
                log.warn("无法获取 GitHub OAuth2 授权客户端");
                return null;
            }

            String accessToken = authorizedClient.getAccessToken().getTokenValue();

            // 调用 GitHub API 获取邮箱列表
            List<Map<String, Object>> emails = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .body(List.class);

            if (emails != null && !emails.isEmpty()) {
                // 查找主要邮箱 (primary) 且已验证 (verified) 的邮箱
                for (Map<String, Object> emailInfo : emails) {
                    Boolean primary = (Boolean) emailInfo.get("primary");
                    Boolean verified = (Boolean) emailInfo.get("verified");
                    if (Boolean.TRUE.equals(primary) && Boolean.TRUE.equals(verified)) {
                        String email = (String) emailInfo.get("email");
                        log.info("从 GitHub API 获取到主要邮箱: {}", email);
                        return email;
                    }
                }

                // 如果没有主要邮箱，返回第一个已验证的邮箱
                for (Map<String, Object> emailInfo : emails) {
                    Boolean verified = (Boolean) emailInfo.get("verified");
                    if (Boolean.TRUE.equals(verified)) {
                        String email = (String) emailInfo.get("email");
                        log.info("从 GitHub API 获取到已验证邮箱: {}", email);
                        return email;
                    }
                }
            }

            log.warn("GitHub API 未返回任何可用邮箱");
            return null;
        } catch (Exception e) {
            log.error("从 GitHub API 获取邮箱失败", e);
            return null;
        }
    }
}
