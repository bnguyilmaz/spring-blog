package com.bengu.springblog.exceptions;

public class GoogleAccountAlreadyLinkedException extends RuntimeException {

    public GoogleAccountAlreadyLinkedException() {
        super("This Google account is already linked to a user.");
    }
}