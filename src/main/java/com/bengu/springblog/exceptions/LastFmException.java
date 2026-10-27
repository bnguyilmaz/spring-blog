package com.bengu.springblog.exceptions;

public class LastFmException extends RuntimeException {
    public LastFmException() {
        super("Last.fm şu anda yanıt veremiyor. Lütfen biraz sonra tekrar dene.");
    }
}
