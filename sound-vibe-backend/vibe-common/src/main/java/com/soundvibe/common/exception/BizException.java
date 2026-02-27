package com.soundvibe.common.exception;

import com.soundvibe.common.result.ResultCode;
import lombok.Getter;

/**
 * 自定义业务异常
 * 用于在业务逻辑中抛出可预期的异常情况
 * 
 * @author SoundVibe Team
 */
@Getter
public class BizException extends RuntimeException {

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 构造器 - 使用状态码枚举
     *
     * @param resultCode 状态码枚举
     */
    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }

    /**
     * 构造器 - 使用状态码枚举和自定义消息
     *
     * @param resultCode 状态码枚举
     * @param message    自定义消息
     */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
        this.message = message;
    }

    /**
     * 构造器 - 直接指定错误码和消息
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    /**
     * 构造器 - 仅自定义消息（使用默认业务错误码）
     *
     * @param message 错误消息
     */
    public BizException(String message) {
        super(message);
        this.code = ResultCode.BIZ_ERROR.getCode();
        this.message = message;
    }
}
