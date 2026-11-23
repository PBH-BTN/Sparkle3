package com.ghostchu.btn.sparkle.controller.ping;

import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentAccess;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentPage;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CommentView;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.CreateCommentRequest;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.LookupResponse;
import com.ghostchu.btn.sparkle.controller.ui.dto.IpQueryModels.UpdateCommentRequest;
import com.ghostchu.btn.sparkle.entity.Userapp;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.exception.PowCaptchaFailureException;
import com.ghostchu.btn.sparkle.exception.UserApplicationBannedException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.btn.BtnRouteResolver;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.ipquery.IpQueryCommentCountService;
import com.ghostchu.btn.sparkle.service.ipquery.IpQueryCommentService;
import com.ghostchu.btn.sparkle.service.ipquery.IpQueryLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ping/queryIp")
@RequiredArgsConstructor
public class PingIpQueryDetailsController extends BasePingController {
    private final BtnRuntimeConfigService config;
    private final IUserService users;
    private final IpQueryLookupService lookupService;
    private final IpQueryCommentService comments;
    private final IpQueryCommentCountService commentCounts;
    private final BtnRouteResolver routeResolver;

    @GetMapping("/lookup")
    public Lookup lookup(@RequestParam String query)
            throws
            BusinessException {
        Userapp app = authenticate("powCaptcha", false);
        LookupResponse lookup = lookupService.lookup(query);
        return new Lookup(lookup, lookup.commentAccess().publishingEnabled() && !app.isSuspended() && eligible(app));
    }

    @GetMapping("/comments")
    public CommentPage comments(
            @RequestParam String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) throws
            BusinessException {
        Userapp app = authenticate("powCaptcha", false);
        boolean eligibleOwner = eligible(app);
        var result = comments.list(eligibleOwner ? app.getOwner() : -1, scope, page, size, false);
        var access = result.access();
        boolean canPublish = eligibleOwner && !app.isSuspended() && access.publishingEnabled();
        return new CommentPage(withAbsoluteAvatars(result.records()), result.current(), result.size(), result.total(), result.pages(),
                result.scope(), new CommentAccess(access.visible(), canPublish,
                access.moderationEnabled(), access.maxLength(), access.hourlyLimit()));
    }

    @PostMapping("/comments")
    public CommentView create(@RequestBody CreateCommentRequest body)
            throws
            BusinessException {
        long owner = requireEligible(authenticate("powCaptcha", true));
        return withAbsoluteAvatar(comments.create(owner, body, request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    @PutMapping("/comments/{id}")
    public CommentView update(@PathVariable long id, @RequestBody UpdateCommentRequest body)
            throws
            BusinessException {
        long owner = requireEligible(authenticate("powCaptcha", true));
        return withAbsoluteAvatar(comments.update(owner, id, body, request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id)
            throws
            BusinessException {
        comments.delete(requireEligible(authenticate("powCaptcha", true)), id);
    }

    @PostMapping("/commentCounts")
    public Map<String, Long> count(@RequestBody List<String> ips)
            throws
            BusinessException {
        return commentCounts.count(authenticate("commentCountPowCaptcha", false).getId(), ips);
    }

    private List<CommentView> withAbsoluteAvatars(List<CommentView> records) {
        if (records.stream().noneMatch(comment -> comment.authorAvatarUrl() != null)) {
            return records;
        }
        String rootUrl = avatarRootUrl();
        return records.stream()
                .map(comment -> comment.authorAvatarUrl() == null ? comment
                        : comment.withAuthorAvatarUrl(rootUrl + comment.authorAvatarUrl()))
                .toList();
    }

    private CommentView withAbsoluteAvatar(CommentView comment) {
        return comment.authorAvatarUrl() == null ? comment
                : comment.withAuthorAvatarUrl(avatarRootUrl() + comment.authorAvatarUrl());
    }

    private String avatarRootUrl() {
        return routeResolver.resolve(request.getRemoteAddr()).replaceAll("/+$", "");
    }

    private Userapp authenticate(String powSetting, boolean submission)
            throws AccessDeniedException, UserApplicationBannedException,
            UserApplicationNotFoundException, PowCaptchaFailureException {
        if (config.booleanValue("queryIp", powSetting) && !validatePowCaptcha()) {
            throw new PowCaptchaFailureException();
        }
        return submission ? verifyUserApplicationForSubmission(false) : verifyUserApplicationForRead(false);
    }

    private boolean eligible(Userapp app) {
        long owner = app.getOwner();
        if (owner == config.longValue("userappAutoAccount", "holderUid")
                || app.getAppId().startsWith("AUTOACCOUNT-")) {
            return false;
        }
        var user = users.getById(owner);
        return user != null && !user.isSuspended();
    }

    private long requireEligible(Userapp app) throws BusinessException {
        if (!eligible(app)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "This BTN account cannot publish IP comments");
        }
        return app.getOwner();
    }

    public record Lookup(LookupResponse lookup, boolean canComment) {
    }
}
