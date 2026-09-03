package com.cs.framework.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体 —— 全站 REST 接口的标准返回结构（并行开发公共契约）。
 *
 * <p>约定：{@code code=0} 表示成功；非 0 为错误码（见 {@link ErrorCode}）。
 * 所有 Controller 一律返回 {@code R<T>}，前端按此结构统一解析。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
public class R<T> implements Serializable {

    /** 状态码：0=成功，非0=错误码 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 服务器时间戳（毫秒） */
    private long timestamp;

    /** 链路追踪 ID（阶段二全链路 Trace 填充，MVP 预留） */
    private String traceId;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ErrorCode.SUCCESS.getCode();
        r.message = ErrorCode.SUCCESS.getMessage();
        r.data = data;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return fail(errorCode.getCode(), message);
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.timestamp = System.currentTimeMillis();
        return r;
    }
}
