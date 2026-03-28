package com.blog.exception;

/**
 * 限流异常类
 */
public class RateLimiterException extends RuntimeException {
    public RateLimiterException(String message) {
        super(message);
    }
}
