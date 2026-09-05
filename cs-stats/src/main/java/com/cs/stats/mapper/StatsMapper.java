package com.cs.stats.mapper;

import com.cs.stats.dto.DailyCountRow;
import com.cs.stats.dto.HotQuestionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 统计聚合 Mapper。
 *
 * <p><b>设计说明</b>：统计是典型的读侧场景，直接对 chat_message / ticket / feedback
 * 以及 kb_knowledge_base / kb_document / sys_user 做只读聚合 SQL，而不依赖
 * cs-qa / cs-ticket / cs-knowledge / cs-system 的 Service 或 Mapper——cs-stats 按依赖契约
 * 只依赖 cs-framework（CONVENTIONS 第 2 节、DECISIONS D24），表级只读是数仓/统计模块
 * 的常规边界。写入仍由各归属模块负责，本 Mapper 只 SELECT。</p>
 */
@Mapper
public interface StatsMapper {

    /** 咨询量：用户提问消息数 */
    @Select("SELECT COUNT(*) FROM chat_message WHERE role = 'user'")
    long countUserMessages();

    /** 按类型的反馈数（LIKE / DISLIKE） */
    @Select("SELECT COUNT(*) FROM feedback WHERE type = #{type}")
    long countFeedbackByType(@Param("type") String type);

    /** 工单总数 */
    @Select("SELECT COUNT(*) FROM ticket")
    long countTickets();

    /** 待处理工单数（OPEN） */
    @Select("SELECT COUNT(*) FROM ticket WHERE status = 'OPEN'")
    long countOpenTickets();

    /**
     * 知识库数：kb_knowledge_base 行数。
     *
     * <p>按 D24 约束，cs-stats 只读直查表，不依赖 cs-knowledge / cs-ingestion / cs-system
     * 的任何 Service 或 Mapper，以保持模块边界与依赖方向（CONVENTIONS 第 2 节：
     * cs-stats 只依赖 cs-framework）。下方两个 count 方法同理。</p>
     */
    @Select("SELECT COUNT(*) FROM kb_knowledge_base")
    long countKnowledgeBases();

    /** 文档数：kb_document 行数（含入库中/失败文档，即全部已登记文档） */
    @Select("SELECT COUNT(*) FROM kb_document")
    long countDocuments();

    /** 用户数：sys_user 行数（含禁用账号，反映注册规模） */
    @Select("SELECT COUNT(*) FROM sys_user")
    long countUsers();

    /** 热点问题：用户消息按内容分组 TopN */
    @Select("SELECT content AS question, COUNT(*) AS count "
            + "FROM chat_message WHERE role = 'user' "
            + "GROUP BY content ORDER BY count DESC LIMIT #{limit}")
    List<HotQuestionVO> selectHotQuestions(@Param("limit") int limit);

    /** 每日提问数（近 N 天，含今天） */
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS date, COUNT(*) AS cnt "
            + "FROM chat_message WHERE role = 'user' AND created_at >= #{startTime} "
            + "GROUP BY date ORDER BY date")
    List<DailyCountRow> countDailyAsks(@Param("startTime") String startTime);

    /** 每日点赞数（解决口径的日粒度体现） */
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS date, COUNT(*) AS cnt "
            + "FROM feedback WHERE type = 'LIKE' AND created_at >= #{startTime} "
            + "GROUP BY date ORDER BY date")
    List<DailyCountRow> countDailyLikes(@Param("startTime") String startTime);
}
