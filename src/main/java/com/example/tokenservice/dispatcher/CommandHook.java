package com.example.tokenservice.dispatcher;

import com.example.tokenservice.command.TokenCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

public interface CommandHook {

    default boolean beforeCommand(CommandContext context, TokenCommand<?> command) {
        return true;
    }

    default void afterCommand(CommandContext context, TokenCommand<?> command, Object result) {
    }

    default void onError(CommandContext context, TokenCommand<?> command, Throwable throwable) {
    }
}

@Component
class LoggingCommandHook implements CommandHook {

    private static final Logger log = LoggerFactory.getLogger(LoggingCommandHook.class);

    @Override
    public boolean beforeCommand(CommandContext context, TokenCommand<?> command) {
        log.info("执行命令前 - ID: {}, 类型: {}, 优先级: {}", 
                context.getCommandId(), command.getType(), context.getPriority());
        return true;
    }

    @Override
    public void afterCommand(CommandContext context, TokenCommand<?> command, Object result) {
        log.info("执行命令后 - ID: {}, 类型: {}, 耗时: {}ms, 成功: {}",
                context.getCommandId(), command.getType(), 
                context.getExecutionTimeMillis(), context.isSuccess());
    }

    @Override
    public void onError(CommandContext context, TokenCommand<?> command, Throwable throwable) {
        log.error("命令执行错误 - ID: {}, 类型: {}, 错误: {}",
                context.getCommandId(), command.getType(), throwable.getMessage(), throwable);
    }
}

@Component
class MetricsCommandHook implements CommandHook {

    private static final Logger log = LoggerFactory.getLogger(MetricsCommandHook.class);

    private final List<CommandContext> completedCommands = new ArrayList<>();

    @Override
    public void afterCommand(CommandContext context, TokenCommand<?> command, Object result) {
        synchronized (completedCommands) {
            completedCommands.add(context);
        }
        log.debug("命令执行统计 - 类型: {}, 耗时: {}ms", 
                command.getType(), context.getExecutionTimeMillis());
    }

    public List<CommandContext> getCompletedCommands() {
        synchronized (completedCommands) {
            return new ArrayList<>(completedCommands);
        }
    }

    public void clear() {
        synchronized (completedCommands) {
            completedCommands.clear();
        }
    }
}
