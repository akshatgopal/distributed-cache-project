package com.distributed.distributed_cache_project.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Configure standard serialization features.
        // Disable writing dates as timestamps (human-readable format).
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Enable pretty printing for debugging (optional)
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Crucially: Remove any potentially interfering modules or mixins
        // if they were added automatically by a classpath conflict.
        // This is a defensive step. If this doesn't help, the conflict is deeper.
        // mapper.findAndRegisterModules(); // This usually finds all default modules, be careful if a bad one exists

        // If you suspect a specific module is causing issues, you might try to explicitly
        // unregister it, but this is advanced and usually not necessary if it's a default module.

        return mapper;
    }
}
