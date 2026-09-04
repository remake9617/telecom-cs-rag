package com.cs.ingestion.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * URL 内容抓取 —— 用 Jsoup 拉取网页并提取正文文本（剔除脚本/样式/导航等噪音）。
 *
 * <p>对应 D4「网页 URL 抓取」。返回「标题 + 正文」，供后续分块与向量化。</p>
 */
@Slf4j
@Component
public class UrlContentFetcher {

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; cs-ingestion-bot/1.0)";
    private static final int TIMEOUT_MS = 15000;

    /**
     * 抓取 URL 正文。
     *
     * @param url 目标网页地址
     * @return 标题 + 正文纯文本
     */
    public String fetch(String url) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();
        // 去除无关标签，尽量只保留正文
        doc.select("script, style, noscript, nav, footer, header, aside, iframe").remove();
        String title = doc.title();
        String body = doc.body() != null ? doc.body().text() : "";
        log.debug("抓取 URL[{}] 得到正文 {} 字符", url, body.length());
        return (title != null && !title.isBlank() ? title + "\n" : "") + body;
    }
}
