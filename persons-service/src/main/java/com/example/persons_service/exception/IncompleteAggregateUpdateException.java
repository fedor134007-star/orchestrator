package com.example.persons_service.exception;

/**
 * Частичное обновление агрегата оставило бы его в неполном состоянии
 * (например, PATCH создаёт адрес без обязательных полей) → 400.
 */
public class IncompleteAggregateUpdateException extends RuntimeException {

    public IncompleteAggregateUpdateException(String message) {
        super(message);
    }
}
