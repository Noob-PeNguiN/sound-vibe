package com.soundvibe.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应包装类
 * 用于封装所有 API 响应结果
 *
 * @param <T> 数据类型
 * @author SoundVibe Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    /**
     * 响应状态码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 成功响应（带数据）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return Result 对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功响应（无数据）
     *
     * @param <T> 数据类型
     * @return Result 对象
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 失败响应（使用枚举）
     *
     * @param resultCode 状态码枚举
     * @param <T>        数据类型
     * @return Result 对象
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 失败响应（自定义消息）
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return Result 对象
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(ResultCode.SYSTEM_ERROR.getCode(), message, null);
    }

    /**
     * 失败响应（自定义状态码和消息）
     *
     * @param code    状态码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return Result 对象
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
