package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.ui.dto.AfdianPublicModels.BindingView;
import com.ghostchu.btn.sparkle.entity.AfdianBinding;
import com.ghostchu.btn.sparkle.entity.MemberTransaction;
import com.ghostchu.btn.sparkle.mapper.AfdianBindingMapper;
import com.ghostchu.btn.sparkle.mapper.AfdianDonationMapper;
import com.ghostchu.btn.sparkle.mapper.MemberPlatformBindingMapper;
import com.ghostchu.btn.sparkle.mapper.MemberTransactionMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AfdianIntegrationServiceTest {
    private final AfdianBindingMapper bindingMapper =
            mock(AfdianBindingMapper.class);
    private final AfdianDonationMapper donationMapper =
            mock(AfdianDonationMapper.class);
    private final MemberPlatformBindingMapper memberBindingMapper =
            mock(MemberPlatformBindingMapper.class);
    private final MemberTransactionMapper transactionMapper =
            mock(MemberTransactionMapper.class);
    private final IUserService userService = mock(IUserService.class);
    private final AfdianSettingsService settingsService =
            mock(AfdianSettingsService.class);
    private final AfdianClient afdianClient = mock(AfdianClient.class);
    private final PbhLicenseService licenseService =
            mock(PbhLicenseService.class);
    private final ExchangeRateService exchangeRateService =
            mock(ExchangeRateService.class);
    private final MemberTransactionLedgerService ledgerService =
            mock(MemberTransactionLedgerService.class);
    private final AfdianEmailBindingService emailBinding = mock(AfdianEmailBindingService.class);
    private final AfdianIntegrationService service =
            new AfdianIntegrationService(
                    bindingMapper,
                    donationMapper,
                    memberBindingMapper,
                    transactionMapper,
                    userService,
                    settingsService,
                    afdianClient,
                    new ObjectMapper(),
                    licenseService,
                    exchangeRateService,
                    ledgerService,
                    emailBinding
            );

    @Test
    void cumulativeAmountCoversDonationsAndProductOrders() {
        stubBinding();
        when(transactionMapper.selectList(any())).thenReturn(List.of(
                transaction("D-1", "DONATION", "2", "50.00", true),
                transaction("P-1", "PRODUCT", "2", "100.00", true),
                transaction("P-2", "PRODUCT", "0", "30.00", true),
                transaction("D-2", "DONATION", "2", "20.00", false)
        ));

        BindingView view = service.bindingForUser(9L);

        assertEquals("150.00", view.totalAmount());
        assertEquals(2, view.transactionCount());
    }

    @Test
    void cumulativeAmountFallsBackToOrderAmountWithoutSettlementAmount() {
        stubBinding();
        when(transactionMapper.selectList(any())).thenReturn(List.of(
                transaction("P-1", "PRODUCT", "2", "88.50", true)
                        .setAmountCny(null)
        ));

        BindingView view = service.bindingForUser(9L);

        assertEquals("88.50", view.totalAmount());
        assertEquals(1, view.transactionCount());
    }

    @Test
    void unboundUserHasNoCumulativePayment() {
        when(bindingMapper.selectOne(any())).thenReturn(null);
        when(settingsService.configured()).thenReturn(false);

        BindingView view = service.bindingForUser(9L);

        assertEquals("0.00", view.totalAmount());
        assertEquals(0, view.transactionCount());
    }

    private void stubBinding() {
        when(bindingMapper.selectOne(any())).thenReturn(new AfdianBinding()
                .setId(1L)
                .setBtnUserId(9L)
                .setAfdianUserId("AFD-1"));
        when(settingsService.configured()).thenReturn(true);
    }

    private MemberTransaction transaction(
            String id,
            String type,
            String status,
            String amount,
            boolean remotePresent
    ) {
        return new MemberTransaction()
                .setExternalTransactionId(id)
                .setExternalUserId("AFD-1")
                .setTransactionType(type)
                .setAmount(new BigDecimal(amount))
                .setAmountCny(new BigDecimal(amount))
                .setTransactionStatus(status)
                .setRemotePresent(remotePresent);
    }

    @Test
    void completedNewAndHistoricalOrdersTryEmailBindingDuringEverySync() throws Exception {
        when(settingsService.configured()).thenReturn(true);
        when(bindingMapper.selectList(any())).thenReturn(List.of());
        var raw = new ObjectMapper().createObjectNode();
        var paid = new AfdianClient.AfdianOrder("paid-order", "afdian-user", "Buyer", null,
                "plan", "Product", BigDecimal.TEN, BigDecimal.TEN, 2, "buyer@example.test",
                java.time.OffsetDateTime.now(), 1, raw.arrayNode(), raw);
        var unpaid = new AfdianClient.AfdianOrder("unpaid-order", "other-user", "Other", null,
                "plan", "Product", BigDecimal.TEN, BigDecimal.TEN, 0, "other@example.test",
                java.time.OffsetDateTime.now(), 1, raw.arrayNode(), raw);
        when(afdianClient.queryOrders(any())).thenReturn(new AfdianClient.OrderPage(List.of(paid, unpaid), 2, 1));
        service.syncNow();
        service.syncNow();
        org.mockito.Mockito.verify(emailBinding, org.mockito.Mockito.times(2))
                .bind("afdian-user", "paid-order", "buyer@example.test");
        org.mockito.Mockito.verify(emailBinding, org.mockito.Mockito.never())
                .bind("other-user", "unpaid-order", "other@example.test");
    }
}
