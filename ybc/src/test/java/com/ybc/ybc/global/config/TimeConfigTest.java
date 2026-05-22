package com.ybc.ybc.global.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TimeConfigTest {

    @Test
    void systemClockUsesAsiaSeoulZone() {
        TimeConfig timeConfig = new TimeConfig();

        Clock clock = timeConfig.systemClock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }
}
