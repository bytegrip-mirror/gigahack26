package com.pistonpunk.gigahack26;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

@Modulithic
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class Gigahack26Application {

    static void main(String[] args) {
        SpringApplication.run(Gigahack26Application.class, args);
    }
}
