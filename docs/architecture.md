# 架构设计说明书（Architecture）

## 1. 架构目标
单体、分层、可测试、可解释。用最小复杂度真实解决"高并发扣减一致性与热点防护"。

## 2. 技术选型与理由（详见 decisions ADR-011/012）
| 组件 | 选型 | 业务/工程理由 |
|---|---|---|
| JDK | 17 LTS | 长期支持 |
| 框架 | Spring Boot 3.x | 自动装配、生态、招聘主流 |
| ORM | MyBatis-Plus 3.5.x | SQL 可控；国内主流 |
| DB | MySQL 8 InnoDB utf8mb4 | 事务 + 行锁 |
| 缓存 | Redis 6+ | Lua 原子脚本 / 判重 / token |
| 鉴权 | 自研 token（UUID+Redis TTL2h） | 轻量；v2 候选 JWT |
| 构建 | Maven | 主流 |
| 测试 | JUnit5 + MockMvc | 接口级验证 |
| 压测 | JMeter（脚本入库） | 验收量化 |

## 3. 分层与包结构（模块化/单一职责）
```
com.example.seckill
├── controller     接口层：参数校验、统一返回
├── service        业务层（具体 @Service 类；单实现场景省略接口层，见 ADR-021）
│   （无 impl 分包，理由见 ADR-021）
├── mapper         MyBatis-Plus Mapper
├── entity         实体（与表 1:1，禁止直接出参）
├── dto            请求/响应对象
├── common         Result / 错误码 / 异常 / 常量 / 上下文
├── config         Redis/线程池/限流/定时/拦截器注册
├── interceptor    登录鉴权、角色鉴权
├── job            对账定时任务
└── utils          订单号/时间工具
```

## 4. 非功能设计
| 类别 | 要求 |
|---|---|
| 性能 | 500 并发无超卖；P95≤200ms；TPS 实测留档 |
| 安全 | BCrypt；token 鉴权+角色；防越权(按 token 取 userId)；预编译 SQL；日志不落密码/token；参数校验 |
| 可用性 | Redis 可降级为 DB 条件更新（开关）；降级不允许超卖 |
| 可维护性 | 分层、统一异常/返回、traceId 日志、阿里规约核心项 |
| 可测试性 | 核心链路集成测试覆盖；压测脚本随仓库 |

## 5. 部署拓扑（v1 单机）
```
[Postman/JMeter/浏览器] → Spring Boot(:8080) → MySQL(:3306)
                                        └──→ Redis(:6379)
```
环境：本机运行；可选 docker-compose 编排 MySQL+Redis（deployment.md）。

## 6. 生产风险清单（"真上线会怎样"主动预演）
| 风险 | 应对（v1 内做到） | 登记 |
|---|---|---|
| Redis 宕机 | 降级开关 → DB 条件更新；对账重建 | 自动熔断 v2 |
| MySQL 宕机 | 无法服务（单点）；快速重启+初始化脚本 | 主从 v2 |
| 流量突增 | 限流 429 保护 | MQ 削峰 v2 |
| 重复提交 | Redis 判重 + DB 唯一索引 | — |
| 扣库存成功/落库失败 | 补偿回补 + 对账 | 本地消息表 v2 |
| 新版本 bug | Git 版本回滚（单机部署=替换 jar） | CI/CD v2 |
| 接口变慢 | 日志 traceId + P95 压测留档 | 监控告警 v2 |

## 7. 演进路径（规模扩大时）
CDN/静态化拦截 → 多实例水平扩容(需 Redis 分布式限流+分布式锁) → MQ 削峰 → 分库分表 → 微服务拆分（仅当团队/业务需要）。


## 8. v1.1 运行形态补充（与代码对齐）
- 部署容量默认参数（application.yml）：Tomcat max-threads=500、accept-count=1000、Hikari maximum-pool-size=50；
- 新增异步落库 worker：OrderAsyncPersister（单线程消费内存队列写 DB），由 seckill.persist-mode=sync|async 切换（默认 sync）；
- 压测与容量结论见 perf/load-test-report.md。
