package com.example.reviewsystem.exception;

/**
 * 请求参数不满足业务前置条件（如作品尚未凑齐评委分），映射 HTTP 400。
 */
public class BusinessValidationException extends RuntimeException {
    public BusinessValidationException(String message) {
        super(message);
    }
}
