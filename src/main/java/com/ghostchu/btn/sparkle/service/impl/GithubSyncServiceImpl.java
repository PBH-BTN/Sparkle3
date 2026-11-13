package com.ghostchu.btn.sparkle.service.impl;

import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.RunResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.TargetRunResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.TargetView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.github.GithubRuleSourceCatalog;
import com.ghostchu.btn.sparkle.service.github.GithubSyncSettingsService;
import com.ghostchu.btn.sparkle.service.github.GithubSyncTargetService;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GithubSyncServiceImpl {
    private final GithubSyncSettingsService settingsService;
    private final GithubSyncTargetService targetService;
    private final GithubRuleSourceCatalog sourceCatalog;

    @MonitoredScheduledTask(
            id = "github-rule-sync",
            group = TaskGroup.SYNC,
            nameKey = "admin.tasks.items.github-rule-sync.name",
            descriptionKey = "admin.tasks.items.github-rule-sync.description"
    )
    public RunResult scheduleSync() {
        try {
            targetService.seedLegacyTargetIfNeeded(settingsService);
        } catch (BusinessException exception) {
            throw new IllegalStateException("Unable to initialize GitHub sync targets", exception);
        }
        List<TargetView> targets = targetService.enabledTargets();
        if (targets.isEmpty()) {
            return new RunResult(0, 0, 0, List.of());
        }
        GitHub github;
        try {
            github = buildClient();
        } catch (IOException exception) {
            List<TargetRunResult> failures = targets.stream()
                    .map(target -> {
                        String message = "Unable to initialize GitHub client: "
                                + exception.getMessage();
                        targetService.markAttempt(target.id());
                        targetService.markFailure(target.id(), message);
                        return new TargetRunResult(
                                target.id(),
                                target.name(),
                                false,
                                message,
                                null
                        );
                    })
                    .toList();
            return new RunResult(targets.size(), 0, targets.size(), failures);
        }

        List<TargetRunResult> results = new ArrayList<>(targets.size());
        for (TargetView target : targets) {
            results.add(sync(target, github));
        }
        int succeeded = (int) results.stream().filter(TargetRunResult::success).count();
        return new RunResult(results.size(), succeeded, results.size() - succeeded, results);
    }

    public TargetRunResult syncTarget(long id) throws BusinessException {
        targetService.seedLegacyTargetIfNeeded(settingsService);
        TargetView target = targetService.find(id);
        if (target == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "GitHub sync target not found");
        }
        if (!target.enabled()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GitHub sync target is disabled");
        }
        try {
            return sync(target, buildClient());
        } catch (IOException exception) {
            targetService.markAttempt(id);
            targetService.markFailure(id, exception.getMessage());
            return new TargetRunResult(
                    target.id(),
                    target.name(),
                    false,
                    "Unable to initialize GitHub client: " + exception.getMessage(),
                    null
            );
        }
    }

    private GitHub buildClient() throws IOException {
        String token = settingsService.accessToken();
        GitHubBuilder builder = new GitHubBuilder();
        if (!token.isBlank()) {
            builder.withOAuthToken(token);
        }
        return builder.build();
    }

    private TargetRunResult sync(TargetView target, GitHub github) {
        targetService.markAttempt(target.id());
        try {
            GHRepository repository = github.getRepository(
                    target.repositoryOwner() + "/" + target.repositoryName()
            );
            String content = sourceCatalog.combineContent(target.sourceIds());
            FileUpdateResult update = updateFile(
                    repository,
                    target.branchName(),
                    target.filePath(),
                    content.getBytes(StandardCharsets.UTF_8)
            );
            targetService.markSuccess(target.id(), update.sha());
            return new TargetRunResult(
                    target.id(),
                    target.name(),
                    true,
                    update.changed() ? "Updated" : "Already up to date",
                    update.sha()
            );
        } catch (Throwable throwable) {
            String message = throwable.getMessage() == null
                    ? throwable.getClass().getSimpleName()
                    : throwable.getMessage();
            targetService.markFailure(target.id(), message);
            log.error("Unable to sync GitHub target {} ({}/{})", target.id(),
                    target.repositoryOwner(), target.repositoryName(), throwable);
            return new TargetRunResult(
                    target.id(),
                    target.name(),
                    false,
                    message,
                    null
            );
        }
    }

    private FileUpdateResult updateFile(
            GHRepository repository,
            String branch,
            String file,
            byte[] content
    ) throws IOException {
        GHContent oldFile = repository.getFileContent(file, branch);
        String sha = oldFile != null ? oldFile.getSha() : null;
        if (oldFile != null) {
            try {
                @Cleanup
                var input = oldFile.read();
                if (Arrays.equals(content, input.readAllBytes())) {
                    log.info("{}: content unchanged, skipping", file);
                    return new FileUpdateResult(sha, false);
                }
            } catch (Exception exception) {
                log.warn(
                        "Unable to read existing GitHub content for {}, overwriting: {}",
                        file,
                        exception.getMessage()
                );
            }
        }
        var response = repository.createContent()
                .path(file)
                .branch(branch)
                .message("[Sparkle3] Auto update " + file)
                .sha(sha)
                .content(content)
                .commit();
        String commitSha = response.getCommit().getSHA1();
        log.info("Updated GitHub file {} on {}/{}@{}: {}",
                file,
                repository.getOwnerName(),
                repository.getName(),
                branch,
                commitSha);
        return new FileUpdateResult(commitSha, true);
    }

    private record FileUpdateResult(String sha, boolean changed) {
    }
}
