package app.hantteok.support;

import java.time.*;

public class TestClock extends Clock {
    public volatile Instant time = Instant.parse("2026-10-23T08:00:00Z");

    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
        return this;
    }

    public Instant instant() {
        return time;
    }
}
