# 秒杀系统（Seckill）

高并发**限时限量秒杀**后端服务：不超卖、不重复抢、不被瞬时流量打垮。

## 为什么做 / 解决什么
电商限时低价抢购场景下，开抢瞬间大量用户并发下单。核心问题：库存扣减一致性（防超卖）、防重复下单、热点防护（缓存/限流/对账）。

## 核心功能
- 用户：注册 / 登录（自研 token 鉴权，Redis 存储会话）
- 秒杀活动：运营创建（落库 + Redis 预热）、用户浏览 / 倒计时 / 有货状态
- 秒杀下单：Redis + Lua 原子预扣 → DB 落库兜底（可配置降级为 DB 条件更新）
- 订单：我的订单 / 详情 / 取消（回补库存）/ 模拟支付（幂等）
- 工程能力：防重幂等（Redis SETNX + DB 生成列唯一索引）、定时对账、令牌桶限流、商品缓存（穿透/击穿防护）、统一异常与日志

## 技术栈
Spring Boot 3.3 · JDK 17 · MyBatis-Plus 3.5 · MySQL 8 · Redis · Guava · Maven · JUnit5 / MockMvc

## 文档
| 入口 | 说明 |
|---|---|
| [DESIGN.md](DESIGN.md) | 规格总索引 |
| [docs/requirements.md](docs/requirements.md) | 需求规格 |
| [docs/architecture.md](docs/architecture.md) | 架构 |
| [docs/database-design.md](docs/database-design.md) | 数据库设计 + DDL |
| [docs/api.md](docs/api.md) | API 契约 |
| [docs/technical-design.md](docs/technical-design.md) | 关键机制 |
| [docs/testing.md](docs/testing.md) | 测试与压测 |
| [docs/deployment.md](docs/deployment.md) | 部署与排查 |
| [docs/decisions.md](docs/decisions.md) | 决策记录 ADR |

## 目录结构
```
seckill/
├── docs/                     # 需求/架构/数据库/API/测试/部署/决策 文档
├── sql/schema.sql            # 建表 + 预置商品数据
├── src/main/java/com/example/seckill/
│   ├── common/               # 统一返回、错误码、异常、上下文
│   ├── config/               # Redis/Lua、限流、拦截器注册
│   ├── controller/           # 接口层
│   ├── dto/                  # 请求/响应（Java record）
│   ├── entity/               # 实体
│   ├── interceptor/          # 登录鉴权
│   ├── job/                  # 对账定时任务
│   ├── mapper/               # MyBatis-Plus Mapper
│   ├── service/              # 业务层
│   └── utils/                # 订单号生成等
├── src/main/resources/       # application.yml、seckill.lua
└── src/test/                 # 集成测试 + 降级测试
```

## 环境要求
JDK 17 · Maven 3.8+ · MySQL 8 · Redis 6+（版本与配置见 [docs/deployment.md](docs/deployment.md)）

## 如何启动
1. 初始化数据库（首次）：
   ```bash
   mysql -uroot -p < sql/schema.sql
   ```
   本机已建库 `seckill`，账号 `seckill / seckill123`。
2. 确保 MySQL（服务名 `MySQL80`）与 Redis（服务名 `Redis`）已启动。
3. （可选）复制 `.env.example` 为 `.env` 覆盖默认连接配置；默认值已指向本机。
4. 启动：
   ```bash
   mvn spring-boot:run
   ```
5. 健康检查：`GET http://127.0.0.1:8080/actuator/health` → `{"status":"UP"}`

## 默认账号（仅本地开发）
- 管理员：`admin / admin123`（首次启动自动初始化）
- 普通用户：调用 `POST /api/auth/register` 注册

## 如何测试
```bash
mvn test
```
- 依赖本机 MySQL 测试库 `seckill_test` 与 Redis（逻辑库 15），配置见 `src/test/resources/application-test.yml`
- 共 14 个集成测试，覆盖正常/异常/权限/重复/并发核心规则/降级模式

## 如何打包
```bash
mvn -DskipTests package
java -jar target/seckill-1.0.0.jar
```

## API 文档
见 [docs/api.md](docs/api.md)；接口风格与错误码均在此定义。

## 常见问题 / 排查
见 [docs/deployment.md](docs/deployment.md) §6 Runbook。

> 一切规格以 `docs/` 为准；本文件是入口速览。
