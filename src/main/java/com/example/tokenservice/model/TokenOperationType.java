package com.example.tokenservice.model;

/**
 * Token操作类型枚举
 * 用于区分不同的Token操作，以便进行统计
 */
public enum TokenOperationType {

    /**
     * Token生成操作
     */
    GENERATE("生成"),

    /**
     * Token验证操作
     */
    VALIDATE("验证"),

    /**
     * Token续签操作
     */
    RENEW("续签"),

    /**
     * Token作废操作
     */
    INVALIDATE("作废");

    private final String description;

    TokenOperationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
