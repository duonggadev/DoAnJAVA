package vn.edu.solop;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Bounded, per-process attempt windows for a single app instance. */
@Component
class AttemptLimits {
    private static final int MAX_KEYS = 20_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;

    AttemptLimits(Clock clock) {
        this.clock = clock;
    }

    synchronized boolean reserve(String key, int limit, long durationMillis) {
        long now = clock.millis();
        Window window = windows.get(key);
        if (window == null || window.until <= now) {
            if (windows.size() >= MAX_KEYS) windows.values().removeIf(item -> item.until <= now);
            if (windows.size() >= MAX_KEYS && !windows.containsKey(key)) return false;
            window = new Window(now + durationMillis);
            windows.put(key, window);
        }
        if (window.count >= limit) return false;
        window.count++;
        return true;
    }

    synchronized void refund(String key) {
        Window window = windows.get(key);
        if (window != null && --window.count <= 0) windows.remove(key);
    }

    private static final class Window {
        final long until;
        int count;

        Window(long until) {
            this.until = until;
        }
    }
}