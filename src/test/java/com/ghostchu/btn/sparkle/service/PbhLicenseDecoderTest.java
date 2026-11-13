package com.ghostchu.btn.sparkle.service;

import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PbhLicenseDecoderTest {
    private final PbhLicenseDecoder decoder = new PbhLicenseDecoder();

    @Test
    void extractsV1AndV2InsideProseAndWrappedCodeBlocks() throws Exception {
        KeyPair pair = pair();
        String v1 = encode(pair, payload(""));
        String v2 = encode(pair, payload(",\"licenseVersion\":2,\"paymentOrderId\":\"order-1\""));
        var keys = decoder.verificationKeys(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        var result = decoder.scan("您的许可证：" + v1 + "，请保存。\n```\n" + v2.replaceAll("(.{64})", "$1\n") + "\n```", keys);
        assertEquals(2, result.size());
        assertEquals(v1, result.get(0).key());
        assertEquals(v2, result.get(1).key());
        assertEquals("order-1", result.get(1).payload().path("paymentOrderId").asText());
    }

    @Test
    void rejectsPlainBase64TamperedKeysOtherSignersAndUnsupportedVersions() throws Exception {
        KeyPair pair = pair();
        var keys = List.of(pair.getPublic());
        assertTrue(decoder.scan(Base64.getEncoder().encodeToString(payload("").getBytes(StandardCharsets.UTF_8)), keys).isEmpty());
        assertTrue(decoder.scan(encode(pair(), payload("")), keys).isEmpty());
        assertTrue(decoder.scan(encode(pair, payload(",\"licenseVersion\":3")), keys).isEmpty());
        assertTrue(decoder.scan(encode(pair, payload("").replace("PeerBanHelper", "OtherProduct")), keys).isEmpty());
        byte[] tampered = Base64.getDecoder().decode(encode(pair, payload("")));
        tampered[10] ^= 1;
        assertTrue(decoder.scan(Base64.getEncoder().encodeToString(tampered), keys).isEmpty());
    }

    static KeyPair pair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        return generator.generateKeyPair();
    }

    @Test
    void skipsOversizedUntrustedBase64RunsWithoutExcessiveBacktracking() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () ->
                assertTrue(decoder.scan("A".repeat(200_000), List.of()).isEmpty()));
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () ->
                assertTrue(decoder.scan(("A".repeat(64) + "\n").repeat(10_000), List.of()).isEmpty()));
    }

    @Test
    void normalizesUrlSafeAndUnpaddedKeysAcrossRepeatedAppearances() throws Exception {
        var pair = pair();
        String key = encode(pair, payload(""));
        String alternate = key.replace('+', '-').replace('/', '_').replace("=", "");
        var result = decoder.scan("许可证：" + key + "，重复：" + alternate, List.of(pair.getPublic()));
        assertEquals(1, result.size());
        assertEquals(key, result.getFirst().key());
    }

    static String payload(String extra) {
        return "{\"verifyMagic\":\"PeerBanHelper\",\"licenseTo\":\"Test\",\"createAt\":1700000000000,\"expireAt\":4900000000000" + extra + "}";
    }

    static String encode(KeyPair pair, String payload) throws Exception {
        var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pair.getPrivate());
        var output = new ByteArrayOutputStream();
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        for (int offset = 0; offset < bytes.length; offset += 117) {
            output.write(cipher.doFinal(bytes, offset, Math.min(117, bytes.length - offset)));
        }
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }
}
