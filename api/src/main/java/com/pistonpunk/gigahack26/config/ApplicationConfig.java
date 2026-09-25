package com.pistonpunk.gigahack26.config;

import com.pistonpunk.gigahack26.service.ClassifierProperties;
import com.pistonpunk.gigahack26.service.CorpusProperties;
import com.pistonpunk.gigahack26.service.RefreshProperties;
import com.pistonpunk.gigahack26.service.ScoringProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({ClassifierProperties.class, ScoringProperties.class, CorpusProperties.class,
        RefreshProperties.class})
public class ApplicationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
