CREATE TABLE IF NOT EXISTS `mt_china_workday_calendar`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `calendar_date` date         NOT NULL COMMENT '日历日期',
    `day_type`      varchar(32)  NOT NULL COMMENT '日型: HOLIDAY/ADJUSTED_WORKDAY',
    `holiday_name`  varchar(64)  DEFAULT NULL COMMENT '节假日名称',
    `source_year`   int          NOT NULL COMMENT '来源年份',
    `source_title`  varchar(255) NOT NULL COMMENT '来源标题',
    `source_url`    varchar(1000) DEFAULT NULL COMMENT '来源链接',
    `enabled`       tinyint(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `create_time`   datetime     NOT NULL COMMENT '创建时间',
    `update_time`   datetime     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mt_china_workday_calendar_date` (`calendar_date`),
    KEY `idx_mt_china_workday_calendar_year_enabled` (`source_year`, `enabled`),
    KEY `idx_mt_china_workday_calendar_date_enabled` (`calendar_date`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='中国工作日历表';

SET @china_workday_calendar_source_title = '国务院办公厅关于2026年部分节假日安排的通知';
SET @china_workday_calendar_source_url = 'https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm';

INSERT INTO `mt_china_workday_calendar` (
    `calendar_date`, `day_type`, `holiday_name`, `source_year`, `source_title`, `source_url`, `enabled`, `create_time`, `update_time`
) VALUES
    ('2026-01-01', 'HOLIDAY', '元旦', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-01-02', 'HOLIDAY', '元旦', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-01-03', 'HOLIDAY', '元旦', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-01-04', 'ADJUSTED_WORKDAY', '元旦补班', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-02-14', 'ADJUSTED_WORKDAY', '春节补班', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-02-15', 'HOLIDAY', '春节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-02-16', 'HOLIDAY', '春节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-02-17', 'HOLIDAY', '春节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-02-18', 'HOLIDAY', '春节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-02-19', 'HOLIDAY', '春节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-02-20', 'HOLIDAY', '春节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-02-21', 'HOLIDAY', '春节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-02-22', 'HOLIDAY', '春节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-02-23', 'HOLIDAY', '春节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-02-28', 'ADJUSTED_WORKDAY', '春节补班', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-04-04', 'HOLIDAY', '清明节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-04-05', 'HOLIDAY', '清明节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-04-06', 'HOLIDAY', '清明节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-05-01', 'HOLIDAY', '劳动节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-05-02', 'HOLIDAY', '劳动节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-05-03', 'HOLIDAY', '劳动节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-05-04', 'HOLIDAY', '劳动节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-05-05', 'HOLIDAY', '劳动节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-05-09', 'ADJUSTED_WORKDAY', '劳动节补班', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-06-19', 'HOLIDAY', '端午节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-06-20', 'HOLIDAY', '端午节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-06-21', 'HOLIDAY', '端午节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-09-25', 'HOLIDAY', '中秋节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-09-26', 'HOLIDAY', '中秋节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-09-27', 'HOLIDAY', '中秋节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-09-20', 'ADJUSTED_WORKDAY', '国庆节补班', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-10-01', 'HOLIDAY', '国庆节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-10-02', 'HOLIDAY', '国庆节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-10-03', 'HOLIDAY', '国庆节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-10-04', 'HOLIDAY', '国庆节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-10-05', 'HOLIDAY', '国庆节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-10-06', 'HOLIDAY', '国庆节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-10-07', 'HOLIDAY', '国庆节', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW()),
    ('2026-10-10', 'ADJUSTED_WORKDAY', '国庆节补班', 2026, @china_workday_calendar_source_title, @china_workday_calendar_source_url, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `day_type` = VALUES(`day_type`),
    `holiday_name` = VALUES(`holiday_name`),
    `source_year` = VALUES(`source_year`),
    `source_title` = VALUES(`source_title`),
    `source_url` = VALUES(`source_url`),
    `enabled` = VALUES(`enabled`),
    `update_time` = VALUES(`update_time`);
