-- ============================================
-- SoundVibe Catalog 模块数据库表结构
-- 数据库: sound_vibe_db
-- ============================================

-- 1. 作品表（可交易商品）
-- 设计说明：
--   作品是用户发布的可交易内容，展示在市场主页供其他用户浏览和购买
--   支持两种作品类型：
--     SINGLE（单曲）— 包含 1 个文件，使用 file_id + file_type 直接关联
--     PACK（合集/采样包）— 包含多个文件，文件列表存储在 track_files 表
--   支持两种文件类型：AUDIO（音频采样、drum loop、melody loop 等）和 MIDI（MIDI 序列）
--   不设置固定的 bpm / musical_key / genre 字段，由用户通过 tags 和 description 自由描述
CREATE TABLE IF NOT EXISTS `tracks` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `title`         VARCHAR(200)    NOT NULL                 COMMENT '作品标题',
    `description`   TEXT            DEFAULT NULL             COMMENT '作品描述（建议用户在此说明 BPM、调式、风格等信息）',
    `track_type`    VARCHAR(10)     NOT NULL DEFAULT 'SINGLE' COMMENT '作品类型: SINGLE=单曲, PACK=合集/采样包',
    `producer_id`   BIGINT          NOT NULL                 COMMENT '发布者 ID（关联 users.id）',
    `cover_id`      BIGINT          DEFAULT NULL             COMMENT '封面资产 ID（关联 assets.id, IMAGE 类型）',
    `file_id`       BIGINT          DEFAULT NULL             COMMENT '文件资产 ID（仅 SINGLE 类型，关联 assets.id）',
    `file_type`     VARCHAR(20)     DEFAULT NULL             COMMENT '文件类型（仅 SINGLE 类型）: AUDIO, MIDI',
    `file_count`    INT             NOT NULL DEFAULT 1       COMMENT '文件数量（SINGLE=1, PACK=合集中的实际文件数量）',
    `price`         DECIMAL(10, 2)  DEFAULT NULL             COMMENT '价格（null 或 0 表示免费）',
    `status`        TINYINT         NOT NULL DEFAULT 0       COMMENT '状态: 0=草稿/私密, 1=已上架/公开',
    `visibility`    TINYINT         NOT NULL DEFAULT 2       COMMENT '可见范围: 0=仅自己, 1=指定用户, 2=公开',
    `tags`          VARCHAR(500)    DEFAULT NULL             COMMENT '标签（逗号分隔, e.g., "trap,dark,140bpm,C minor,sample-pack"）',
    `allow_preview` TINYINT         NOT NULL DEFAULT 1       COMMENT '是否允许预览: 0=不允许, 1=允许（付费作品由发布者控制，免费作品默认全曲可听）',
    `preview_duration` INT          NOT NULL DEFAULT 30      COMMENT '预览时长（秒），仅付费作品生效，默认 30 秒',
    `sold_count`    INT             NOT NULL DEFAULT 0       COMMENT '已售数量（每次购买成功 +1）',
    `stock`         INT             DEFAULT NULL             COMMENT '库存数量（null 表示不限库存，0 表示售罄）',
    `deleted`       TINYINT         NOT NULL DEFAULT 0       COMMENT '逻辑删除: 0=未删除, 1=已删除',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_producer_id` (`producer_id`),
    INDEX `idx_track_type` (`track_type`),
    INDEX `idx_file_type` (`file_type`),
    INDEX `idx_status_deleted` (`status`, `deleted`),
    INDEX `idx_visibility` (`visibility`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品表（可交易商品，支持单曲和合集）';

-- 2. 作品文件表（PACK 合集中的文件列表）
-- 设计说明：
--   用于 PACK 类型的作品，记录合集中包含的每个文件
--   SINGLE 类型的作品不使用此表（直接用 tracks.file_id / file_type）
--   sort_order 用于控制文件在合集中的展示顺序
--   original_name 冗余存储文件名，避免跨服务查询 vibe-asset
CREATE TABLE IF NOT EXISTS `track_files` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `track_id`      BIGINT          NOT NULL                 COMMENT '所属作品 ID（关联 tracks.id）',
    `asset_id`      BIGINT          NOT NULL                 COMMENT '文件资产 ID（关联 assets.id）',
    `file_type`     VARCHAR(20)     NOT NULL                 COMMENT '文件类型: AUDIO, MIDI',
    `original_name` VARCHAR(255)    DEFAULT NULL             COMMENT '原始文件名（冗余存储）',
    `sort_order`    INT             NOT NULL DEFAULT 0       COMMENT '排序序号（从 0 开始，越小越靠前）',
    `allow_preview` TINYINT         NOT NULL DEFAULT 1       COMMENT '是否允许预览: 0=不允许, 1=允许（发布者可控制合集中哪些文件可试听）',
    `deleted`       TINYINT         NOT NULL DEFAULT 0       COMMENT '逻辑删除: 0=未删除, 1=已删除',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_track_id` (`track_id`),
    INDEX `idx_asset_id` (`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品文件表（PACK 合集文件列表）';

-- 3. 作品协作者表（Track Collaborators）
-- 设计说明：
--   每个作品可以有多个协作者，协作者由作品发布者邀请
--   协作者需要确认后才会在作品页面展示
--   发布者（producer_id）不需要出现在此表中，他默认就是作品所有者
CREATE TABLE IF NOT EXISTS `track_collaborators` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `track_id`      BIGINT          NOT NULL                 COMMENT '作品 ID（关联 tracks.id）',
    `user_id`       BIGINT          NOT NULL                 COMMENT '协作者用户 ID（关联 users.id）',
    `role`          VARCHAR(50)     NOT NULL DEFAULT 'FEATURED'
                                    COMMENT '协作角色: FEATURED=参与制作, VOCALIST=歌手, LYRICIST=作词, ARRANGER=编曲, MIXER=混音, MASTERING=母带',
    `credit_name`   VARCHAR(100)    DEFAULT NULL
                                    COMMENT '署名（可选，默认使用 username。允许协作者自定义显示名）',
    `status`        TINYINT         NOT NULL DEFAULT 0       COMMENT '确认状态: 0=待确认, 1=已确认, 2=已拒绝',
    `invited_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '邀请时间',
    `confirmed_at`  DATETIME        DEFAULT NULL             COMMENT '确认/拒绝时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0       COMMENT '逻辑删除: 0=未删除, 1=已删除',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_track_user` (`track_id`, `user_id`),
    INDEX `idx_track_id` (`track_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品协作者表';

-- 4. 购买记录表
-- 设计说明：
--   记录用户对作品的购买行为（含免费获取）
--   同一用户不可重复购买同一作品（UNIQUE KEY uk_user_track）
--   当前阶段为模拟购买，不涉及真实支付
CREATE TABLE IF NOT EXISTS `purchases` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `user_id`       BIGINT          NOT NULL                 COMMENT '购买者 ID（关联 users.id）',
    `track_id`      BIGINT          NOT NULL                 COMMENT '作品 ID（关联 tracks.id）',
    `price_paid`    DECIMAL(10, 2)  NOT NULL DEFAULT 0       COMMENT '实付金额（0=免费获取）',
    `deleted`       TINYINT         NOT NULL DEFAULT 0       COMMENT '逻辑删除: 0=未删除, 1=已删除',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_track` (`user_id`, `track_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_track_id` (`track_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购买记录表';
