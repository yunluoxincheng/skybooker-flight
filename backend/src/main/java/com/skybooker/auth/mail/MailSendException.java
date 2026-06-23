package com.skybooker.auth.mail;

public class MailSendException extends RuntimeException {

    public MailSendException(String message) {
        super(message);
    }
}
