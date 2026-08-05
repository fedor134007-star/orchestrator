package com.example.individuals_api.utils;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class AuthMetrics {

    private final Counter registrationTotal;
    private final Counter registrationSuccess;
    private final Counter registrationError;
    private final Counter loginTotal;
    private final Counter loginError;
    private final Counter refreshTotal;
    private final Timer registrationTimer;
    private final Timer loginTimer;
    private final Timer refreshTimer;

    public AuthMetrics(MeterRegistry registry) {
        registrationTotal = Counter.builder("auth.registration.total")
                .description("Total registrations").register(registry);
        registrationSuccess = Counter.builder("auth.registration.success")
                .description("Successful registrations").register(registry);
        registrationError = Counter.builder("auth.registration.error")
                .description("Failed registrations").register(registry);
        loginTotal = Counter.builder("auth.login.total")
                .description("Total logins").register(registry);
        loginError = Counter.builder("auth.login.error")
                .description("Failed logins").register(registry);
        refreshTotal = Counter.builder("auth.refresh.total")
                .description("Total token refreshes").register(registry);
        registrationTimer = Timer.builder("auth.registration.time")
                .description("Registration duration").register(registry);
        loginTimer = Timer.builder("auth.login.time")
                .description("Login duration").register(registry);
        refreshTimer = Timer.builder("auth.refresh.time")
                .description("Token refresh duration").register(registry);
    }

    public void recordRegistration(boolean success, long durationMs) {
        registrationTotal.increment();
        if (success) {
            registrationSuccess.increment();
        } else {
            registrationError.increment();
        }
        registrationTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordLogin(boolean success, long durationMs) {
        loginTotal.increment();
        if (!success) loginError.increment();
        loginTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordRefresh(long durationMs) {
        refreshTotal.increment();
        refreshTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}