package com.ghostchu.btn.sparkle.service.mail;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class Pop3MessageUidTest {
    /**
     * Mirrors the POP3 folder contract, where UIDL identifiers are strings and the folder
     * therefore does not implement {@link UIDFolder}. It is public so that Mockito can add it
     * to a mocked folder.
     */
    public interface StringUidFolder {
        String getUID(Message message);
    }

    @Test
    void usesUidlFromFolderThatDoesNotImplementUidFolder() throws Exception {
        MimeMessage message = message("<reply@example.com>", "Re: hello");
        Folder folder = folderWithUidl("0000000001");

        assertFalse(folder instanceof UIDFolder);
        assertEquals("uidl:0000000001", Pop3MessageUid.of(folder, message));
    }

    @Test
    void prefersNumericUidWhenFolderImplementsUidFolder() throws Exception {
        Folder folder = mock(Folder.class, withSettings().extraInterfaces(UIDFolder.class));
        MimeMessage message = message("<numeric@example.com>", "hello");
        when(((UIDFolder) folder).getUID(message)).thenReturn(42L);

        assertEquals("uid:42", Pop3MessageUid.of(folder, message));
    }

    @Test
    void fallsBackToHeaderFingerprintWhenUidlIsUnavailable() throws Exception {
        Folder folder = mock(Folder.class);
        String first = Pop3MessageUid.of(folder, message("<same@example.com>", "Re: hello"));
        String second = Pop3MessageUid.of(folder, message("<same@example.com>", "Re: hello"));

        assertEquals(first, second);
        assertNotEquals(first,
                Pop3MessageUid.of(folder, message("<other@example.com>", "Re: hello")));
    }

    @Test
    void fallsBackToHeaderFingerprintWhenProviderHasNoUidlSupport() throws Exception {
        Folder folder = folderWithUidl(null);

        String uid = Pop3MessageUid.of(folder, message("<fallback@example.com>", "Re: hello"));

        assertTrue(uid.startsWith("message-id:"), uid);
    }

    @Test
    void usesMessageNumberWhenHeadersCannotBeRead() throws Exception {
        Folder folder = mock(Folder.class);
        Message message = mock(Message.class);
        when(message.getHeader("Message-ID")).thenThrow(new MessagingException("unreadable"));
        when(message.getMessageNumber()).thenReturn(7);

        assertEquals("number:7", Pop3MessageUid.of(folder, message));
    }

    private Folder folderWithUidl(String uid) {
        Folder folder = mock(Folder.class, withSettings().extraInterfaces(StringUidFolder.class));
        when(((StringUidFolder) folder).getUID(any(Message.class))).thenReturn(uid);
        return folder;
    }

    private MimeMessage message(String messageId, String subject) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setHeader("Message-ID", messageId);
        message.setSubject(subject);
        return message;
    }
}
