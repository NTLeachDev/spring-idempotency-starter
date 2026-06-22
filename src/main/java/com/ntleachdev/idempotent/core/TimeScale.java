package com.ntleachdev.idempotent.core;

import java.time.Duration;

public enum TimeScale {
    MINUTE,
    HOUR,
    DAY,
    WEEK;

    public Duration toDuration(int value) {
        return switch (this) {
            case MINUTE -> Duration.ofMinutes(value);
            case HOUR -> Duration.ofHours(value);
            case DAY -> Duration.ofDays(value);
            case WEEK -> Duration.ofDays((long) value * 7);
        };
    }
}
