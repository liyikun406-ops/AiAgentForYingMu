package com.orangecode.agent.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadConfig {

    private String path;
    private String allowedExtensions;

}
