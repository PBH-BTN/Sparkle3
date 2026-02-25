package com.ghostchu.btn.sparkle.service.impl;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    @Autowired
    private AnalyseRuleUnTrustVoteServiceImpl unTrustVoteService;
    @Autowired
    private AnalyseRuleOverDownloadServiceImpl overDownloadService;
    @Autowired
    private AnalyseRuleConcurrentDownloadServiceImpl concurrentDownloadService;
    @Autowired
    private AnalyseGopeedDevIdentityServiceImpl gopeedDevIdentityService;
    @Autowired
    private AnalyseRain000IdentityServiceImpl rain000IdentityService;
    @Autowired
    private AnalyseRandomIdentityServiceImpl randomIdentityService;


    @Scheduled(cron = "${sparkle.github-sync.schedule}")
    public void scheduleSync() throws IOException {
        log.info("开始更新 GitHub 同步规则存储库...");
        GitHub github = new GitHubBuilder().withOAuthToken(accessToken, orgName).build();
        var organization = github.getOrganization(orgName);
        if (organization == null) {
            throw new IllegalArgumentException("Organization " + orgName + " not found");
        }
        var repository = organization.getRepository(repoName);

        var untrustIps = unTrustVoteService.getGeneratedContent().getValue();
        var concurrentDownloadIps = concurrentDownloadService.getGeneratedContent().getValue();
        var overDownloadIps = overDownloadService.getGeneratedContent().getValue();
        var randomIdentityIps = randomIdentityService.getGeneratedContent().getValue();
        var rain000IdentityIps = rain000IdentityService.getGeneratedContent().getValue();
        var gopeedDevIdentityIps = gopeedDevIdentityService.getGeneratedContent().getValue();
        if(untrustIps != null)
            updateFile(repository, "untrusted-ips.txt", ()->untrustIps.getBytes(StandardCharsets.UTF_8));
        if(concurrentDownloadIps != null)
            updateFile(repository, "concurrent-downloads-ips.txt", ()->concurrentDownloadIps.getBytes(StandardCharsets.UTF_8));
        if(overDownloadIps != null)
            updateFile(repository, "overdownload-ips.txt", ()->overDownloadIps.getBytes(StandardCharsets.UTF_8));
        if(randomIdentityIps != null)
            updateFile(repository, "random-identity.txt", ()->randomIdentityIps.getBytes(StandardCharsets.UTF_8));
        if(rain000IdentityIps != null)
            updateFile(repository, "rain0.0.0.txt", ()->rain000IdentityIps.getBytes(StandardCharsets.UTF_8));
        if(gopeedDevIdentityIps != null)
            updateFile(repository, "gopeeddev.txt", ()->gopeedDevIdentityIps.getBytes(StandardCharsets.UTF_8));
        log.info("GitHub 同步规则存储库更新完成");
    }


    private void updateFile(GHRepository repository, String file, Supplier<byte[]> contentSupplier) {
        try {
            var oldFile = repository.getFileContent(file);
            var sha = oldFile != null ? oldFile.getSha() : null;
            var content = contentSupplier.get();
            if (oldFile != null) {
                try {
                    @Cleanup
                    var is = oldFile.read();
                    var oldData = is.readAllBytes();
                    if (Arrays.equals(content, oldData)) {
                        log.info("{}: 无需更新，跳过", file);
                        return;
                    }
                }catch (Exception e){
                    log.warn("Unable to read existing file content for {}, will attempt to overwrite. Error: {}", file, e.getMessage());
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
