package com.soundvibe.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一状态码枚举
 * 定义系统中所有可能的响应状态码
 *
 * @author SoundVibe Team
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    /**
     * 成功
     */
    SUCCESS(200, "成功"),

    /**
     * 参数错误
     */
    PARAM_ERROR(400, "参数错误"),

    /**
     * 未授权
     */
    UNAUTHORIZED(401, "未授权"),

    /**
     * 禁止访问
     */
    FORBIDDEN(403, "禁止访问"),

    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 系统异常
     */
    SYSTEM_ERROR(500, "系统异常"),

    /**
     * 业务逻辑错误
     */
    BIZ_ERROR(600, "业务逻辑错误"),

    /**
     * 用户名已存在
     */
    USER_ALREADY_EXISTS(1001, "用户名已存在"),

    /**
     * 用户不存在
     */
    USER_NOT_FOUND(1002, "用户不存在"),

    /**
     * 密码错误
     */
    PASSWORD_ERROR(1003, "密码错误"),

    // ==================== Asset 模块 (2xxx) ====================

    /**
     * 文件为空
     */
    FILE_IS_EMPTY(2001, "上传文件不能为空"),

    /**
     * 文件类型不支持
     */
    FILE_TYPE_NOT_SUPPORTED(2002, "不支持的文件类型"),

    /**
     * 文件大小超限
     */
    FILE_SIZE_EXCEEDED(2003, "文件大小超出限制"),

    /**
     * 文件上传失败
     */
    FILE_UPLOAD_FAILED(2004, "文件上传失败"),

    /**
     * 资产不存在
     */
    ASSET_NOT_FOUND(2005, "资产不存在"),

    // ==================== Catalog 模块 (3xxx) ====================

    /**
     * 作品不存在
     */
    TRACK_NOT_FOUND(3001, "作品不存在"),

    /**
     * 作品标题不能为空
     */
    TRACK_TITLE_REQUIRED(3002, "作品标题不能为空"),

    /**
     * 已经购买过此作品
     */
    ALREADY_PURCHASED(3003, "已经购买过此作品"),

    /**
     * 不能购买自己的作品
     */
    CANNOT_BUY_OWN(3004, "不能购买自己发布的作品"),

    /**
     * 商品已售罄
     */
    OUT_OF_STOCK(3005, "该作品已售罄"),

    // ==================== Order 模块 (4xxx) ====================

    /**
     * 购物车为空
     */
    CART_EMPTY(4001, "购物车为空"),

    /**
     * 订单不存在
     */
    ORDER_NOT_FOUND(4002, "订单不存在"),

    /**
     * 订单状态不允许操作
     */
    ORDER_STATUS_INVALID(4003, "订单状态不允许此操作"),

    /**
     * 作品正在被购买（分布式锁冲突）
     */
    TRACK_BEING_PURCHASED(4004, "作品正在被其他用户购买，请稍后再试"),

    /**
     * 价格不一致
     */
    PRICE_CHANGED(4005, "作品价格已变更，请刷新购物车");

    /**
     * 状态码
     */
    private final Integer code;

    /**
     * 状态消息
     */
    private final String message;
}
