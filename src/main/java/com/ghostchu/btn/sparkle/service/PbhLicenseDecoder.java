package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class PbhLicenseDecoder {
    // PeerBanHelper LicenseParser.OFFICIAL_PUBLIC_KEY.
    static final String OFFICIAL_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCHxgRTk+Zx/pkN8rpK+Lbr1/f1meapIRDJIgBiSfFy4xdbmDF8wE9PJhdM+3peThz9dJQlt6dkeduIVp65rGS9oZdj7gO5YKtUCDir4NgGQGe1p2C41Xv6RiOXObLmF+ubAJILsimwtDyJT8IysEh9hgaZWnvRXT8JX9wB0Ti2rwIDAQAB";
    private static final Pattern CANDIDATE = Pattern.compile("(?<![A-Za-z0-9+/_-])[A-Za-z0-9+/_-]{160,32768}={0,2}(?![A-Za-z0-9+/_=-])");
    private static final Pattern CHUNK = Pattern.compile("(?<![A-Za-z0-9+/_-])[A-Za-z0-9+/_-]{4,32768}={0,2}(?![A-Za-z0-9+/_=-])");
    private final ObjectMapper json = new ObjectMapper();

    public List<PublicKey> verificationKeys(String privateKey) throws Exception {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        List<PublicKey> keys = new ArrayList<>();
        keys.add(factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(OFFICIAL_KEY))));
        if (privateKey != null && !privateKey.isBlank()) {
            String encoded = privateKey.replaceAll("-----[^-]+-----", "").replaceAll("\\s+", "");
            RSAPrivateCrtKey key = (RSAPrivateCrtKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
            keys.add(factory.generatePublic(new RSAPublicKeySpec(key.getModulus(), key.getPublicExponent())));
        }
        return List.copyOf(keys);
    }

    public List<Decoded> scan(String text, List<PublicKey> keys) {
        var result = new LinkedHashMap<String, Decoded>();
        var matcher = CANDIDATE.matcher(text);
        while (matcher.find()) accept(matcher.group(), keys, result);
        // Iterate line fragments rather than using a recursive multiline regex on untrusted content.
        var chunks = CHUNK.matcher(text);
        StringBuilder joined = new StringBuilder();
        int previousEnd = -1;
        while (chunks.find()) {
            String fragment = chunks.group();
            boolean continuation = previousEnd >= 0 && chunks.start() - previousEnd <= 256
                    && text.substring(previousEnd, chunks.start()).matches("[\r\n]+[ \t]*")
                    && joined.length() >= 32 && joined.length() + fragment.length() <= 32768;
            if (!continuation) joined.setLength(0);
            joined.append(fragment);
            if (continuation && joined.length() >= 160) accept(joined.toString(), keys, result);
            previousEnd = chunks.end();
        }
        return List.copyOf(result.values());
    }

    private void accept(String raw, List<PublicKey> keys, Map<String, Decoded> result) {
        String candidate = raw.replace('-', '+').replace('_', '/');
        try {
            byte[] encrypted = Base64.getDecoder().decode(candidate);
            for (PublicKey key : keys) {
                try {
                    int block = (((RSAPublicKey) key).getModulus().bitLength() + 7) / 8;
                    if (encrypted.length == 0 || encrypted.length % block != 0) continue;
                    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                    cipher.init(Cipher.DECRYPT_MODE, key);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    for (int offset = 0; offset < encrypted.length; offset += block) {
                        output.write(cipher.doFinal(encrypted, offset, block));
                    }
                    JsonNode payload = json.readTree(output.toString(StandardCharsets.UTF_8));
                    if (!"PeerBanHelper".equals(payload.path("verifyMagic").asText())) continue;
                    if (payload.has("licenseVersion") && !List.of(1, 2).contains(payload.path("licenseVersion").asInt())) continue;
                    long created = payload.path("createAt").asLong(-1);
                    long expires = payload.path("expireAt").asLong(-1);
                    if (created < 0 || expires <= created || payload.path("licenseTo").asText().isBlank()) continue;
                    long start = payload.path("startAt").asLong(created);
                    String canonical = Base64.getEncoder().encodeToString(encrypted);
                    result.putIfAbsent(canonical, new Decoded(canonical, payload,
                            date(created), date(start < 0 ? created : start), date(expires)));
                    break;
                } catch (Exception ignored) {
                    // Invalid candidates in untrusted chat content are expected.
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private OffsetDateTime date(long value) {
        return Instant.ofEpochMilli(value).atOffset(ZoneOffset.UTC);
    }

    public record Decoded(String key, JsonNode payload, OffsetDateTime createdAt,
                          OffsetDateTime startAt, OffsetDateTime expireAt) {
        @Override
        public String toString() { return "Decoded[redacted]"; }
    }
}
