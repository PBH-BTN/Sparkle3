package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformModels.ProviderUpdateRequest;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformModels.ProviderView;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformModels.SettingsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformAdminModels.ReconciliationView;
import com.ghostchu.btn.sparkle.controller.admin.dto.MemberPlatformAdminModels.TransactionPage;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.security.SparkleUserDetails;
import com.ghostchu.btn.sparkle.service.ExchangeRateService;
import com.ghostchu.btn.sparkle.service.MemberPlatformService;
import com.ghostchu.btn.sparkle.service.MemberPlatformAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/memberships")
@RequiredArgsConstructor
public class AdminMemberPlatformController {
    private final MemberPlatformService memberPlatformService;
    private final MemberPlatformAdminService memberPlatformAdminService;
    private final ExchangeRateService exchangeRateService;

    @GetMapping
    public SettingsView settings() {
        return memberPlatformService.settings();
    }

    @PutMapping("/{provider}")
    public ProviderView update(
            @PathVariable String provider,
            @RequestBody ProviderUpdateRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        MemberPlatform platform = memberPlatformService.parseProvider(provider);
        return memberPlatformService.update(
                platform,
                request,
                principal == null ? null : principal.getUserId()
        );
    }

    @PutMapping("/currency")
    public Map<String, Object> updateCurrency(
            @RequestBody CurrencyUpdateRequest request,
            @AuthenticationPrincipal SparkleUserDetails principal
    ) throws BusinessException {
        exchangeRateService.updateSettings(
                request.baseCurrency(),
                request.exchangeRateApiUrl(),
                principal == null ? null : principal.getUserId()
        );
        exchangeRateService.refresh();
        int recalculatedTransactions =
                memberPlatformService.recalculateTransactionBaseAmounts();
        return Map.of(
                "baseCurrency", exchangeRateService.baseCurrency(),
                "exchangeRateApiUrl", exchangeRateService.apiUrl(),
                "recalculatedTransactions", recalculatedTransactions
        );
    }

    @PostMapping("/sync")
    public Map<String, Object> sync() {
        return Map.of("synchronizedProviders", memberPlatformService.syncAll());
    }

    @GetMapping("/transactions")
    public TransactionPage transactions(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean remotePresent,
            @RequestParam(required = false) OffsetDateTime occurredFrom,
            @RequestParam(required = false) OffsetDateTime occurredTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) throws BusinessException {
        return memberPlatformAdminService.transactions(
                provider,
                keyword,
                type,
                status,
                remotePresent,
                occurredFrom,
                occurredTo,
                page,
                size
        );
    }

    @GetMapping("/reconciliation")
    public ReconciliationView reconciliation(
            @RequestParam(required = false) String provider,
            @RequestParam(defaultValue = "50") int limit
    ) throws BusinessException {
        return memberPlatformAdminService.reconciliation(provider, limit);
    }

    public record CurrencyUpdateRequest(
            String baseCurrency,
            String exchangeRateApiUrl
    ) {
    }
}
