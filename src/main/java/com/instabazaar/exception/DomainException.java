package com.instabazaar.exception;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {
    public final HttpStatus status;

    public DomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static DomainException invalid(String message) { return new DomainException(HttpStatus.BAD_REQUEST, message); }
    public static DomainException missing() { return new DomainException(HttpStatus.NOT_FOUND, "This page or item is not available."); }
    public static DomainException forbidden() { return new DomainException(HttpStatus.FORBIDDEN, "You do not have access to this action."); }
    public HttpStatus getStatus() { return status; }
}
