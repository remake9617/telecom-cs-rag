package com.cs.framework.exception;

import com.cs.framework.common.ErrorCode;
import com.cs.framework.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器 —— 将各类异常统一转换为标准响应体 {@link R}（并行开发公共契约）。
 *
 * <ul>
 *   <li>{@link BizException}：可预期业务错误，warn 级日志，返回其错误码</li>
 *   <li>{@link MethodArgumentNotValidException}：参数校验失败，返回 1001 + 具体字段信息</li>
 *   <li>{@link NoResourceFoundException}：未匹配的 /api/** 路径（客户端 URL 写错），返回 1004，
 *       不得落入兜底 1999（DEF-085：1999 暗示服务端内部错误，会误导排障方向）</li>
 *   <li>其他 {@link Exception}：未预期错误，error 级日志（含堆栈），对外统一返回 1999，不泄露内部细节</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        log.warn("参数校验失败: {}", detail);
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), detail);
    }

    /**
     * 未匹配的 /api/** 路径（Spring 6.1+ 对静态资源链未命中抛出）。
     *
     * <p>为什么单独处理（DEF-085）：请求写错的 URL 会穿透到 ResourceHttpRequestHandler 并抛
     * {@link NoResourceFoundException}，若无此分支会被下方兜底 catch 包成 1999「系统未预期异常」——
     * 客户端 URL 写错却被报成服务端内部错误，路6 容器实测中被它整整误导了一轮排障。
     * 返回 1004（资源不存在）并以 warn 级日志记录：客户端错误不应刷 ERROR 日志噪音。</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("请求路径不存在: {}", e.getResourcePath());
        return R.fail(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统未预期异常", e);
        return R.fail(ErrorCode.SYSTEM_ERROR);
    }
}
