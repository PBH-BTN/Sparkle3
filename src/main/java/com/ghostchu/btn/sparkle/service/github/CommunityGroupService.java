package com.ghostchu.btn.sparkle.service.github;

import com.ghostchu.btn.sparkle.constants.CommunityGroupType;
import com.ghostchu.btn.sparkle.controller.admin.dto.CommunityGroupModels.GroupInput;
import com.ghostchu.btn.sparkle.controller.admin.dto.CommunityGroupModels.GroupView;
import com.ghostchu.btn.sparkle.controller.admin.dto.CommunityGroupModels.GithubTeamInput;
import com.ghostchu.btn.sparkle.controller.admin.dto.CommunityGroupModels.GithubTeamView;
import com.ghostchu.btn.sparkle.controller.admin.dto.CommunityGroupModels.SettingsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.CommunityGroupModels.TeamValidationResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.CommunityGroupModels.TeamValidationView;
import com.ghostchu.btn.sparkle.controller.ui.dto.CommunityGroupPublicModels.GithubJoinResult;
import com.ghostchu.btn.sparkle.controller.ui.dto.CommunityGroupPublicModels.GithubStatusView;
import com.ghostchu.btn.sparkle.controller.ui.dto.CommunityGroupPublicModels.TeamStatusView;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.entity.UserRel;
import com.ghostchu.btn.sparkle.entity.CommunityGroup;
import com.ghostchu.btn.sparkle.entity.CommunityGroupTeam;
import com.ghostchu.btn.sparkle.entity.CommunityGroupInviteEvent;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.CommunityGroupMapper;
import com.ghostchu.btn.sparkle.mapper.CommunityGroupTeamMapper;
import com.ghostchu.btn.sparkle.mapper.CommunityGroupInviteEventMapper;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.github.GithubCommunityClient.MembershipSnapshot;
import com.ghostchu.btn.sparkle.service.github.GithubCommunityClient.TeamMembership;
import com.ghostchu.btn.sparkle.service.github.GithubCommunityClient.TeamSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityGroupService {
    private static final Pattern SLUG =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    private static final Pattern GITHUB_PART =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}");

    private final CommunityGroupMapper groupMapper;
    private final CommunityGroupTeamMapper teamMapper;
    private final CommunityGroupInviteEventMapper inviteEventMapper;
    private final IUserService userService;
    private final GithubCommunitySettingsService settingsService;
    private final GithubCommunityClient githubClient;

    public SettingsView settings() {
        return new SettingsView(
                settingsService.tokenConfigured(),
                settingsService.apiBaseUrl(),
                listAdminGroups()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSettings(
            @Nullable String personalAccessToken,
            boolean clearToken,
            String apiBaseUrl,
            @Nullable Long operatorId
    ) throws BusinessException {
        settingsService.update(personalAccessToken, clearToken, apiBaseUrl, operatorId);
    }

    public List<GroupView> listAdminGroups() {
        List<GroupRow> rows = groupMapper.selectList(
                new QueryWrapper<CommunityGroup>()
                        .orderByAsc("sort_order")
                        .orderByAsc("id")
        ).stream().map(GroupRow::from).toList();
        Map<Long, List<GithubTeamView>> teams = loadTeams();
        return rows.stream()
                .map(row -> row.toView(teams.getOrDefault(row.id(), List.of())))
                .toList();
    }

    public boolean hasEnabledGroups() {
        return groupMapper.selectCount(
                new QueryWrapper<CommunityGroup>().eq("enabled", true)
        ) > 0;
    }

    public List<com.ghostchu.btn.sparkle.controller.ui.dto.CommunityGroupPublicModels.GroupView>
    listEnabled(long userId) {
        UserRel relation = githubRelation(userId);
        boolean configured = settingsService.tokenConfigured();
        List<GroupRow> rows = groupMapper.selectList(
                new QueryWrapper<CommunityGroup>()
                        .eq("enabled", true)
                        .orderByAsc("sort_order")
                        .orderByAsc("id")
        ).stream().map(GroupRow::from).toList();
        Map<Long, List<GithubTeamView>> teams = loadTeams();
        return rows.stream().map(row -> {
            List<com.ghostchu.btn.sparkle.controller.ui.dto.CommunityGroupPublicModels.TeamView>
                    publicTeams = teams.getOrDefault(row.id(), List.of()).stream()
                    .map(team -> new com.ghostchu.btn.sparkle.controller.ui.dto
                            .CommunityGroupPublicModels.TeamView(
                            team.slug(),
                            team.name()
                    ))
                    .toList();
            return new com.ghostchu.btn.sparkle.controller.ui.dto
                    .CommunityGroupPublicModels.GroupView(
                    row.id(),
                    row.slug(),
                    row.type().name(),
                    row.titleZh(),
                    row.titleEn(),
                    row.descriptionZh(),
                    row.descriptionEn(),
                    row.publicUrl(),
                    row.githubOrganization(),
                    publicTeams,
                    relation != null,
                    relation == null ? null : relation.getPlatformUserLogin(),
                    configured
            );
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupView create(GroupInput input, @Nullable Long operatorId)
            throws BusinessException {
        NormalizedGroup group = normalize(input);
        try {
            CommunityGroup entity = new CommunityGroup()
                    .setSlug(group.slug())
                    .setGroupType(group.type().name())
                    .setTitleZh(group.titleZh())
                    .setTitleEn(group.titleEn())
                    .setDescriptionZh(group.descriptionZh())
                    .setDescriptionEn(group.descriptionEn())
                    .setUrl(group.url())
                    .setGithubOrganization(group.githubOrganization())
                    .setEnabled(group.enabled())
                    .setSortOrder(group.sortOrder())
                    .setUpdatedBy(operatorId);
            groupMapper.insert(entity);
            if (entity.getId() == null) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unable to create community group");
            }
            replaceTeams(entity.getId(), group.teams());
            return requireAdmin(entity.getId());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "A community group with this slug already exists");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public GroupView update(long id, GroupInput input, @Nullable Long operatorId)
            throws BusinessException {
        requireRow(id);
        NormalizedGroup group = normalize(input);
        try {
            CommunityGroup entity = requireEntity(id)
                    .setSlug(group.slug())
                    .setGroupType(group.type().name())
                    .setTitleZh(group.titleZh())
                    .setTitleEn(group.titleEn())
                    .setDescriptionZh(group.descriptionZh())
                    .setDescriptionEn(group.descriptionEn())
                    .setUrl(group.url())
                    .setGithubOrganization(group.githubOrganization())
                    .setEnabled(group.enabled())
                    .setSortOrder(group.sortOrder())
                    .setUpdatedBy(operatorId);
            if (groupMapper.updateById(entity) == 0) {
                throw new BusinessException(HttpStatus.NOT_FOUND,
                        "Community group not found");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "A community group with this slug already exists");
        }
        replaceTeams(id, group.teams());
        return requireAdmin(id);
    }

    public void delete(long id) throws BusinessException {
        if (groupMapper.deleteById(id) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Community group not found");
        }
    }

    public TeamValidationResult validateTeams(
            String organization,
            List<String> teamSlugs
    ) throws BusinessException {
        String normalizedOrg = githubPart(organization, "GitHub organization");
        List<TeamSpec> specs = normalizeTeamSlugs(teamSlugs);
        List<GithubCommunityClient.ResolvedTeam> resolved =
                githubClient.resolveTeams(normalizedOrg, specs);
        return new TeamValidationResult(
                normalizedOrg,
                resolved.stream()
                        .map(team -> new TeamValidationView(
                                team.id(),
                                team.slug(),
                                team.name()
                        ))
                        .toList()
        );
    }

    public GithubStatusView status(long userId, long groupId) {
        GroupRow group;
        try {
            group = requireGithubGroup(groupId);
        } catch (BusinessException exception) {
            return new GithubStatusView(
                    groupId,
                    "ERROR",
                    "none",
                    List.of(),
                    exception.getMessage(),
                    OffsetDateTime.now()
            );
        }
        UserRel relation = githubRelation(userId);
        if (relation == null) {
            return statusView(groupId, "NOT_LINKED", "none", List.of(),
                    "Link a GitHub account before joining this group");
        }
        if (!settingsService.tokenConfigured()) {
            return statusView(groupId, "UNAVAILABLE", "none", List.of(),
                    "GitHub community integration is not configured");
        }

        List<GithubTeamView> teams = loadTeams().getOrDefault(groupId, List.of());
        try {
            MembershipSnapshot snapshot = githubClient.membership(
                    group.githubOrganization(),
                    toTeamSpecs(teams),
                    relation.getPlatformUserLogin()
            );
            String status = aggregateStatus(
                    snapshot.organizationState(),
                    snapshot.teams()
            );
            List<TeamStatusView> teamStatuses = snapshot.teams().stream()
                    .map(team -> new TeamStatusView(
                            team.slug(),
                            team.name(),
                            normalizeState(team.state())
                    ))
                    .toList();
            return statusView(
                    groupId,
                    status,
                    normalizeState(snapshot.organizationState()),
                    teamStatuses,
                    null
            );
        } catch (BusinessException exception) {
            return statusView(
                    groupId,
                    "ERROR",
                    "none",
                    List.of(),
                    exception.getMessage()
            );
        }
    }

    public GithubJoinResult join(long userId, long groupId) throws BusinessException {
        GroupRow group = requireGithubGroup(groupId);
        User user = userService.getById(userId);
        if (user == null || user.getBannedAt() != null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Account is unavailable");
        }
        UserRel relation = githubRelation(userId);
        if (relation == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Link a GitHub account before joining this group");
        }
        if (!settingsService.tokenConfigured()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GitHub community integration is not configured");
        }
        long githubUserId;
        try {
            githubUserId = Long.parseLong(relation.getPlatformUserId());
        } catch (NumberFormatException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "The linked GitHub account ID is invalid");
        }

        List<GithubTeamView> teams = loadTeams().getOrDefault(groupId, List.of());
        MembershipSnapshot before = githubClient.membership(
                group.githubOrganization(),
                toTeamSpecs(teams),
                relation.getPlatformUserLogin()
        );
        String currentStatus = aggregateStatus(before.organizationState(), before.teams());
        if ("ACTIVE".equals(currentStatus)) {
            recordInvite(userId, groupId, relation.getPlatformUserLogin(),
                    "ALREADY_MEMBER", null);
            return new GithubJoinResult("ACTIVE", "You already belong to every configured team");
        }
        if ("PENDING".equals(currentStatus)) {
            recordInvite(userId, groupId, relation.getPlatformUserLogin(),
                    "ALREADY_PENDING", null);
            return new GithubJoinResult(
                    "PENDING",
                    "A GitHub organization invitation is already pending"
            );
        }

        List<GithubCommunityClient.ResolvedTeam> resolved =
                githubClient.resolveTeams(group.githubOrganization(), toTeamSpecs(teams));
        GithubCommunityClient.InvitationResult invitation = githubClient.invite(
                group.githubOrganization(),
                resolved,
                githubUserId,
                relation.getPlatformUserLogin(),
                before.organizationState()
        );
        recordInvite(userId, groupId, relation.getPlatformUserLogin(),
                invitation.state(), invitation.message());
        return new GithubJoinResult(invitation.state(), invitation.message());
    }

    public static String aggregateStatus(
            String organizationState,
            List<TeamMembership> teams
    ) {
        String org = normalizeState(organizationState);
        if (!teams.isEmpty() && teams.stream()
                .allMatch(team -> "ACTIVE".equals(normalizeState(team.state())))) {
            return "ACTIVE";
        }
        if ("PENDING".equals(org) || teams.stream()
                .anyMatch(team -> "PENDING".equals(normalizeState(team.state())))) {
            return "PENDING";
        }
        boolean anyTeamActive = teams.stream()
                .anyMatch(team -> "ACTIVE".equals(normalizeState(team.state())));
        if (anyTeamActive || "ACTIVE".equals(org)) {
            return "PARTIAL";
        }
        return "NOT_MEMBER";
    }

    private GroupView requireAdmin(long id) throws BusinessException {
        GroupRow row = requireRow(id);
        return row.toView(loadTeams().getOrDefault(id, List.of()));
    }

    private GroupRow requireRow(long id) throws BusinessException {
        return GroupRow.from(requireEntity(id));
    }

    private GroupRow requireGithubGroup(long id) throws BusinessException {
        GroupRow group = requireRow(id);
        if (!group.enabled()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Community group not found");
        }
        if (group.type() != CommunityGroupType.GITHUB) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "This community group is not a GitHub group");
        }
        return group;
    }

    private CommunityGroup requireEntity(long id) throws BusinessException {
        CommunityGroup entity = groupMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Community group not found");
        }
        return entity;
    }

    private Map<Long, List<GithubTeamView>> loadTeams() {
        Map<Long, List<GithubTeamView>> teams = new LinkedHashMap<>();
        teamMapper.selectList(
                new QueryWrapper<CommunityGroupTeam>()
                        .orderByAsc("group_id")
                        .orderByAsc("sort_order")
                        .orderByAsc("id")
        ).forEach(team -> {
            teams.computeIfAbsent(team.getGroupId(), ignored -> new ArrayList<>())
                    .add(new GithubTeamView(
                            team.getTeamSlug(),
                            team.getTeamName()
                    ));
        });
        return teams;
    }

    private void replaceTeams(long groupId, List<GithubTeamView> teams) {
        teamMapper.delete(
                new QueryWrapper<CommunityGroupTeam>().eq("group_id", groupId)
        );
        if (teams.isEmpty()) {
            return;
        }
        for (int index = 0; index < teams.size(); index++) {
            GithubTeamView team = teams.get(index);
            teamMapper.insert(new CommunityGroupTeam()
                    .setGroupId(groupId)
                    .setTeamSlug(team.slug())
                    .setTeamName(team.name())
                    .setSortOrder(index));
        }
    }

    private NormalizedGroup normalize(GroupInput input) throws BusinessException {
        if (input == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Community group is required");
        }
        String slug = required(input.slug(), "Group slug", 128);
        if (!SLUG.matcher(slug).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Group slug may contain only letters, numbers, dots, underscores, and hyphens");
        }
        CommunityGroupType type = CommunityGroupType.parse(input.type())
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,
                        "Unsupported community group type"));
        String titleZh = optional(input.titleZh(), 255);
        String titleEn = optional(input.titleEn(), 255);
        if (titleZh.isBlank() && titleEn.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "At least one group title is required");
        }
        String descriptionZh = optional(input.descriptionZh(), 5_000);
        String descriptionEn = optional(input.descriptionEn(), 5_000);
        String url = nullableUrl(input.url());
        String organization = null;
        List<GithubTeamView> teams = List.of();
        if (type == CommunityGroupType.GITHUB) {
            organization = githubPart(input.githubOrganization(), "GitHub organization");
            teams = normalizeTeams(input.teams());
            if (teams.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "A GitHub community group requires at least one team");
            }
        } else {
            if (url == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "A URL is required for a link community group");
            }
        }
        if (input.sortOrder() < -10_000 || input.sortOrder() > 10_000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Community group sort order is invalid");
        }
        return new NormalizedGroup(
                slug.toLowerCase(Locale.ROOT),
                type,
                titleZh,
                titleEn,
                descriptionZh,
                descriptionEn,
                url,
                organization,
                input.enabled(),
                input.sortOrder(),
                teams
        );
    }

    private List<GithubTeamView> normalizeTeams(@Nullable List<GithubTeamInput> inputs)
            throws BusinessException {
        if (inputs == null) {
            return List.of();
        }
        Map<String, GithubTeamView> teams = new LinkedHashMap<>();
        for (GithubTeamInput input : inputs) {
            if (input == null) continue;
            String slug = required(input.slug(), "GitHub team slug", 100);
            if (!GITHUB_PART.matcher(slug).matches()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "GitHub team slug contains unsupported characters");
            }
            String name = optional(input.name(), 255);
            if (name.isBlank()) {
                name = slug;
            }
            teams.putIfAbsent(
                    slug.toLowerCase(Locale.ROOT),
                    new GithubTeamView(slug.toLowerCase(Locale.ROOT), name)
            );
        }
        return List.copyOf(teams.values());
    }

    private List<TeamSpec> normalizeTeamSlugs(@Nullable List<String> slugs)
            throws BusinessException {
        if (slugs == null || slugs.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "At least one GitHub team slug is required");
        }
        List<TeamSpec> teams = new ArrayList<>();
        for (String value : slugs.stream().filter(Objects::nonNull).distinct().toList()) {
            String slug = required(value, "GitHub team slug", 100);
            if (!GITHUB_PART.matcher(slug).matches()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "GitHub team slug contains unsupported characters");
            }
            teams.add(new TeamSpec(slug.toLowerCase(Locale.ROOT), slug));
        }
        return List.copyOf(teams);
    }

    private List<TeamSpec> toTeamSpecs(List<GithubTeamView> teams) {
        return teams.stream()
                .map(team -> new TeamSpec(team.slug(), team.name()))
                .toList();
    }

    @Nullable
    private UserRel githubRelation(long userId) {
        UserRel relation = userService.getUserRelByBindUserId(userId);
        if (relation == null
                || !"github".equalsIgnoreCase(relation.getPlatform())
                || relation.getPlatformUserLogin() == null
                || relation.getPlatformUserLogin().isBlank()) {
            return null;
        }
        return relation;
    }

    private void recordInvite(
            long userId,
            long groupId,
            String githubLogin,
            String result,
            @Nullable String message
    ) {
        try {
            inviteEventMapper.insert(new CommunityGroupInviteEvent()
                    .setUserId(userId)
                    .setGroupId(groupId)
                    .setGithubLogin(githubLogin)
                    .setResult(result)
                    .setMessage(message == null ? null : truncate(message, 2_000)));
        } catch (Exception exception) {
            log.warn("Unable to record community group invite event for user {} and group {}: {}",
                    userId, groupId, exception.getMessage());
        }
    }

    private GithubStatusView statusView(
            long groupId,
            String status,
            String organizationState,
            List<TeamStatusView> teams,
            @Nullable String message
    ) {
        return new GithubStatusView(
                groupId,
                status,
                organizationState,
                teams,
                message,
                OffsetDateTime.now()
        );
    }

    private static String normalizeState(String value) {
        return value == null ? "NONE" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String githubPart(String value, String label) throws BusinessException {
        String normalized = required(value, label, 100);
        if (!GITHUB_PART.matcher(normalized).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    label + " contains unsupported characters");
        }
        return normalized;
    }

    private String required(String value, String label, int maxLength)
            throws BusinessException {
        String normalized = optional(value, maxLength);
        if (normalized.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, label + " is required");
        }
        return normalized;
    }

    private String optional(String value, int maxLength) throws BusinessException {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "A community group field is too long");
        }
        return normalized;
    }

    @Nullable
    private String nullableUrl(String value) throws BusinessException {
        String normalized = optional(value, 2_048);
        if (normalized.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Community group URL is invalid");
        }
        if (uri.getScheme() == null
                || !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Community group URL must be an HTTP(S) URL");
        }
        return normalized;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record NormalizedGroup(
            String slug,
            CommunityGroupType type,
            String titleZh,
            String titleEn,
            String descriptionZh,
            String descriptionEn,
            String url,
            String githubOrganization,
            boolean enabled,
            int sortOrder,
            List<GithubTeamView> teams
    ) {
    }

    private record GroupRow(
            long id,
            String slug,
            CommunityGroupType type,
            String titleZh,
            String titleEn,
            String descriptionZh,
            String descriptionEn,
            String url,
            String githubOrganization,
            boolean enabled,
            int sortOrder,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            Long updatedBy
    ) {
        private static GroupRow from(CommunityGroup entity) {
            return new GroupRow(
                    entity.getId(),
                    entity.getSlug(),
                    CommunityGroupType.parse(entity.getGroupType())
                            .orElse(CommunityGroupType.LINK),
                    entity.getTitleZh(),
                    entity.getTitleEn(),
                    entity.getDescriptionZh(),
                    entity.getDescriptionEn(),
                    entity.getUrl(),
                    entity.getGithubOrganization(),
                    Boolean.TRUE.equals(entity.getEnabled()),
                    entity.getSortOrder() == null ? 0 : entity.getSortOrder(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt(),
                    entity.getUpdatedBy()
            );
        }

        private GroupView toView(List<GithubTeamView> teams) {
            return new GroupView(
                    id,
                    slug,
                    type.name(),
                    titleZh,
                    titleEn,
                    descriptionZh,
                    descriptionEn,
                    url,
                    githubOrganization,
                    enabled,
                    sortOrder,
                    teams,
                    createdAt,
                    updatedAt,
                    updatedBy
            );
        }

        private String publicUrl() {
            if (url != null && !url.isBlank()) {
                return url;
            }
            if (type == CommunityGroupType.GITHUB && githubOrganization != null) {
                return "https://github.com/orgs/" + githubOrganization;
            }
            return null;
        }
    }
}
