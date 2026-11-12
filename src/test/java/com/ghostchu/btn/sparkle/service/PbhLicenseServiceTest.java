package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.constants.MemberPlatform;
import com.ghostchu.btn.sparkle.controller.ui.dto.AfdianPublicModels.LicenseView;
import com.ghostchu.btn.sparkle.entity.MemberPlatformBinding;
import com.ghostchu.btn.sparkle.entity.PbhLicense;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.mapper.MemberPlatformBindingMapper;
import com.ghostchu.btn.sparkle.mapper.MemberTransactionMapper;
import com.ghostchu.btn.sparkle.mapper.PbhLicenseMapper;
import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
}
