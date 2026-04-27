package com.example.tokenservice.command;

/**
 * Token 命令类型枚举
 * 用于标识不同的 Token 操作类型
 */
public enum TokenCommandType {

    /**
     * 生成 Token
     */
    GENERATE,

    /**
     * 验证 Token
     */
    VALIDATE,

    /**
     * 续签 Token
     */
    RENEW,

    /**
     * 作废 Token
     */
    INVALIDATE,

    /**
     * 获取 Token 信息
     */
    GET_INFO
}
