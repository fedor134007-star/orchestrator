package com.example.persons_service.exception;

import java.net.URI;

/**
 * Типы проблем RFC 9457, публикуемые сервисом.
 */
public final class ProblemTypes {

    public static final String BASE = "https://example.org/problems/";

    public static final URI VALIDATION_ERROR = URI.create(BASE + "validation-error");
    public static final URI USER_NOT_FOUND = URI.create(BASE + "user-not-found");
    public static final URI RESOURCE_NOT_FOUND = URI.create(BASE + "resource-not-found");
    public static final URI EMAIL_ALREADY_EXISTS = URI.create(BASE + "email-already-exists");
    public static final URI UNKNOWN_COUNTRY = URI.create(BASE + "unknown-country");
    public static final URI CONCURRENT_MODIFICATION = URI.create(BASE + "concurrent-modification");
    public static final URI INTERNAL_ERROR = URI.create(BASE + "internal-error");

    private ProblemTypes() {
    }
}
