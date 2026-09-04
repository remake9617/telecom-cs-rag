package com.cs.ingestion.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文档解析器 —— 基于 Apache Tika，一个依赖统一解析 PDF/Word/Excel/MD/TXT/HTML 为纯文本。
 *
 * <p>对应 DESIGN 4.2 中 cs-ingestion 的解析环节、D4「文档解析全家桶」。
 * Tika 内部封装了 PDFBox/POI 等，无需逐个格式手写解析。</p>
 */
@Slf4j
@Component
public class TikaDocumentParser {

    private final Tika tika = new Tika();

    /**
     * 解析文档输入流为纯文本。
     *
     * @param input    文档输入流
     * @param filename 文件名（用于日志与类型辅助判断）
     * @return 抽取的正文文本（可能为空串）
     */
    public String parse(InputStream input, String filename) throws IOException {
        try {
            // parseToString 自动探测文档类型并抽取正文；MVP 语料为中小文档，够用
            String text = tika.parseToString(input);
            log.debug("解析文档[{}]得到 {} 字符", filename, text == null ? 0 : text.length());
            return text == null ? "" : text;
        } catch (TikaException e) {
            throw new IOException("文档解析失败: " + filename, e);
        }
    }
}
