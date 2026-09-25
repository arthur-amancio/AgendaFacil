package br.com.agendafacilpro.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {
    @Bean
    ZoneId applicationZoneId(@Value("${app.time-zone:America/Sao_Paulo}") String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone);
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        return zoneId;
    }

    @Bean
    Clock applicationClock(ZoneId applicationZoneId) {
        return Clock.system(applicationZoneId);
    }
}
