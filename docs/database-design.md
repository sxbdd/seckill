# 数据库设计说明书（Database Design）

> 依据：requirements.md；本文件是 schema.sql 的唯一来源。

## 1. 设计原则
- 每张表解释"为什么这样设计"；
- 索引解释"为哪个查询而建"；
- 事务与并发安全点在 technical-design.md 描述，这里聚焦结构与约束。

## 2. ER 关系
```
t_user 1 ──── n t_seckill_order n ──── 1 t_seckill_activity n ──── 1 t_goods
t_seckill_activity 1 ──── 1 t_seckill_stock
```
- 一个商品可参与多个活动（时间窗区分，ADR-010 允许重叠）；
- 一个用户可参与多个活动；同一活动同一用户**至多一笔活跃订单**（由 active_key 唯一约束保证）。

## 3. 表设计（含"为什么"）

### 3.1 t_user 用户表
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK AUTO_INCREMENT | 代理主键（业务上 username 唯一但可变，故用自增 id 做关联） |
| username | VARCHAR(50) | NOT NULL UNIQUE uk_username | 登录名；唯一索引支撑注册查重/登录查询 |
| password | VARCHAR(100) | NOT NULL | BCrypt 密文（60 字符内，留余量） |
| nickname | VARCHAR(50) | NULL | |
| role | VARCHAR(20) | NOT NULL DEFAULT 'USER' | USER/ADMIN；角色少，字符串可读性好 |
| status | TINYINT | NOT NULL DEFAULT 1 | 1正常 0禁用；预留封禁能力 |
| created_at / updated_at | DATETIME | 见 DDL | 审计字段 |

### 3.2 t_goods 商品表
普通商品信息。秒杀库存独立（3.4），故本表 stock 仅展示用。
索引：仅主键。商品量小，列表按 id 分页即可，不建多余索引（避免无收益索引）。

### 3.3 t_seckill_activity 秒杀活动表
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | 活动ID（对外暴露） |
| goods_id | BIGINT UNSIGNED | NOT NULL, KEY idx_goods | 关联商品；列表常按商品查，建索引 |
| seckill_price | DECIMAL(10,2) | NOT NULL | 金额用 DECIMAL，禁止浮点 |
| start_time / end_time | DATETIME | NOT NULL; CHECK end>start | 时间窗 |
| status | TINYINT | NOT NULL DEFAULT 0 | 冗余展示态（判定以时间为准 ADR-015） |
| created_at / updated_at | | | |
索引：idx_time(start_time,end_time)——用户侧"进行中/即将开始"列表按时间过滤分页。

### 3.4 t_seckill_stock 秒杀库存表
独立成表的原因：
1. **热点隔离**：扣库存是全系统最热的一行，独立小表让行锁只影响本行，不与活动其它字段更新互相阻塞；
2. **预热/对账简单**：只把这一列同步到 Redis。
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| activity_id | BIGINT UNSIGNED | NOT NULL UNIQUE uk_activity | 1:1；唯一约束防重复初始化 |
| stock | INT | NOT NULL | 剩余库存；≥0 由代码与条件更新保证（不建 CHECK，避免与高并发更新冲突，靠 SQL 条件兜底） |
| created_at / updated_at | | | |

### 3.5 t_seckill_order 秒杀订单表
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT UNSIGNED | PK | |
| order_no | VARCHAR(32) | NOT NULL UNIQUE uk_order_no | 业务订单号（对外）；唯一索引兜底并发重复 |
| user_id | BIGINT UNSIGNED | NOT NULL, KEY idx_user | 我的订单查询 |
| activity_id | BIGINT UNSIGNED | NOT NULL | |
| goods_id / goods_name | BIGINT/VARCHAR(120) | NOT NULL | 商品快照 |
| seckill_price | DECIMAL(10,2) | NOT NULL | 价格快照（R5） |
| status | TINYINT | NOT NULL DEFAULT 0 | 0待支付 1已支付 2已取消 |
| active_key | VARCHAR(64) | 生成列 + UNIQUE uk_user_active | **见下方设计定案** |
| create_time / pay_time / cancel_time | DATETIME | | 状态时间审计 |

