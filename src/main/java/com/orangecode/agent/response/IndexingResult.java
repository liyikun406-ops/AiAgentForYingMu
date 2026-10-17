package com.orangecode.agent.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
public class IndexingResult {

    @Setter
    private boolean success;
    @Setter
    private String directoryPath;
    @Setter
    private int totalFiles;
    private int successCount;
    private int failCount;
    @Setter
    private LocalDateTime startTime;
    @Setter
    private LocalDateTime endTime;
    @Setter
    private String errorMessage;
    private Map<String, String> failedFiles = new HashMap<>();

    public void incrementSuccessCount() {
        this.successCount++;
    }

    public void incrementFailCount() {
        this.failCount++;
    }

    public void addFailedFile(String filePath, String error) {
        this.failedFiles.put(filePath, error);
    }
}
