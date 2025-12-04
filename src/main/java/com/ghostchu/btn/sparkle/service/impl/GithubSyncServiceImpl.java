package com.ghostchu.btn.sparkle.service.impl;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;

@Service
@Slf4j
public class GithubSyncServiceImpl {
    @Value("${sparkle.github-sync.personal-access-token}")
    private String accessToken;
    @Value("${sparkle.github-sync.org-name}")
    private String orgName;
    @Value("${sparkle.github-sync.repository-name}")
    private String repoName;
    @Value("${sparkle.github-sync.branch-name}")
    private String branchName;

    @Scheduled(cron = "${sparkle.github-sync.schedule}")
    public void scheduleSync() throws IOException {
        log.info("开始更新 GitHub 同步规则存储库...");
        GitHub github = new GitHubBuilder().withOAuthToken(accessToken, orgName).build();
        var organization = github.getOrganization(orgName);
        if (organization == null) {
            throw new IllegalArgumentException("Organization " + orgName + " not found");
        }
        var repository = organization.getRepository(repoName);
    }


    private void updateFile(GHRepository repository, String file, Supplier<byte[]> contentSupplier) {
        try {
            var oldFile = repository.getFileContent(file);
            var sha = oldFile != null ? oldFile.getSha() : null;
            var content = contentSupplier.get();
            if (oldFile != null) {
                @Cleanup
                var is = oldFile.read();
                var oldData = is.readAllBytes();
                if (Arrays.equals(content, oldData)) {
                    log.info("{}: 无需更新，跳过", file);
                    return;
                }
            }
            var commitResponse = repository.createContent()
                    .path(file)
                    .branch(branchName)
                    .message("[Sparkle3] 自动更新 " + file)
                    .sha(sha)
                    .content(content)
                    .commit();
            var commit = commitResponse.getCommit();
            log.info("GitHub 同步规则 “{}” 更新结果：Sha: {}", file, commit.getSHA1());
        } catch (Throwable e) {
            log.error("无法完成数据更新操作", e);
        }
    }

}
