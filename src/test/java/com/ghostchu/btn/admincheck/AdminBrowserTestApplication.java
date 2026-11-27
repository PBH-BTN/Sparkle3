package com.ghostchu.btn.admincheck;

import com.ghostchu.btn.sparkle.SparkleApplication;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.btn.BtnConfigCatalog;
import com.ghostchu.btn.sparkle.util.ipdb.IPDBManager;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Opt-in localhost browser fixture. Not included in production jars. Uses real controllers, SQL and Redis. */
public class AdminBrowserTestApplication {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("SPARKLE_ADMIN_TEST_URL");
        String password = System.getenv("SPARKLE_ADMIN_TEST_PASSWORD");
        if (url == null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/[^/]*test[^/]*") || password == null) {
            throw new IllegalArgumentException("A dedicated localhost test database and password are required");
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (var definition : BtnConfigCatalog.definitions()) defaults.put(definition.key(), definition.defaultValue());
        var required = Pattern.compile("\\$\\{(sparkle\\.[^{}:]+)}");
        try (var sources = Files.walk(Path.of("src/main/java"))) {
            for (var source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                var matcher = required.matcher(Files.readString(source));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    defaults.putIfAbsent(key, key.endsWith("cron") ? "-" : key.endsWith("enabled") || key.endsWith(".enable") ? "false"
                            : key.endsWith("duration") ? "86400000" : key.endsWith("include-modules") ? "test" : "3");
                }
            }
        }
        defaults.put("sparkle.user-contact-email.subject-template", "[Test] {{title}}");
        defaults.put("sparkle.user-contact-email.content-template", "{{content}}");
        defaults.put("sparkle.ping.sync-swarm.cleanup-cron", "-");
        defaults.put("server.address", "127.0.0.1");
        defaults.put("server.port", "17799");
        defaults.put("spring.datasource.url", url);
        defaults.put("spring.datasource.username", "postgres");
        defaults.put("spring.datasource.password", password);
        defaults.put("spring.datasource.dynamic.datasource.master.url", url);
        defaults.put("spring.datasource.dynamic.datasource.master.username", "postgres");
        defaults.put("spring.datasource.dynamic.datasource.master.password", password);
        defaults.put("spring.datasource.dynamic.datasource.master.driver-class-name", "org.postgresql.Driver");
        defaults.put("spring.data.redis.host", "127.0.0.1");
        defaults.put("spring.data.redis.port", "16388");
        defaults.put("spring.security.oauth2.client.registration.github.client-id", "local-admin-test");
        defaults.put("spring.security.oauth2.client.registration.github.client-secret", "local-admin-test");
        defaults.put("spring.web.resources.static-locations", "file:./build/generated/frontend/");
        defaults.put("spring.batch.job.enabled", "false");
        defaults.put("spring.batch.jdbc.initialize-schema", "never");
        defaults.put("spring.main.banner-mode", "off");
        defaults.put("spring.task.scheduling.pool.size", "2");
        var app = new SpringApplication(SparkleApplication.class, LocalIdentity.class);
        app.setDefaultProperties(defaults);
        // Command-line precedence and an empty config location prevent ignored developer/production YAML
        // or machine environment settings from selecting any non-test infrastructure.
        defaults.put("spring.config.location", "optional:classpath:/admin-browser-test-isolated.properties");
        defaults.put("spring.flyway.url", url);
        defaults.put("spring.flyway.user", "postgres");
        defaults.put("spring.flyway.password", password);
        defaults.put("sentry.enabled", "false");
        String[] isolatedArguments = defaults.entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("sparkle."))
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
        app.addInitializers(context -> {
            // Business settings use fixture defaults and the test database, rather than workstation credentials.
            context.getEnvironment().getPropertySources().remove("systemEnvironment");
            context.getEnvironment().getPropertySources().remove("systemProperties");
        });
        var context = app.run(isolatedArguments);
        var jdbc = context.getBean(JdbcTemplate.class);
        String[] roles = {"admin", "moderator", "user"};
        for (int index = 0; index < roles.length; index++) {
            jdbc.update("INSERT INTO \"user\" (id, avatar, email, nickname, register_at, last_login_at, roles) "
                            + "VALUES (?, '', ?, ?, now(), now(), string_to_array(?, ',')) ON CONFLICT (id) DO NOTHING",
                    90001 + index, roles[index] + "@example.invalid", "Test " + roles[index], "user," + roles[index]);
        }
        var database = context.getBean(DatabaseConfigService.class);
        for (var entry : defaults.entrySet()) {
            if (entry.getKey().startsWith("sparkle.")) database.insertIfMissing(entry.getKey(), entry.getValue().toString(), "database");
        }
        Map<String, String> mail = Map.ofEntries(
                Map.entry("enabled", "false"), Map.entry("host", ""), Map.entry("port", "587"),
                Map.entry("username", ""), Map.entry("password", ""), Map.entry("security", "STARTTLS"),
                Map.entry("auth", "true"), Map.entry("connection-timeout-seconds", "10"),
                Map.entry("read-timeout-seconds", "15"), Map.entry("write-timeout-seconds", "15"),
                Map.entry("from-address", ""), Map.entry("from-name", "Test"));
        for (var entry : mail.entrySet()) database.insertIfMissing("sparkle.mail.smtp." + entry.getKey(), entry.getValue(), "database");
        Map<String, String> pop3 = Map.ofEntries(Map.entry("enabled", "false"), Map.entry("host", ""),
                Map.entry("port", "995"), Map.entry("username", ""), Map.entry("password", ""),
                Map.entry("security", "SSL"), Map.entry("reply-address", ""), Map.entry("poll-interval-seconds", "60"),
                Map.entry("connection-timeout-seconds", "15"), Map.entry("max-messages-per-poll", "100"));
        for (var entry : pop3.entrySet()) database.insertIfMissing("sparkle.mail.pop3." + entry.getKey(), entry.getValue(), "database");
        System.out.println("ADMIN_BROWSER_TEST_READY http://127.0.0.1:17799/__admin-test/login?role=admin");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LocalIdentity {
        @Bean
        static InstantiationAwareBeanPostProcessor localTestFilters(ApplicationContext context) {
            return new InstantiationAwareBeanPostProcessor() {
                @Override
                public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
                    // GeoIP downloads are the only constructor network side effect; exclude external data from this fixture.
                    if (beanClass == IPDBManager.class) return org.mockito.Mockito.mock(IPDBManager.class);
                    if (beanClass == com.ghostchu.btn.sparkle.service.DatabaseConfigMigrationService.class) {
                        // Never import ignored workstation YAML (which may contain real platform credentials).
                        var migration = org.mockito.Mockito.mock(com.ghostchu.btn.sparkle.service.DatabaseConfigMigrationService.class);
                        org.mockito.Mockito.when(migration.migrateNow()).thenReturn(
                                new com.ghostchu.btn.sparkle.controller.admin.dto.DatabaseConfigModels.MigrationResult(0, 0, 0, 0, java.time.OffsetDateTime.now()));
                        return migration;
                    }
                    return null;
                }

                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof DefaultSecurityFilterChain chain)) return bean;
                    var filters = new ArrayList<>(chain.getFilters());
                    Filter identity = (request, response, next) -> {
                        var http = (HttpServletRequest) request;
                        var reply = (HttpServletResponse) response;
                        if (http.getRequestURI().equals("/__admin-test/login")) {
                            String role = http.getParameter("role");
                            if (role == null || !java.util.List.of("admin", "moderator", "user", "anonymous").contains(role)) {
                                reply.sendError(400); return;
                            }
                            Cookie cookie = new Cookie("sparkle-admin-test-role", role);
                            cookie.setPath("/"); cookie.setHttpOnly(true); cookie.setAttribute("SameSite", "Lax");
                            reply.addCookie(cookie); reply.sendRedirect("/admin"); return;
                        }
                        String role = "anonymous";
                        if (http.getCookies() != null) for (var cookie : http.getCookies()) {
                            if (cookie.getName().equals("sparkle-admin-test-role")) role = cookie.getValue();
                        }
                        long id = switch (role) { case "admin" -> 90001; case "moderator" -> 90002; case "user" -> 90003; default -> 0; };
                        if (id > 0) {
                            User user = context.getBean(IUserService.class).getById(id);
                            if (user != null) {
                                var principal = new SparkleUserDetails(user, null);
                                SecurityContextHolder.getContext().setAuthentication(
                                        UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
                            }
                        }
                        next.doFilter(request, response);
                    };
                    int authorization = 0;
                    while (authorization < filters.size() && !(filters.get(authorization) instanceof AuthorizationFilter)) authorization++;
                    filters.add(authorization, identity);
                    return new DefaultSecurityFilterChain(chain.getRequestMatcher(), filters);
                }
            };
        }
    }
}
