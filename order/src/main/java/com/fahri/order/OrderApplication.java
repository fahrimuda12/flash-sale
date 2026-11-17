package com.fahri.order;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
@EnableCaching
@Slf4j
public class OrderApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(OrderApplication.class, args);

        Environment env = context.getEnvironment();

        String appName = env.getProperty("spring.application.name", "order");
        String port = env.getProperty("server.port", "8082");
        String[] activeProfiles = env.getActiveProfiles();
        String profiles = activeProfiles.length > 0 ? String.join(", ", activeProfiles) : "default";

        log.info("----------------------------------------------------------");
        log.info("Application '{}' is running!", appName);
        log.info("Port     : {}", port);
        log.info("Profiles : {}", profiles);
        log.info("----------------------------------------------------------");
    }

}
