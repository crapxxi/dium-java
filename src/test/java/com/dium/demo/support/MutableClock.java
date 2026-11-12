package com.dium.demo.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Часы для тестов: время двигается только вручную через {@link #advance(Duration)}. */
public class MutableClock extends Clock {
    private Instant now;

    public MutableClock(Instant start) {
        this.now = start;
    }

    public MutableClock() {
        this(Instant.parse("2026-01-01T12:00:00Z"));
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void setInstant(Instant instant) {
        now = instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
