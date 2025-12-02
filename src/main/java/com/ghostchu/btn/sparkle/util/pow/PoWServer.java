package com.ghostchu.btn.sparkle.util.pow;

import lombok.Getter;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * PoW 无交互验证码（服务端）
 */
public class PoWServer {
    @Getter
    private final int difficultyBits; // e.g., 20 bits = leading 2.5 bytes of zero
    @Getter
    private final String algorithm;

    public PoWServer(int difficultyBits, @NotNull String algorithm) {
        this.difficultyBits = difficultyBits;
        this.algorithm = algorithm;
    }

    @SneakyThrows(NoSuchAlgorithmException.class)
    public boolean verify(byte[] challenge, byte[] nonce)  {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        digest.update(challenge);
        digest.update(nonce);
        byte[] hash = digest.digest();
        return hasLeadingZeroBits(hash, difficultyBits);
    }

    private boolean hasLeadingZeroBits(byte[] hash, int bits) {
        int fullBytes = bits / 8;
        int remainingBits = bits % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (hash[i] != 0) return false;
        }
        if (remainingBits > 0) {
            int mask = 0xFF << (8 - remainingBits);
            return (hash[fullBytes] & mask) == 0;
        }
        return true;
    }
}