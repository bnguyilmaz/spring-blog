package com.bengu.springblog.exceptions;

public class EmailAlreadyExistsException
        extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("A user with this email already exists: " + email);
    }
}