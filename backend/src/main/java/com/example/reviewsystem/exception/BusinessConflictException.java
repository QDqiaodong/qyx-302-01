package com.example.reviewsystem.exception;

/**
 * 请求与当前业务状态冲突（如对已公示作品改分、重复公示），映射 HTTP 409。
 */
public class BusinessConflictException extends RuntimeException {
    public BusinessConflictException(String message) {
        super(message);
    }
}
