package com.pistonpunk.gigahack26;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.pistonpunk.gigahack26.repository.jpa")
@EnableMongoRepositories(basePackages = "com.pistonpunk.gigahack26.repository.mongo")
public class Gigahack26Application {

    public static void main(String[] args) {
        SpringApplication.run(Gigahack26Application.class, args);
    }
}
