package com.orangecode.agent.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class FileUploadResult {
    private String fileName;
    private String filePath;
    private Long fileSize;
}