**设计定案（ADR-007 落地）**：既要"同一用户同一活动至多一笔**活跃**订单"（DB 级防重最后防线），又要"取消后允许再抢"。
方案：生成列 `active_key` = 当 status∈(0,1) 时为 `user_id_activity_id`，否则 NULL；对 active_key 建唯一索引。
原理：MySQL 唯一索引允许多个 NULL → 只有活跃订单参与唯一性约束；订单取消（status=2）后 active_key 变 NULL，自动"释放名额"，用户可再次下单。
优点：无冗余表、无应用层竞态窗口；是"部分唯一约束"的标准 MySQL 实现。
替代方案对比：①全表 (user_id,activity_id) 唯一 → 取消后无法再抢；②去掉唯一约束 → 失去 DB 级最后防线；③额外锁表 → 多一张表且下单链路复杂。选生成列方案。

## 4. 建表 DDL（schema.sql 的来源，语义以此为准）
```sql
CREATE DATABASE IF NOT EXISTS seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE seckill;

CREATE TABLE t_user (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  username   VARCHAR(50)  NOT NULL COMMENT '登录名',
  password   VARCHAR(100) NOT NULL COMMENT 'BCrypt密文',
  nickname   VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  role       VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE t_goods (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  goods_name VARCHAR(120) NOT NULL COMMENT '商品名',
  goods_desc VARCHAR(500) DEFAULT NULL COMMENT '描述',
  price      DECIMAL(10,2) NOT NULL COMMENT '普通售价',
  stock      INT NOT NULL DEFAULT 0 COMMENT '普通库存(仅展示)',
  status     TINYINT NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

CREATE TABLE t_seckill_activity (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '活动ID',
  goods_id      BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  seckill_price DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
  start_time    DATETIME NOT NULL COMMENT '开始时间',
  end_time      DATETIME NOT NULL COMMENT '结束时间',
  status        TINYINT NOT NULL DEFAULT 0 COMMENT '冗余展示态 0未开始1进行中2已结束',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_goods (goods_id),
  KEY idx_time (start_time, end_time),
  CONSTRAINT chk_activity_time CHECK (end_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表';

CREATE TABLE t_seckill_stock (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存ID',
  activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID(1:1)',
  stock       INT NOT NULL COMMENT '剩余库存',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀库存表';

CREATE TABLE t_seckill_order (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  order_no      VARCHAR(32)  NOT NULL COMMENT '业务订单号',
  user_id       BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  activity_id   BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
  goods_id      BIGINT UNSIGNED NOT NULL COMMENT '商品ID(快照)',
  goods_name    VARCHAR(120) NOT NULL COMMENT '商品名(快照)',
  seckill_price DECIMAL(10,2) NOT NULL COMMENT '秒杀价(快照)',
  status        TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已取消',
  active_key    VARCHAR(64) GENERATED ALWAYS AS (
                  CASE WHEN status IN (0,1)
                       THEN CONCAT(user_id, '_', activity_id)
                       ELSE NULL END
                ) STORED COMMENT '活跃订单唯一键(生成列)',
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  pay_time      DATETIME DEFAULT NULL COMMENT '支付时间',
  cancel_time   DATETIME DEFAULT NULL COMMENT '取消时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  UNIQUE KEY uk_user_active (active_key),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';
```

## 5. 每个索引的"为什么"
| 索引 | 服务的查询/约束 |
|---|---|
| uk_username | 注册查重、登录按用户名查 |
| idx_goods | 按商品查活动 |
| idx_time | 用户侧活动列表按时间窗过滤分页 |
| uk_activity(stock) | 库存与活动 1:1，防重复初始化 |
| uk_order_no | 订单号唯一（对外），并发下防重 |
| uk_user_active | 防重复下单的最后防线（仅活跃订单） |
| idx_user | "我的订单"分页查询 |

## 6. 增长与性能备注
- 数据量增长后：t_seckill_order 按 create_time 分区/归档历史订单；活动表按状态+时间归档；
- 当前规模单表即可，不做分库分表（ADR-012 理由）。
