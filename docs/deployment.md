# 部署与运维说明书（Deployment & Operations）

> 本地单机项目：本文件合并 operations（裁剪理由见 decisions ADR-020）。

## 1. 环境要求
| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | 运行环境 |
| Maven | 3.8+ | 构建 |
| MySQL | 8.x | 初始化执行 schema.sql |
| Redis | 6.x+ | 缓存/判重/预扣 |
| IDE | IDEA（可选） | 开发 |

## 2. 配置管理（不硬编码）
`application.yml` + 环境变量覆盖：
| 配置项 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| 数据库 URL/账号/密码 | DB_URL/DB_USERNAME/DB_PASSWORD | localhost/seckill | 禁止提交真实密码 |
| Redis 地址 | REDIS_HOST/REDIS_PORT | localhost:6379 | |
| 限流 QPS | SECKILL_RATE_LIMIT_QPS | 2000 | 令牌桶速率 |
| Redis 开关 | SECKILL_REDIS_ENABLED | true | false=降级 DB 条件更新 |
| token TTL | SECKILL_TOKEN_TTL | 2h | |

## 3. 启动步骤
1. 初始化数据库：`mysql -uroot -p < schema.sql`（脚本生成于 database-design.md §4）；
2. 启动 Redis；3. `mvn spring-boot:run`；4. 预置数据（见 schema.sql 末尾）；
5. 健康检查：`GET /actuator/health`（引入 spring-boot-starter-actuator，暴露 health 即可）。

## 4. 可选 Docker 编排（本地联调用）
`docker-compose.yml` 编排 mysql:8 + redis:7，应用仍本机运行或一并容器化（登记 v2 全容器化）。

## 5. 上线前补齐项（登记，非本期）
- 多环境配置（dev/test/prod 分离）+ 密钥管理；
- CI/CD（GitHub Actions：build + test + 镜像）；
- 集中日志 + 错误告警（生产思维 §6）；
- 数据库迁移工具（Flyway，替代手工 schema.sql）；
- 健康检查/就绪探针完善与自动重启策略。

## 6. 问题排查 Runbook（"出问题先看哪"）
| 症状 | 排查路径 |
|---|---|
| 下单报 500 | 查应用日志 traceId → 异常堆栈 → 是否 DB/Redis 连接 |
| 全部 429 | 限流阈值过低 → 调 SECKILL_RATE_LIMIT_QPS |
| 提示已抢完但 DB 有库存 | Redis 库存与 DB 不一致 → 跑对账/手动 reload（4.15） |
| 提示可抢但一直失败 | 活动时间窗/时区 → 核对 start/end（ADR-017 防时区坑） |
| Redis 重启后异常 | 对账任务自动补预热；紧急用 4.15 手动 reload |
| 压测错误率高 | 先看 DB 连接池/慢 SQL 日志，再谈优化 |
