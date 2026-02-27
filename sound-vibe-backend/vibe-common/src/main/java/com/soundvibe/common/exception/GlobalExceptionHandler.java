package com.soundvibe.common.exception;

import com.soundvibe.common.result.Result;
import com.soundvibe.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理应用中抛出的各类异常，并返回标准的 Result 格式
 *
 * @author SoundVibe Team
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理自定义业务异常
     *
     * @param e BizException
     * @return Result
     */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        log.error("业务异常: code={}, message={}", e.getCode(), e.getMessage(), e);
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常 (MethodArgumentNotValidException)
     * 用于 @Valid 或 @Validated 注解触发的异常
     *
     * @param e MethodArgumentNotValidException
     * @return Result
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.error("参数校验异常: {}", errorMsg, e);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), errorMsg);
    }

    /**
     * 处理参数绑定异常 (BindException)
     *
     * @param e BindException
     * @return Result
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        String errorMsg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.error("参数绑定异常: {}", errorMsg, e);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), errorMsg);
    }

    /**
     * 处理非法参数异常
     *
     * @param e IllegalArgumentException
     * @return Result
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("非法参数异常: {}", e.getMessage(), e);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), e.getMessage());
    }

    /**
     * 处理所有未捕获的异常
     * 这是兜底的异常处理器
     *
     * @param e Exception
     * @return Result
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}
