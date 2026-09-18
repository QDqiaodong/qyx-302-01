package com.example.reviewsystem.service;

import com.example.reviewsystem.entity.Score;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 评委维度分按月分表流水：只做与主事务同生共死的流水写入（主事务回滚时插入一并回滚）。
 * 综合分/统计的读路径统一走 JPA 主表，保证缺维作品绝不会被流水侧的旧口径垫进统计。
 */
@Service
@RequiredArgsConstructor
public class ScoreShardingService {

    private final JdbcTemplate jdbcTemplate;
    private static final String TABLE_PREFIX = "score_";

    public String getTableName(LocalDateTime dateTime) {
        return TABLE_PREFIX + dateTime.format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    public void ensureTableExists(String month) {
        String tableName = TABLE_PREFIX + month;
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "work_id BIGINT NOT NULL COMMENT '作品ID', " +
                "judge_id BIGINT NOT NULL COMMENT '评委ID', " +
                "judge_name VARCHAR(100) NOT NULL COMMENT '评委姓名', " +
                "dimension VARCHAR(50) NOT NULL COMMENT '打分维度', " +
                "score_value DECIMAL(5,2) NOT NULL COMMENT '该维度得分(0-100)', " +
                "scored_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '打分时间', " +
                "INDEX idx_work_id (work_id), " +
                "INDEX idx_judge_id (judge_id), " +
                "INDEX idx_dimension (dimension), " +
                "INDEX idx_scored_at (scored_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评委维度分流水-" + month + "'";
        jdbcTemplate.execute(sql);
    }

    public void saveScore(Score score) {
        LocalDateTime scoredAt = score.getScoredAt() != null ? score.getScoredAt() : LocalDateTime.now();
        String month = scoredAt.format(DateTimeFormatter.ofPattern("yyyyMM"));
        ensureTableExists(month);

        String sql = "INSERT INTO " + TABLE_PREFIX + month +
                " (work_id, judge_id, judge_name, dimension, score_value, scored_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                score.getWorkId(),
                score.getJudgeId(),
                score.getJudgeName(),
                score.getDimension(),
                score.getValue(),
                scoredAt
        );
    }
}
