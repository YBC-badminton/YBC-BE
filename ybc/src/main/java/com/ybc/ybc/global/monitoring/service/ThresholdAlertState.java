package com.ybc.ybc.global.monitoring.service;

import java.time.Duration;
import java.time.Instant;

/** Missing data or a long polling gap cannot prove a sustained breach or recovery. */
final class ThresholdAlertState {
    enum Condition { BREACH, RECOVER, HOLD, UNKNOWN }
    enum Transition { WARNING, RECOVERY, NONE }
    private Instant breachSince;
    private Instant lastChecked;
    private boolean active;

    Transition evaluate(Condition condition, Instant now, Duration sustain, Duration maximumGap) {
        if (lastChecked != null && (now.isBefore(lastChecked) || Duration.between(lastChecked, now).compareTo(maximumGap) > 0)) {
            breachSince = null;
        }
        lastChecked = now;
        if (condition == Condition.BREACH) {
            if (active) return Transition.NONE;
            if (breachSince == null) breachSince = now;
            if (Duration.between(breachSince, now).compareTo(sustain) >= 0) {
                active = true;
                breachSince = null;
                return Transition.WARNING;
            }
        } else {
            breachSince = null;
            if (condition == Condition.RECOVER && active) {
                active = false;
                return Transition.RECOVERY;
            }
        }
        return Transition.NONE;
    }
}
