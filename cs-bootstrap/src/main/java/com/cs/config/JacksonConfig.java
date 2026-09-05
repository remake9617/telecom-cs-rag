package com.cs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局日期时间格式配置。
 *
 * <p><b>为什么不用 {@code spring.jackson.date-format}：</b>该配置项只对
 * {@code java.util.Date} 生效；本项目 15 个时间字段全部使用 JSR-310 类型
 * （{@link LocalDateTime} / {@link LocalDate}），{@code date-format} 配了等于没配。
 * JSR-310 的序列化/反序列化必须通过注册 {@code JavaTimeModule} 并为每种类型
 * 显式指定 {@code DateTimeFormatter} 来控制输出格式。</p>
 *
 * <p><b>格式约定：</b></p>
 * <ul>
 *   <li>{@code LocalDateTime} → {@code yyyy-MM-dd HH:mm:ss}（如 2026-09-04 14:30:00）</li>
 *   <li>{@code LocalDate} → {@code yyyy-MM-dd}（如 2026-09-04）</li>
 * </ul>
 * <p><b>严禁将 datetime 的 pattern 套到 {@code LocalDate}：</b>否则 {@code TrendVO.date}
 * 等字段会输出 {@code 2026-09-04 00:00:00}，污染前端折线图 X 轴。</p>
 *
 * <p><b>Deserializer 安全性核查：</b>当前全仓所有 {@code @RequestBody} DTO/Request 类
 * （ChatRequest / RegisterRequest / LoginRequest / FeedbackRequest / TicketCreateRequest /
 * TicketReplyRequest）均不含 {@link LocalDateTime} 或 {@link LocalDate} 字段，
 * 故 Deserializer 仅为防御性注册——将来若新增请求体携带日期字段，该注册可确保
 * {@code yyyy-MM-dd HH:mm:ss} 格式自动解析，无需额外处理。</p>
 *
 * <p><b>无副作用确认：</b>MyBatis-Plus 走 JDBC TypeHandler 与 Jackson 独立；
 * {@code StatsMapper} 用 MySQL 层 {@code DATE_FORMAT} 返回 {@code String}；
 * {@code R.timestamp} 是 {@code long}——均不受本配置影响。</p>
 *
 * @see JavaTimeModule
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 注册 Jackson2ObjectMapperBuilderCustomizer，为 JSR-310 类型指定全局序列化/反序列化格式。
     *
     * <p>使用 Customizer 而非直接暴露 {@link ObjectMapper} bean，是为了不覆盖
     * Spring Boot 的自动配置（如 {@code WRITE_DATES_AS_TIMESTAMPS=false} 等默认行为），
     * 仅叠加我们需要的格式化器。</p>
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsr310DateTimeCustomizer() {
        return builder -> {
            // LocalDateTime: yyyy-MM-dd HH:mm:ss
            builder.serializerByType(LocalDateTime.class,
                    new LocalDateTimeSerializer(DATETIME_FORMATTER));
            builder.deserializerByType(LocalDateTime.class,
                    new LocalDateTimeDeserializer(DATETIME_FORMATTER));

            // LocalDate: yyyy-MM-dd（严禁套用 datetime pattern）
            builder.serializerByType(LocalDate.class,
                    new LocalDateSerializer(DATE_FORMATTER));
            builder.deserializerByType(LocalDate.class,
                    new LocalDateDeserializer(DATE_FORMATTER));
        };
    }
}
