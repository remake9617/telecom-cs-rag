package com.cs.framework.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码枚举（并行开发公共契约）。
 *
 * <p>分段规则，各模块在自己的区间内扩展，避免并行开发时错误码冲突：</p>
 * <ul>
 *   <li>0        成功</li>
 *   <li>1xxx     通用/框架（参数、认证、权限、系统）</li>
 *   <li>2xxx     知识库与入库（cs-knowledge / cs-ingestion）</li>
 *   <li>3xxx     检索与问答（cs-qa）</li>
 *   <li>4xxx     工单（cs-ticket）</li>
 *   <li>5xxx     系统/统计（cs-system / cs-stats）</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "成功"),

    // ---------- 1xxx 通用 ----------
    PARAM_ERROR(1001, "参数校验失败"),
    UNAUTHORIZED(1002, "未认证或登录已过期"),
    FORBIDDEN(1003, "无权访问该资源"),
    NOT_FOUND(1004, "资源不存在"),
    RATE_LIMITED(1005, "请求过于频繁，请稍后再试"),
    SYSTEM_ERROR(1999, "系统繁忙，请稍后再试"),

    // ---------- 2xxx 知识库 / 入库 ----------
    KB_NOT_FOUND(2001, "知识库不存在"),
    DOC_NOT_FOUND(2002, "文档不存在"),
    DOC_PARSE_FAIL(2003, "文档解析失败"),
    DOC_UNSUPPORTED_TYPE(2004, "不支持的文档类型"),
    EMBEDDING_FAIL(2005, "向量化失败"),

    // ---------- 3xxx 检索 / 问答 ----------
    RETRIEVAL_EMPTY(3001, "未检索到相关内容"),
    MODEL_CALL_FAIL(3002, "模型调用失败"),
    CONVERSATION_NOT_FOUND(3003, "会话不存在"),

    // ---------- 4xxx 工单 ----------
    TICKET_NOT_FOUND(4001, "工单不存在"),
    TICKET_STATE_ILLEGAL(4002, "工单状态不允许该操作"),

    // ---------- 5xxx 系统 / 统计 ----------
    USER_NOT_FOUND(5001, "用户不存在"),
    USERNAME_EXISTS(5002, "用户名已存在"),
    PASSWORD_ERROR(5003, "用户名或密码错误");

    private final int code;
    private final String message;
}
