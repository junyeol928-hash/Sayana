package com.sayana.dojo.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** 「今日」の判定に使う時計。テストでは固定の時計に差し替える。 */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
