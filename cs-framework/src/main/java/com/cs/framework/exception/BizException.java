package com.cs.framework.exception;

import com.cs.framework.common.ErrorCode;
import lombok.Getter;

/**
 * 业务异常 —— 可预期的业务错误，由 {@link GlobalExceptionHandler} 统一转为 {@code R.fail}。
 *
 * <p>业务代码中通过 {@code throw new BizException(ErrorCode.XXX)} 抛出，
 * 避免在每处手动组装错误响应。</p>
 */
@Getter
public class BizException extends RuntimeException {

    /** 错误码 */
    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
