package com.cs.framework.exception;

import com.cs.framework.common.ErrorCode;
import com.cs.framework.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器 —— 将各类异常统一转换为标准响应体 {@link R}（并行开发公共契约）。
 *
 * <ul>
 *   <li>{@link BizException}：可预期业务错误，warn 级日志，返回其错误码</li>
 *   <li>{@link MethodArgumentNotValidException}：参数校验失败，返回 1001 + 具体字段信息</li>
 *   <li>{@link NoResourceFoundException}：未匹配的 /api/** 路径（客户端 URL 写错），返回 1004，
 *       不得落入兜底 1999（DEF-085：1999 暗示服务端内部错误，会误导排障方向）</li>
 *   <li>{@link AsyncRequestNotUsableException}：客户端断开导致异步请求不可用（SSE 中断的
 *       必然伴生现象，路10 实测发现），降为 warn——响应已无法送达，按 1999 error 刷堆栈
 *       只会掩盖真正的服务端异常</li>
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

    /**
     * 客户端断开导致的异步请求不可用（路10 DEF-081 实测发现）：SSE 中途断开时，容器错误派发
     * 会把该异常送进异常解析器，若落到下方兜底会以 ERROR + 全堆栈刷屏——但连接已死、响应无法
     * 送达，这是断开的正常伴生现象而非服务端故障（QaService 的取消联动已另行 info 留痕）。
     * 返回值仅保持 R 形状，实际不会到达任何客户端。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public R<Void> handleAsyncNotUsable(AsyncRequestNotUsableException e) {
        log.warn("异步请求已不可用（客户端断开的伴生现象，不按系统异常处理）: {}", e.getMessage());
        return R.fail(ErrorCode.SYSTEM_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统未预期异常", e);
        return R.fail(ErrorCode.SYSTEM_ERROR);
    }
}
