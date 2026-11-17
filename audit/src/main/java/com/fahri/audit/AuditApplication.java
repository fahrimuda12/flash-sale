package com.fahri.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
@Slf4j
public class AuditApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(AuditApplication.class, args);
        Environment env = context.getEnvironment();

        String appName = env.getProperty("spring.application.name", "audit");
        String port = env.getProperty("server.port", "8083");
        String[] activeProfiles = env.getActiveProfiles();
        String profiles = activeProfiles.length > 0 ? String.join(", ", activeProfiles) : "default";

        log.info("==========================================================");
        log.info("Application '{}' is running!", appName);
        log.info("Port     : {}", port);
        log.info("Profiles : {}", profiles);
        log.info("Database : MongoDB (NoSQL Document Store)");
        log.info("Streaming: Kafka Event-Driven Consumer");
        log.info("==========================================================");
    }

}
