package dev.ownhost;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * State for one published world. The token is deliberately never logged or
 * persisted, and it expires when the host stops or its lease ends.
 */
public final class HostSession {
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(8);
    private static final int MAX_ATTEMPTS_PER_WINDOW = 12;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(1);
    private final SecureRandom random = new SecureRandom();
    private final AtomicInteger attempts = new AtomicInteger();
    private volatile Instant attemptWindowStarted = Instant.now();
    private volatile State state = State.STOPPED;
    private volatile String token;
    private volatile Instant expiresAt;

    public synchronized String start() {
        token = randomToken();
        expiresAt = Instant.now().plus(TOKEN_LIFETIME);
        state = State.RUNNING;
        attempts.set(0);
        attemptWindowStarted = Instant.now();
        return token;
    }

    public synchronized void stop() {
        state = State.STOPPED;
        token = null;
        expiresAt = null;
        attempts.set(0);
    }

    public boolean isRunning() {
        if (state != State.RUNNING || expiresAt == null) {
            return false;
        }
        if (Instant.now().isAfter(expiresAt)) {
            stop();
            return false;
        }
        return true;
    }

    public boolean accept(String candidate) {
        if (!isRunning() || candidate == null || !allowAttempt()) {
            return false;
        }
        return Objects.equals(token, candidate);
    }

    public String tokenForHostDisplay() {
        return isRunning() ? token : null;
    }

    private boolean allowAttempt() {
        Instant now = Instant.now();
        synchronized (this) {
            if (now.isAfter(attemptWindowStarted.plus(ATTEMPT_WINDOW))) {
                attemptWindowStarted = now;
                attempts.set(0);
            }
            return attempts.incrementAndGet() <= MAX_ATTEMPTS_PER_WINDOW;
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public enum State {
        RUNNING,
        STOPPED
    }
}
