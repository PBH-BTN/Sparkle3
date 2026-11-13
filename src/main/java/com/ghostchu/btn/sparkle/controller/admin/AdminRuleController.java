package com.ghostchu.btn.sparkle.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.PageResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.AdminModels.RuleSummary;
import com.ghostchu.btn.sparkle.entity.Rule;
import com.ghostchu.btn.sparkle.service.IRuleService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

@RestController
@RequestMapping("/api/admin/rules")
@RequiredArgsConstructor
public class AdminRuleController {
    private static final Set<String> RULE_TYPES = Set.of(
            "ip_denylist",
            "peer_id",
            "client_name",
            "port",
            "script"
    );

    private final IRuleService ruleService;

    @GetMapping
    public PageResult<Rule> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type
    ) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
        QueryWrapper<Rule> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim();
            wrapper.and(condition -> condition
                    .like("category", term)
                    .or()
                    .like("content", term));
        }
        if (type != null && !type.isBlank()) {
            wrapper.eq("type", type.trim());
        }
        wrapper.orderByDesc("id");
        Page<Rule> result = ruleService.page(new Page<>(safePage, safeSize), wrapper);
        return new PageResult<>(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal(), result.getPages());
    }

    @GetMapping("/summary")
    public List<RuleSummary> summary() {
        return RULE_TYPES.stream()
                .map(type -> new RuleSummary(type, ruleService.count(new QueryWrapper<Rule>().eq("type", type))))
                .toList();
    }

    @GetMapping("/generated-content")
    public String generatedDenyList() {
        StringJoiner joiner = new StringJoiner("\n\n");
        for (Rule rule : ruleService.getRulesByType("ip_denylist")) {
            joiner.add("# [Sparkle3 手动规则] " + rule.getCategory() + "\n" + rule.getContent());
        }
        return joiner.toString();
    }

    @PostMapping
    public Rule create(@RequestBody RuleRequest request) {
        validate(request);
        Rule rule = new Rule()
                .setType(request.type())
                .setCategory(request.category().trim())
                .setContent(request.content().trim());
        ruleService.save(rule);
        return rule;
    }

    @PutMapping("/{id}")
    public Rule update(@PathVariable long id, @RequestBody RuleRequest request) {
        Rule rule = requireRule(id);
        validate(request);
        rule.setType(request.type())
                .setCategory(request.category().trim())
                .setContent(request.content().trim());
        ruleService.updateById(rule);
        return rule;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        requireRule(id);
        ruleService.removeById(id);
    }

    private void validate(RuleRequest request) {
        if (request.type() == null || !RULE_TYPES.contains(request.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported rule type");
        }
        if (request.category() == null || request.category().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rule category is required");
        }
        if (request.category().trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rule category must not exceed 255 characters");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rule content is required");
        }
    }

    private Rule requireRule(long id) {
        Rule rule = ruleService.getById(id);
        if (rule == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found");
        }
        return rule;
    }

    public record RuleRequest(String type, String category, String content) {
    }
}
