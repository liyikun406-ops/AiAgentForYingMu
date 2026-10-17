package com.orangecode.agent.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@ConfigurationProperties("document.chunk")
public class DocumentChunkConfig {

    private int maxSize ;
    private int overlap;

}
