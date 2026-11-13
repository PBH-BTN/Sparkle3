package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.controller.ui.dto.AfdianPublicModels.LicenseView;
import com.ghostchu.btn.sparkle.entity.MemberPlatformBinding;
import com.ghostchu.btn.sparkle.entity.PbhLicense;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.MemberPlatformBindingMapper;
import com.ghostchu.btn.sparkle.mapper.MemberTransactionMapper;
import com.ghostchu.btn.sparkle.mapper.PbhLicenseMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PbhLicenseServiceTest {
    private final MemberPlatformBindingMapper bindingMapper =
            mock(MemberPlatformBindingMapper.class);
    private final MemberTransactionMapper transactionMapper =
            mock(MemberTransactionMapper.class);
    private final PbhLicenseMapper licenseMapper = mock(PbhLicenseMapper.class);
    private final IUserService userService = mock(IUserService.class);
    private final AfdianSettingsService settingsService =
            mock(AfdianSettingsService.class);
    private final ExchangeRateService exchangeRateService =
            mock(ExchangeRateService.class);

    @Test
    void generatesDecryptablePbhLicenseV2Payload() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        var keyPair = generator.generateKeyPair();
        String privateKey = Base64.getEncoder()
                .encodeToString(keyPair.getPrivate().getEncoded());

        when(bindingMapper.selectList(any())).thenReturn(List.of(
                new MemberPlatformBinding()
                        .setProvider(MemberPlatform.PATREON.name())
                        .setBtnUserId(42L)
                        .setExternalUserId("patron-42")
                        .setExternalUserName("Patron 42")
        ));
        when(transactionMapper.sumCompletedDonationBase(42L))
                .thenReturn(new BigDecimal("50.00"));
        when(transactionMapper.countAvailableProductSlots(42L)).thenReturn(0);
        when(transactionMapper.lockNextAvailableProduct(42L)).thenReturn(null);
        when(licenseMapper.countDonationLicenses(42L)).thenReturn(0);
        when(userService.getById(42L)).thenReturn(new User()
                .setId(42L)
                .setNickname("Sparkle User")
                .setEmail("user@example.invalid"));
        when(settingsService.donationLicenseAmount())
                .thenReturn(new BigDecimal("50.00"));
        when(settingsService.licenseValidYears()).thenReturn(100);
        when(settingsService.licensePrivateKeyConfigured()).thenReturn(true);
        when(settingsService.licensePrivateKey()).thenReturn(privateKey);
        when(exchangeRateService.baseCurrency()).thenReturn("USD");
        AtomicReference<PbhLicense> capturedLicense = new AtomicReference<>();
        when(licenseMapper.insertWithPayload(any())).thenAnswer(invocation -> {
            PbhLicense license = invocation.getArgument(0);
            license.setIssuedAt(OffsetDateTime.now());
            capturedLicense.set(license);
            return 1;
        });
        when(licenseMapper.selectOne(any()))
                .thenAnswer(invocation -> capturedLicense.get());

        PbhLicenseService service = new PbhLicenseService(
                bindingMapper,
                transactionMapper,
                licenseMapper,
                userService,
                settingsService,
                exchangeRateService
        );

        LicenseView result = service.generate(42L);

        verify(licenseMapper).lockKey("sparkle:pbh-license:42");
        assertEquals("DONATION", result.sourceType());
        assertTrue(result.consumedAmount().startsWith("50"));
        JsonNode payload = decrypt(result.licenseKey(), keyPair.getPublic());
        assertEquals("PeerBanHelper", payload.path("verifyMagic").asText());
        assertEquals(2, payload.path("licenseVersion").asInt());
        assertEquals("patreon", payload.path("type").asText());
        assertEquals("PBH-BTN ALIS", payload.path("source").asText());
        assertTrue(payload.path("orderId").asText().startsWith("SPARKLE-"));
        assertEquals("patreon", payload.path("paymentGateway").asText());
    }

    private JsonNode decrypt(String licenseKey, PublicKey publicKey) throws Exception {
        byte[] encrypted = Base64.getDecoder().decode(licenseKey);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int offset = 0; offset < encrypted.length; offset += 128) {
            output.write(cipher.doFinal(encrypted, offset, 128));
        }
        return new ObjectMapper().readTree(
                output.toString(StandardCharsets.UTF_8)
        );
    }

    @Test
    void adminLicensesDeriveStatusAndExposeUser() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        Page<PbhLicense> page = new Page<>(1, 20);
        page.setRecords(List.of(
                license(42L, now.plusYears(1), null),
                license(42L, now.minusDays(1), null),
                license(7L, now.plusYears(1), now.minusHours(1))
        ));
        page.setTotal(3);
        when(licenseMapper.selectPage(any(), any())).thenReturn(page);
        when(userService.listByIds(anyList())).thenReturn(List.of(
                new User().setId(42L).setNickname("alice")
        ));
        when(exchangeRateService.baseCurrency()).thenReturn("CNY");

        var result = service().adminLicenses(null, null, null, "ALL", 1, 20);

        assertEquals(3, result.total());
        assertEquals(1, result.page());
        assertEquals(20, result.size());
        assertEquals("CNY", result.baseCurrency());
        assertEquals("ACTIVE", result.items().get(0).status());
        assertEquals("EXPIRED", result.items().get(1).status());
        assertEquals("REVOKED", result.items().get(2).status());
        assertEquals("42", result.items().get(0).btnUserId());
        assertEquals("alice", result.items().get(0).btnNickname());
        assertEquals("12.50", result.items().get(0).consumedAmount());
    }

    @Test
    void adminLicensesApplyProviderAndStatusFilters() throws Exception {
        Page<PbhLicense> page = new Page<>(1, 20);
        page.setTotal(0);
        when(licenseMapper.selectPage(any(), any())).thenReturn(page);
        when(exchangeRateService.baseCurrency()).thenReturn("CNY");

        service().adminLicenses(null, "patreon", "DONATION", "ACTIVE", 1, 20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<PbhLicense>> wrapper =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(licenseMapper).selectPage(any(), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("provider ="));
        assertTrue(sql.contains("source_type ="));
        assertTrue(sql.contains("revoked_at IS NULL"));
        assertTrue(sql.contains("expire_at >"));
    }

    @Test
    void adminLicensesRejectUnknownFilters() {
        PbhLicenseService service = service();

        assertThrows(
                BusinessException.class,
                () -> service.adminLicenses(null, "unknown", null, null, 1, 20)
        );
        assertThrows(
                BusinessException.class,
                () -> service.adminLicenses(null, null, "SUBSCRIPTION", null, 1, 20)
        );
        assertThrows(
                BusinessException.class,
                () -> service.adminLicenses(null, null, null, "WHATEVER", 1, 20)
        );
    }

    private PbhLicenseService service() {
        return new PbhLicenseService(
                bindingMapper,
                transactionMapper,
                licenseMapper,
                userService,
                settingsService,
                exchangeRateService
        );
    }

    @Test
    void pendingHistoricalIssuanceBlocksNewSlotsUntilReviewed() throws Exception {
        when(bindingMapper.selectList(any())).thenReturn(List.of(new MemberPlatformBinding()
                .setProvider("AFDIAN").setBtnUserId(42L).setExternalUserId("user")));
        when(settingsService.donationLicenseAmount()).thenReturn(new BigDecimal("50"));
        when(licenseMapper.countUnmatchedImports(42L)).thenReturn(1);
        when(licenseMapper.selectList(any())).thenReturn(List.of());
        var view = service().availability(42L);
        assertEquals(0, view.totalAvailable());
        assertTrue(view.historicalReviewRequired());
    }

    @Test
    void recentLicensesSupportHistoricalIssuanceBeforeAccountBinding() {
        when(licenseMapper.selectList(any())).thenReturn(List.of(
                license(42L, OffsetDateTime.now().plusYears(1), null).setBtnUserId(null)));
        var view = service().recentLicenses(20);
        assertEquals(1, view.size());
        assertEquals(null, view.getFirst().btnUserId());
    }

    @Test
    void userLicenseQueryAssociatesImportsThroughCurrentPlatformBinding() {
        when(bindingMapper.selectList(any())).thenReturn(List.of(new MemberPlatformBinding()
                .setProvider("AFDIAN").setBtnUserId(42L).setExternalUserId("user")));
        when(settingsService.donationLicenseAmount()).thenReturn(new BigDecimal("50"));
        when(licenseMapper.selectList(any())).thenReturn(List.of());
        service().availability(42L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<PbhLicense>> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(licenseMapper).selectList(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("member_platform_bindings"));
        assertTrue(query.getValue().getSqlSegment().contains("b.external_user_id = pbh_licenses.external_user_id"));
    }

    private PbhLicense license(
            long btnUserId,
            OffsetDateTime expireAt,
            OffsetDateTime revokedAt
    ) {
        return new PbhLicense()
                .setLicenseId(UUID.randomUUID())
                .setBtnUserId(btnUserId)
                .setProvider("PATREON")
                .setSourceType("DONATION")
                .setConsumedAmount(new BigDecimal("12.50"))
                .setIssuedAt(OffsetDateTime.now().minusDays(1))
                .setStartAt(OffsetDateTime.now().minusDays(1))
                .setExpireAt(expireAt)
                .setRevokedAt(revokedAt)
                .setRevokedReason(revokedAt == null ? null : "测试撤销");
    }
}
