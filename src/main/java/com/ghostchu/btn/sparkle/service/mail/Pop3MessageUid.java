package com.ghostchu.btn.sparkle.service.mail;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.UIDFolder;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the stable identifier used to remember which POP3 messages were already ingested.
 *
 * <p>POP3 servers identify messages through the optional UIDL extension, which returns string
 * identifiers. The matching folder therefore does not implement {@link UIDFolder}, whose numeric
 * identifiers target stores such as IMAP. The provider-specific UIDL accessor is used when it is
 * available; servers that do not offer UIDL fall back to a fingerprint of the message headers.</p>
 */
final class Pop3MessageUid {
    private static final ConcurrentHashMap<Class<?>, Optional<Method>> UIDL_METHODS =
            new ConcurrentHashMap<>();

    private Pop3MessageUid() {
    }

    static String of(Folder folder, Message message) {
        String serverUid = serverUid(folder, message);
        return serverUid != null ? serverUid : fingerprint(message);
    }

    private static @Nullable String serverUid(Folder folder, Message message) {
        if (folder instanceof UIDFolder uidFolder) {
            try {
                long uid = uidFolder.getUID(message);
                if (uid >= 0) {
                    return "uid:" + uid;
                }
            } catch (MessagingException ignored) {
                // Try the provider-specific UIDL accessor below.
            }
        }
        Optional<Method> method = uidlMethod(folder.getClass());
        if (method.isEmpty()) {
            return null;
        }
        try {
            Object value = method.get().invoke(folder, message);
            if (value instanceof String text && !text.isBlank()) {
                return "uidl:" + text.trim();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // A provider without UIDL support is handled by the header fingerprint.
        }
        return null;
    }

    private static Optional<Method> uidlMethod(Class<?> folderType) {
        return UIDL_METHODS.computeIfAbsent(folderType, type -> {
            try {
                Method method = type.getMethod("getUID", Message.class);
                return method.getReturnType() == String.class
                        ? Optional.of(method)
                        : Optional.empty();
            } catch (NoSuchMethodException | SecurityException exception) {
                return Optional.empty();
            }
        });
    }

    private static String fingerprint(Message message) {
        try {
            String messageId = firstHeader(message, "Message-ID");
            if (messageId != null) {
                return "message-id:" + sha256(messageId);
            }
            String value = String.join("\u0000",
                    nullToEmpty(firstHeader(message, "Date")),
                    nullToEmpty(firstHeader(message, "From")),
                    nullToEmpty(firstHeader(message, "To")),
                    nullToEmpty(firstHeader(message, "Subject")),
                    String.valueOf(message.getSize()));
            return "sha256:" + sha256(value);
        } catch (Exception exception) {
            return "number:" + message.getMessageNumber();
        }
    }

    private static @Nullable String firstHeader(Message message, String name)
            throws MessagingException {
        String[] values = message.getHeader(name);
        if (values == null || values.length == 0 || values[0] == null || values[0].isBlank()) {
            return null;
        }
        return values[0].trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
