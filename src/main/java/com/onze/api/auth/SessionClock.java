package com.onze.api.auth;

import java.time.Instant;

/** Session time can be controlled independently of match scheduling in tests. */
@FunctionalInterface
public interface SessionClock {
    Instant instant();
}
