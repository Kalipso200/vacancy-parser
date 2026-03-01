package com.example.vacancyparser.model;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

public class ParserTask {
    private final String taskId;
    private final String url;
    private final String source;
    private final LocalDateTime startTime;
    private CompletableFuture<Void> future;
    private ParserStatus status;
    private int parsedCount;
    private String errorMessage;

    public enum ParserStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    public ParserTask(String url, String source) {
        this.taskId = java.util.UUID.randomUUID().toString();
        this.url = url;
        this.source = source;
        this.startTime = LocalDateTime.now();
        this.status = ParserStatus.PENDING;
        this.parsedCount = 0;
    }

    public String getTaskId() { return taskId; }
    public String getUrl() { return url; }
    public String getSource() { return source; }
    public LocalDateTime getStartTime() { return startTime; }
    public ParserStatus getStatus() { return status; }
    public int getParsedCount() { return parsedCount; }
    public String getErrorMessage() { return errorMessage; }
    public CompletableFuture<Void> getFuture() { return future; }

    public void setStatus(ParserStatus status) { this.status = status; }
    public void setParsedCount(int parsedCount) { this.parsedCount = parsedCount; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setFuture(CompletableFuture<Void> future) { this.future = future; }

    public boolean isCompleted() { return status == ParserStatus.COMPLETED; }
    public boolean isFailed() { return status == ParserStatus.FAILED; }
    public boolean isRunning() { return status == ParserStatus.RUNNING; }
}