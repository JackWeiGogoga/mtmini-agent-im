package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应封装类
 *
 * @param <T> 响应数据类型
 * @author JackWei
 * @since 2026-01-21
 */
@Data
public class ResultVO<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 消息
     */
    private String msg;

    /**
     * 数据
     */
    private T data;

    public ResultVO() {
    }

    public ResultVO(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public ResultVO(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 成功响应
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ResultVO<T> success() {
        return new ResultVO<>(200, "success");
    }

    /**
     * 成功响应（带数据）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> ResultVO<T> success(T data) {
        return new ResultVO<>(200, "success", data);
    }

    /**
     * 成功响应（带消息和数据）
     *
     * @param msg  消息
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> ResultVO<T> success(String msg, T data) {
        return new ResultVO<>(200, msg, data);
    }

    /**
     * 失败响应
     *
     * @param msg 错误消息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> ResultVO<T> error(String msg) {
        return new ResultVO<>(500, msg);
    }

    /**
     * 失败响应（带状态码）
     *
     * @param code 状态码
     * @param msg  错误消息
     * @param <T>  数据类型
     * @return 失败响应
     */
    public static <T> ResultVO<T> error(Integer code, String msg) {
        return new ResultVO<>(code, msg);
    }

    /**
     * 参数错误响应
     *
     * @param msg 错误消息
     * @param <T> 数据类型
     * @return 参数错误响应
     */
    public static <T> ResultVO<T> badRequest(String msg) {
        return new ResultVO<>(400, msg);
    }

    /**
     * 未找到资源响应
     *
     * @param msg 错误消息
     * @param <T> 数据类型
     * @return 未找到资源响应
     */
    public static <T> ResultVO<T> notFound(String msg) {
        return new ResultVO<>(404, msg);
    }

    /**
     * 冲突响应
     *
     * @param msg 错误消息
     * @param <T> 数据类型
     * @return 冲突响应
     */
    public static <T> ResultVO<T> conflict(String msg) {
        return new ResultVO<>(409, msg);
    }

    /**
     * 未授权响应
     *
     * @param msg 错误消息
     * @param <T> 数据类型
     * @return 未授权响应
     */
    public static <T> ResultVO<T> unauthorized(String msg) {
        return new ResultVO<>(401, msg);
    }

    /**
     * 无权限响应
     *
     * @param msg 错误消息
     * @param <T> 数据类型
     * @return 无权限响应
     */
    public static <T> ResultVO<T> forbidden(String msg) {
        return new ResultVO<>(403, msg);
    }

    /**
     * 判断是否成功
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return this.code != null && this.code == 200;
    }
}
