package com.cs.knowledge.eval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.knowledge.eval.dto.DislikeFeedbackRow;
import com.cs.knowledge.eval.entity.EvalCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评测用例 Mapper（路9 评估体系）。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper} 承担常规 CRUD；DISLIKE 回流是一条跨表只读采样
 * SQL——跨表 join 不放进 BaseMapper 的能力范围，且只 SELECT 不写其它模块归属的表
 * （feedback/chat_message/chat_reference 的写入权仍在 cs-qa），符合「跨模块表级只读」边界。</p>
 */
@Mapper
public interface EvalCaseMapper extends BaseMapper<EvalCase> {

    /**
     * DISLIKE 反馈回流采样行：点踩消息 + 其引用命中 + 引发该回答的 user 提问。
     *
     * <p>question 用相关子查询取「同会话、id 小于被点踩消息的最近一条 user 消息」——
     * 点踩落在 assistant 消息上，评测需要的是引发该回答的问题；找不到（如会话首条）
     * 返回 null，服务层跳过该消息并计数。</p>
     */
    @Select("SELECT f.id AS feedback_id, f.message_id, "
            + "(SELECT q.content FROM chat_message q "
            + "  WHERE q.conversation_id = m.conversation_id AND q.role = 'user' AND q.id < m.id "
            + "  ORDER BY q.id DESC LIMIT 1) AS question, "
            + "r.es_chunk_id, r.doc_id "
            + "FROM feedback f "
            + "JOIN chat_message m ON m.id = f.message_id "
            + "LEFT JOIN chat_reference r ON r.message_id = f.message_id "
            + "WHERE f.type = 'DISLIKE'")
    List<DislikeFeedbackRow> selectDislikeFeedbackRows();
}
