CREATE TABLE IF NOT EXISTS work (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(50) NOT NULL COMMENT '品类：绘画/手作/设计',
    theme VARCHAR(200) COMMENT '创作题材',
    creator_name VARCHAR(100) NOT NULL COMMENT '创作者姓名',
    creator_phone VARCHAR(20) COMMENT '创作者电话',
    creator_email VARCHAR(100) COMMENT '创作者邮箱',
    work_name VARCHAR(200) NOT NULL COMMENT '作品名称',
    description TEXT COMMENT '作品描述',
    image_url VARCHAR(500) COMMENT '作品图片URL',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态：PENDING/APPROVED/PENDING_SCORES(待齐分)/GRADED',
    total_score DECIMAL(5,2) COMMENT '综合得分（已公示作品等于公示快照，永不回溯）',
    grade VARCHAR(10) COMMENT '等级：S/A/B/C（已公示作品钉死在快照口径）',
    is_qualified BOOLEAN DEFAULT FALSE COMMENT '是否合格',
    published BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已对外公示',
    publication_batch_id BIGINT COMMENT '所属公示批次ID',
    published_total_score DECIMAL(5,2) COMMENT '公示当时的综合分快照',
    published_grade VARCHAR(10) COMMENT '公示当时的等级快照',
    published_is_qualified BOOLEAN COMMENT '公示当时的合格性快照',
    published_at DATETIME COMMENT '公示时间',
    piece_count INT COMMENT '件数（作者申报，点交时核对；已点交后改动将作废点交并撤下展墙/凭证）',
    handover_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '点交状态：NOT_STARTED 未点交/IN_PROGRESS 点交中/COMPLETED 已点交',
    certificate_status VARCHAR(20) NOT NULL DEFAULT 'NOT_ISSUED' COMMENT '参展凭证：NOT_ISSUED 未发放/ISSUED 已发放/REVOKED 已撤销',
    certificate_no VARCHAR(64) COMMENT '参展凭证编号',
    certificate_issued_at DATETIME COMMENT '凭证发放时间',
    certificate_revoked_at DATETIME COMMENT '凭证撤销时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category),
    INDEX idx_status (status),
    INDEX idx_grade (grade),
    INDEX idx_published (published),
    INDEX idx_publication_batch (publication_batch_id),
    INDEX idx_work_handover_status (handover_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投稿作品表';

-- 现场点交单：一件作品同时只有一张；三要素（件数、完好情况、接收人）缺一则不成立。
-- 未点交 = 没有点交单行；点交失败或已点交后件数被改 → 整单删除，作品退回未点交。
CREATE TABLE IF NOT EXISTS handover (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_id BIGINT NOT NULL COMMENT '作品ID',
    status VARCHAR(20) NOT NULL COMMENT 'IN_PROGRESS 点交中 / COMPLETED 已点交',
    condition_status VARCHAR(100) COMMENT '完好情况（三要素之一）',
    receiver VARCHAR(100) COMMENT '接收人（三要素之一）',
    piece_count_snapshot INT COMMENT '点交完成时的件数快照（三要素之一，取自作品件数）',
    started_at DATETIME COMMENT '开始点交时间',
    completed_at DATETIME COMMENT '点交完成时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_handover_work (work_id),
    INDEX idx_handover_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现场点交单（三要素缺一则不成立）';

CREATE TABLE IF NOT EXISTS score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_id BIGINT NOT NULL COMMENT '作品ID',
    judge_id BIGINT NOT NULL COMMENT '评委ID',
    judge_name VARCHAR(100) NOT NULL COMMENT '评委姓名',
    dimension VARCHAR(50) NOT NULL COMMENT '打分维度：creativity/completion/commercial_potential/craftsmanship',
    score_value DECIMAL(5,2) NOT NULL COMMENT '该维度得分(0-100)；缺维度不是零分，而是没有这一行',
    scored_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '打分时间',
    UNIQUE KEY uk_main_score_work_dimension (work_id, dimension),
    INDEX idx_main_score_work_id (work_id),
    INDEX idx_main_score_judge_id (judge_id),
    INDEX idx_main_score_dimension (dimension),
    INDEX idx_main_score_scored_at (scored_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评委维度分流水（一件作品一个维度一条有效分）';

CREATE TABLE IF NOT EXISTS weight_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dimension VARCHAR(50) NOT NULL COMMENT '维度名称',
    weight DECIMAL(5,2) NOT NULL COMMENT '权重(0-1)',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dimension (dimension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打分权重配置表';

CREATE TABLE IF NOT EXISTS threshold_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    qualified_score DECIMAL(5,2) NOT NULL DEFAULT 60 COMMENT '合格线分值',
    extreme_threshold DECIMAL(5,2) NOT NULL DEFAULT 20 COMMENT '极端分阈值',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阈值配置表';

-- 对外公示批次：一批作品在同一个事务里按同一套权重/合格线快照锁定，提交后不可变
CREATE TABLE IF NOT EXISTS publication_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_name VARCHAR(200) COMMENT '批次名称',
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED/REVOKED，不存在部分公示状态',
    creativity_weight DECIMAL(5,2) NOT NULL COMMENT '创意权重快照',
    completion_weight DECIMAL(5,2) NOT NULL COMMENT '完成度权重快照',
    commercial_potential_weight DECIMAL(5,2) NOT NULL COMMENT '商业潜力权重快照',
    craftsmanship_weight DECIMAL(5,2) NOT NULL COMMENT '工艺权重快照',
    qualified_score DECIMAL(5,2) NOT NULL COMMENT '合格线快照',
    extreme_threshold DECIMAL(5,2) NOT NULL COMMENT '极端分阈值快照',
    work_count INT NOT NULL DEFAULT 0 COMMENT '批内作品数',
    published_at DATETIME NOT NULL COMMENT '公示时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pb_status (status),
    INDEX idx_pb_published_at (published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对外公示批次（配置快照）';

INSERT IGNORE INTO weight_config (dimension, weight) VALUES
('creativity', 0.30),
('completion', 0.25),
('commercial_potential', 0.25),
('craftsmanship', 0.20);

INSERT IGNORE INTO threshold_config (qualified_score, extreme_threshold) VALUES (60, 20);