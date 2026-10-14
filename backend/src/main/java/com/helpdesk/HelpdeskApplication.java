package com.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO) // stable {content, page} JSON for list endpoints
public class HelpdeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelpdeskApplication.class, args);
    }
}
