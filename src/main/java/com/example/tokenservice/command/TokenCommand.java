package com.example.tokenservice.command;

/**
 * Token 操作命令接口
 * 命令模式：封装 Token 操作的执行逻辑
 * 
 * 职责：
 * 1. 定义操作执行的标准接口
 * 2. 每个命令封装一个完整的操作逻辑
 * 3. 支持撤销（可选）
 * 
 * @param <R> 执行结果类型
 */
public interface TokenCommand<R> {

    /**
     * 执行命令
     * @return 执行结果
     */
    R execute();

    /**
     * 获取命令类型
     */
    TokenCommandType getType();
}
