CREATE DATABASE IF NOT EXISTS seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE seckill;

CREATE TABLE IF NOT EXISTS t_user (
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

CREATE TABLE IF NOT EXISTS t_goods (
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

CREATE TABLE IF NOT EXISTS t_seckill_activity (
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

CREATE TABLE IF NOT EXISTS t_seckill_stock (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存ID',
  activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID(1:1)',
  stock       INT NOT NULL COMMENT '剩余库存',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀库存表';

CREATE TABLE IF NOT EXISTS t_seckill_order (
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

INSERT INTO t_goods (id, goods_name, goods_desc, price, stock, status) VALUES
(1, 'iPhone 15', '苹果手机演示商品', 5999.00, 1000, 1),
(2, '机械键盘', '演示商品', 399.00, 500, 1)
ON DUPLICATE KEY UPDATE goods_name = VALUES(goods_name);
