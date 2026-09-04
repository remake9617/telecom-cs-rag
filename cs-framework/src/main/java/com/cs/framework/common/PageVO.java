package com.cs.framework.common;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 统一分页响应结构（并行开发契约，见 CONVENTIONS 第 7 节、rest-api 通用约定）。
 *
 * <p>结构对齐契约：{@code { records:[], total, current, size }}。
 * 故意做成不依赖 MyBatis-Plus 的纯 POJO——cs-framework 是最底层模块，
 * 由各业务模块把自己的 {@code IPage} 转换为本结构后返回。</p>
 *
 * @param <T> 记录类型
 */
@Data
@AllArgsConstructor
public class PageVO<T> implements Serializable {

    /** 当前页记录 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码（1 起） */
    private long current;

    /** 每页大小 */
    private long size;

    public static <T> PageVO<T> of(List<T> records, long total, long current, long size) {
        return new PageVO<>(records, total, current, size);
    }
}
