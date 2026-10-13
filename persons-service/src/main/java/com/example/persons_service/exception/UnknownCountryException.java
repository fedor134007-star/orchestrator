package com.example.persons_service.exception;

/** Страна не найдена в справочнике или коды alpha-2/alpha-3 противоречат друг другу → 400. */
public class UnknownCountryException extends RuntimeException {

    public UnknownCountryException(String message) {
        super(message);
    }
}
