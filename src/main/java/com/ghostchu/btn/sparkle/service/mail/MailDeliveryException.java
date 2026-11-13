package com.ghostchu.btn.sparkle.service.mail;

public class MailDeliveryException extends Exception {
    public MailDeliveryException(String message) {
        super(message);
    }

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
