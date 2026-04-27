package com.example.tokenservice.dispatcher;

import com.example.tokenservice.command.TokenCommand;
import com.example.tokenservice.command.TokenCommandType;
import com.example.tokenservice.config.TokenProperties;
import com.example.tokenservice.dto.TokenInfo;
import com.example.tokenservice.factory.TokenOperationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

@Component
public class TokenOperationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TokenOperationDispatcher.class);

    private final TokenOperationFactory operationFactory;
    private final TokenProperties tokenProperties;
    private final List<CommandHook> commandHooks;

    private final PriorityBlockingQueue<PrioritizedCommand<?>> priorityQueue;
    private final ConcurrentHashMap<String, CommandContext> contextMap;

    public TokenOperationDispatcher(TokenOperationFactory operationFactory,
                                     TokenProperties tokenProperties,
                                     List<CommandHook> commandHooks) {
        this.operationFactory = operationFactory;
        this.tokenProperties = tokenProperties;
        this.commandHooks = commandHooks != null ? commandHooks : Collections.emptyList();
        this.priorityQueue = new PriorityBlockingQueue<>();
        this.contextMap = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void init() {
        log.info("Token 操作调度器初始化完成");
        log.info("已注册 {} 个命令钩子", commandHooks.size());
        log.info("异步执行: {}", tokenProperties.getScheduler().isAsyncEnabled());
    }

    public String generateToken(String userId, String subject, Long expireSeconds) {
        return generateToken(userId, subject, expireSeconds, 0);
    }

    public String generateToken(String userId, String subject, Long expireSeconds, int priority) {
        log.debug("调度生成 Token - userId: {}, priority: {}", userId, priority);

        TokenCommand<String> command = operationFactory.createGenerateCommand(userId, subject, expireSeconds);
        return executeWithHooks(command, priority);
    }

    public boolean validateToken(String tokenValue) {
        return validateToken(tokenValue, 0);
    }

    public boolean validateToken(String tokenValue, int priority) {
        log.debug("调度验证 Token - priority: {}", priority);

        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            log.warn("验证 Token 失败：Token 为空");
            return false;
        }

        TokenCommand<Boolean> command = operationFactory.createValidateCommand(tokenValue);
        Boolean result = executeWithHooks(command, priority);
        return result != null ? result : false;
    }

    public String renewToken(String oldTokenValue, Long newExpireSeconds, boolean invalidateOldToken) {
        return renewToken(oldTokenValue, newExpireSeconds, invalidateOldToken, 0);
    }

    public String renewToken(String oldTokenValue, Long newExpireSeconds, boolean invalidateOldToken, int priority) {
        log.debug("调度续签 Token - priority: {}", priority);

        if (oldTokenValue == null || oldTokenValue.trim().isEmpty()) {
            log.warn("续签 Token 失败：原 Token 为空");
            return null;
        }

        TokenCommand<String> command = operationFactory.createRenewCommand(
                oldTokenValue, newExpireSeconds, invalidateOldToken);
        return executeWithHooks(command, priority);
    }

    public boolean invalidateToken(String tokenValue) {
        return invalidateToken(tokenValue, 0);
    }

    public boolean invalidateToken(String tokenValue, int priority) {
        log.debug("调度作废 Token - priority: {}", priority);

        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            log.warn("作废 Token 失败：Token 为空");
            return false;
        }

        TokenCommand<Boolean> command = operationFactory.createInvalidateCommand(tokenValue);
        Boolean result = executeWithHooks(command, priority);
        return result != null ? result : false;
    }

    @SuppressWarnings("unchecked")
    public Optional<TokenInfo> getTokenInfo(String tokenValue) {
        return getTokenInfo(tokenValue, 0);
    }

    public Optional<TokenInfo> getTokenInfo(String tokenValue, int priority) {
        log.debug("调度获取 Token 信息 - priority: {}", priority);

        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            log.warn("获取 Token 信息失败：Token 为空");
            return Optional.empty();
        }

        TokenCommand<?> command = operationFactory.createGetInfoCommand(tokenValue);
        Object result = executeWithHooks(command, priority);

        if (result instanceof Optional) {
            return (Optional<TokenInfo>) result;
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public <R> R executeCommand(TokenCommandType type, Object... params) {
        log.debug("调度执行命令: {}", type);

        TokenCommand<?> command = operationFactory.createCommand(type, params);
        return (R) executeWithHooks(command, 0);
    }

    @Async("tokenTaskExecutor")
    public CompletableFuture<String> generateTokenAsync(String userId, String subject, Long expireSeconds) {
        return generateTokenAsync(userId, subject, expireSeconds, 0);
    }

    @Async("tokenTaskExecutor")
    public CompletableFuture<String> generateTokenAsync(String userId, String subject, Long expireSeconds, int priority) {
        log.info("异步调度生成 Token - userId: {}, priority: {}", userId, priority);
        String result = generateToken(userId, subject, expireSeconds, priority);
        return CompletableFuture.completedFuture(result);
    }

    @Async("tokenTaskExecutor")
    public CompletableFuture<Boolean> validateTokenAsync(String tokenValue) {
        return validateTokenAsync(tokenValue, 0);
    }

    @Async("tokenTaskExecutor")
    public CompletableFuture<Boolean> validateTokenAsync(String tokenValue, int priority) {
        log.info("异步调度验证 Token - priority: {}", priority);
        boolean result = validateToken(tokenValue, priority);
        return CompletableFuture.completedFuture(result);
    }

    @Async("tokenTaskExecutor")
    public CompletableFuture<Boolean> invalidateTokenAsync(String tokenValue) {
        return invalidateTokenAsync(tokenValue, 0);
    }

    @Async("tokenTaskExecutor")
    public CompletableFuture<Boolean> invalidateTokenAsync(String tokenValue, int priority) {
        log.info("异步调度作废 Token - priority: {}", priority);
        boolean result = invalidateToken(tokenValue, priority);
        return CompletableFuture.completedFuture(result);
    }

    @Async("tokenTaskExecutor")
    public CompletableFuture<String> renewTokenAsync(String oldTokenValue, Long newExpireSeconds, boolean invalidateOldToken) {
        return renewTokenAsync(oldTokenValue, newExpireSeconds, invalidateOldToken, 0);
    }

    @Async("tokenTaskExecutor")
    public CompletableFuture<String> renewTokenAsync(String oldTokenValue, Long newExpireSeconds, boolean invalidateOldToken, int priority) {
        log.info("异步调度续签 Token - priority: {}", priority);
        String result = renewToken(oldTokenValue, newExpireSeconds, invalidateOldToken, priority);
        return CompletableFuture.completedFuture(result);
    }

    @Async("tokenTaskExecutor")
    public <R> CompletableFuture<R> executeCommandAsync(TokenCommand<R> command) {
        return executeCommandAsync(command, 0);
    }

    @Async("tokenTaskExecutor")
    public <R> CompletableFuture<R> executeCommandAsync(TokenCommand<R> command, int priority) {
        log.info("异步调度执行命令 - 类型: {}, priority: {}", command.getType(), priority);
        R result = executeWithHooks(command, priority);
        return CompletableFuture.completedFuture(result);
    }

    public void submitWithPriority(TokenCommand<?> command, int priority) {
        log.debug("提交带优先级的命令 - 类型: {}, priority: {}", command.getType(), priority);
        priorityQueue.offer(new PrioritizedCommand<>(command, priority));
    }

    public void processPriorityQueue() {
        log.info("开始处理优先级队列，队列大小: {}", priorityQueue.size());
        while (!priorityQueue.isEmpty()) {
            PrioritizedCommand<?> prioritized = priorityQueue.poll();
            if (prioritized != null) {
                log.debug("从队列执行命令 - 类型: {}, priority: {}",
                        prioritized.getCommand().getType(), prioritized.getPriority());
                executeWithHooks(prioritized.getCommand(), prioritized.getPriority());
            }
        }
        log.info("优先级队列处理完成");
    }

    public int getQueueSize() {
        return priorityQueue.size();
    }

    public CommandContext getContext(String commandId) {
        return contextMap.get(commandId);
    }

    private <R> R executeWithHooks(TokenCommand<R> command, int priority) {
        CommandContext context = new CommandContext(command.getType(), priority);
        contextMap.put(context.getCommandId(), context);

        try {
            boolean proceed = executeBeforeHooks(context, command);
            if (!proceed) {
                log.warn("命令被钩子拦截 - ID: {}, 类型: {}", context.getCommandId(), command.getType());
                return null;
            }

            context.setStartedAt(LocalDateTime.now());
            R result = command.execute();
            context.setSuccess(true);
            context.setCompletedAt(LocalDateTime.now());

            executeAfterHooks(context, command, result);
            return result;

        } catch (Exception e) {
            context.setSuccess(false);
            context.setErrorMessage(e.getMessage());
            context.setCompletedAt(LocalDateTime.now());

            executeErrorHooks(context, command, e);
            log.error("命令执行异常 - ID: {}, 类型: {}", context.getCommandId(), command.getType(), e);
            return null;
        }
    }

    private boolean executeBeforeHooks(CommandContext context, TokenCommand<?> command) {
        for (CommandHook hook : commandHooks) {
            try {
                boolean proceed = hook.beforeCommand(context, command);
                if (!proceed) {
                    log.warn("钩子拦截命令 - 钩子: {}", hook.getClass().getSimpleName());
                    return false;
                }
            } catch (Exception e) {
                log.error("钩子执行异常 - 类型: beforeCommand", e);
            }
        }
        return true;
    }

    private void executeAfterHooks(CommandContext context, TokenCommand<?> command, Object result) {
        for (CommandHook hook : commandHooks) {
            try {
                hook.afterCommand(context, command, result);
            } catch (Exception e) {
                log.error("钩子执行异常 - 类型: afterCommand", e);
            }
        }
    }

    private void executeErrorHooks(CommandContext context, TokenCommand<?> command, Throwable throwable) {
        for (CommandHook hook : commandHooks) {
            try {
                hook.onError(context, command, throwable);
            } catch (Exception e) {
                log.error("钩子执行异常 - 类型: onError", e);
            }
        }
    }
}
