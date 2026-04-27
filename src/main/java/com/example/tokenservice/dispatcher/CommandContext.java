package com.example.tokenservice.dispatcher;

import com.example.tokenservice.command.TokenCommand;
import com.example.tokenservice.command.TokenCommandType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CommandContext {

    private final String commandId;
    private final TokenCommandType commandType;
    private final int priority;
    private final LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private boolean success;
    private String errorMessage;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    private static final AtomicLong counter = new AtomicLong(0);

    public CommandContext(TokenCommandType commandType) {
        this(commandType, 0);
    }

    public CommandContext(TokenCommandType commandType, int priority) {
        this.commandId = "CMD-" + System.currentTimeMillis() + "-" + counter.incrementAndGet();
        this.commandType = commandType;
        this.priority = priority;
        this.createdAt = LocalDateTime.now();
        this.success = false;
    }

    public String getCommandId() {
        return commandId;
    }

    public TokenCommandType getCommandType() {
        return commandType;
    }

    public int getPriority() {
        return priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    public Map<String, Object> getAttributes() {
        return new ConcurrentHashMap<>(attributes);
    }

    public long getExecutionTimeMillis() {
        if (startedAt == null || completedAt == null) {
            return -1;
        }
        return java.time.Duration.between(startedAt, completedAt).toMillis();
    }
}
