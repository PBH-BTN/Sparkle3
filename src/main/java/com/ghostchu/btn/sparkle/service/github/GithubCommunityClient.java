package com.ghostchu.btn.sparkle.service.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GithubCommunityClient {
    private final GithubCommunitySettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<ResolvedTeam> resolveTeams(
            String organization,
            List<TeamSpec> teams
    ) throws BusinessException {
        if (teams == null || teams.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "A GitHub community group requires at least one team");
        }
        List<ResolvedTeam> resolved = new ArrayList<>(teams.size());
        for (TeamSpec team : teams) {
            JsonNode body = request(
                    "GET",
                    "/orgs/" + segment(organization) + "/teams/" + segment(team.slug()),
                    null,
                    false
            );
            resolved.add(new ResolvedTeam(
                    body.path("id").asLong(),
                    body.path("slug").asText(team.slug()),
                    body.path("name").asText(team.name())
            ));
        }
        return List.copyOf(resolved);
    }

    public MembershipSnapshot membership(
            String organization,
            List<TeamSpec> teams,
            String githubLogin
    ) throws BusinessException {
        List<ResolvedTeam> resolvedTeams = resolveTeams(organization, teams);
        String organizationState = state(
                "/orgs/" + segment(organization) + "/memberships/"
                        + segment(githubLogin)
        );
        List<TeamMembership> memberships = new ArrayList<>(resolvedTeams.size());
        for (ResolvedTeam team : resolvedTeams) {
            memberships.add(new TeamMembership(
                    team.slug(),
                    team.name(),
                    state(
                            "/orgs/" + segment(organization) + "/teams/"
                                    + segment(team.slug()) + "/memberships/"
                                    + segment(githubLogin)
                    )
            ));
        }
        return new MembershipSnapshot(organizationState, List.copyOf(memberships));
    }

    public InvitationResult invite(
            String organization,
            List<ResolvedTeam> teams,
            long githubUserId,
            String githubLogin,
            String organizationState
    ) throws BusinessException {
        if ("active".equals(organizationState)) {
            for (ResolvedTeam team : teams) {
                request(
                        "PUT",
                        "/orgs/" + segment(organization) + "/teams/"
                                + segment(team.slug()) + "/memberships/"
                                + segment(githubLogin),
                        Map.of("role", "member"),
                        false
                );
            }
            return new InvitationResult(
                    "ACTIVE",
                    "GitHub organization membership was active, so the configured teams were updated"
            );
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invitee_id", githubUserId);
        payload.put("role", "direct_member");
        payload.put("team_ids", teams.stream().map(ResolvedTeam::id).toList());
        JsonNode response = request(
                "POST",
                "/orgs/" + segment(organization) + "/invitations",
                payload,
                false
        );
        return new InvitationResult(
                "PENDING",
                response.path("message").asText("GitHub organization invitation sent")
        );
    }

    private String state(String path) throws BusinessException {
        try {
            JsonNode body = request("GET", path, null, true);
            if (body == null) {
                return "none";
            }
            String state = body.path("state").asText("none");
            return state.isBlank() ? "none" : state;
        } catch (GithubNotFound ignored) {
            return "none";
        }
    }

    private JsonNode request(
            String method,
            String path,
            Object payload,
            boolean allowNotFound
    ) throws BusinessException {
        String token = settingsService.accessToken();
        if (token.isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GitHub community access token is not configured");
        }
        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(URI.create(settingsService.apiBaseUrl() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "SparkleBTN-CommunityGroups");
            if (payload != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(
                                objectMapper.writeValueAsString(payload),
                                StandardCharsets.UTF_8
                        ));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Unable to build GitHub API request: " + exception.getMessage());
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Unable to connect to GitHub: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "GitHub API request was interrupted");
        }

        if (response.statusCode() == 404 && allowNotFound) {
            throw new GithubNotFound();
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            if (response.body() == null || response.body().isBlank()) {
                return objectMapper.createObjectNode();
            }
            try {
                return objectMapper.readTree(response.body());
            } catch (Exception exception) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY,
                        "GitHub returned an invalid JSON response");
            }
        }

        String message = githubErrorMessage(response.body());
        HttpStatus status = switch (response.statusCode()) {
            case 401 -> HttpStatus.BAD_GATEWAY;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409, 422 -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        throw new BusinessException(status,
                "GitHub API request failed (" + response.statusCode() + "): " + message);
    }

    private String githubErrorMessage(String body) {
        if (body != null && !body.isBlank()) {
            try {
                String message = objectMapper.readTree(body).path("message").asText();
                if (!message.isBlank()) {
                    return message;
                }
            } catch (Exception ignored) {
                // Fall through to a bounded raw response.
            }
            return body.length() <= 500 ? body : body.substring(0, 500);
        }
        return "Unknown GitHub API error";
    }

    private String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    public record TeamSpec(
            String slug,
            String name
    ) {
    }

    public record ResolvedTeam(
            long id,
            String slug,
            String name
    ) {
    }

    public record TeamMembership(
            String slug,
            String name,
            String state
    ) {
    }

    public record MembershipSnapshot(
            String organizationState,
            List<TeamMembership> teams
    ) {
    }

    public record InvitationResult(
            String state,
            String message
    ) {
    }

    private static final class GithubNotFound extends BusinessException {
        private GithubNotFound() {
            super(HttpStatus.NOT_FOUND, "GitHub resource not found");
        }
    }
}
