# 技术设计说明书（Technical Design）

> 依据 requirements.md / architecture.md；描述关键机制"怎么实现"及事务/并发边界。

## 1. 防超卖
### 1.1 问题本质
并发"先查后扣"存在 check-then-act 竞态 → 超卖。

### 1.2 方案 A：DB 条件更新（对照/兜底，必做）
```sql
UPDATE t_seckill_stock SET stock = stock - 1
WHERE activity_id = ? AND stock > 0;
```
- 影响行数 = 1 才继续生成订单；
- 原子性来源：InnoDB 对该行 UPDATE 加行锁，同活动扣减串行；
- 约束：必须与插订单同一事务（R4）；否则"扣了没单/有单没扣"。

### 1.3 方案 B：Redis + Lua 预扣（最终方案）
库存预热到 `seckill:stock:{activityId}`；Lua 脚本原子执行"检查>0并扣减"。
```lua
if tonumber(redis.call('get', KEYS[1])) > 0 then
    return redis.call('decr', KEYS[1])
end
return -1
```
- 原子性来源：Redis 单线程执行脚本，期间不插入其它命令；
- 成功后进入 DB 事务：条件更新兜底 + 插订单；DB 落库失败 → 补偿 `INCR` 回补 Redis；
- Redis key 缺失（重启/过期）→ 返回特殊值触发"从 DB 重新预热后重试一次"，仍失败则降级 DB 直扣。

### 1.4 降级（ADR-008）
配置 `seckill.redis-enabled=true|false`：
- true：走方案 B；
- false：走方案 A。
任何模式不允许超卖；降级只损失吞吐。

## 2. 下单时序（正常模式）
```
1 鉴权取 userId
2 活动校验（Redis info 或 DB：存在 && 进行中）
3 Redis SETNX seckill:buy:{uid}:{aid} 判重（TTL=活动结束）
   ├─ 失败 → 2002（查 DB 是否有活跃订单以区分"重复"与"已取消可重抢"）
   └─ 成功 → 继续
4 Lua 预扣 seckill:stock:{aid}
   ├─ 返回 -1 → 2001 已抢完
   └─ 成功 → 5
5 本地事务：
   a. UPDATE t_seckill_stock SET stock=stock-1 WHERE activity_id=? AND stock>0
      （兜底；若影响行数=0 → 抛业务异常回滚并补偿 Redis INCR）
   b. INSERT t_seckill_order（status=0）
      （撞 uk_user_active → DuplicateKey → 按 2002 处理，回滚+补偿）
6 提交 → 返回 orderNo
```
说明：SETNX 是快速拦截；DB 唯一约束（active_key）是最终防线；两处都做，职责不同。

## 3. 取消订单与库存回补
```
1 校验：订单存在且属于本人
2 仅 status=0 可取消（已支付→400；已取消→幂等返回成功）
3 本地事务：UPDATE 订单 SET status=2, cancel_time=now
4 提交后：Redis INCR seckill:stock:{aid}（回补预扣账本）+
          DEL seckill:buy:{uid}:{aid}（释放重抢资格，支持 ADR-007）
5 Redis 操作失败不阻塞主流程，交由对账修复（以 DB 为准）
```
顺序原则：**先 DB 后 Redis**；DB 是最终账本，Redis 可被对账重建。

## 4. 缓存设计（Cache Aside，ADR-018）
- 读：`goods:info:{goodsId}` 命中返回；miss → DB → 回填（TTL 300~600s 随机，防雪崩）；
- 穿透：商品不存在 → 缓存空值占位 60s；
- 击穿：回源前 `SET lock:goods:reload:{goodsId} NX EX 3`，仅成功者回源，其余短暂自旋后读缓存；
- 库存**不进业务读缓存**：库存是写路径（预扣账本），详情页只暴露"有货/售罄"布尔（读 Redis 或 DB 剩余>0，避免把精确库存暴露+缓存不一致）。

## 5. Redis 键规范（全部 TTL 有界；禁止 KEYS，用 SCAN/索引）
| Key | 类型 | TTL | 说明 |
|---|---|---|---|
| seckill:stock:{aid} | String | 活动结束+60s | 预扣账本 |
| seckill:info:{aid} | Hash | 活动结束+60s | {endTime} 供 Lua 判断 |
| seckill:buy:{uid}:{aid} | String | 活动结束 | 判重标记 |
| goods:info:{gid} | String JSON | 300~600s 随机 | 商品详情缓存 |
| user:token:{token} | String | 2h | 登录态 |
| lock:goods:reload:{gid} | String | 3s | 击穿互斥 |

预热时机：创建活动（事务提交后）写入 stock/info；对账任务发现缺失即补。

## 6. 对账任务（M3，@Scheduled 每分钟）
- 扫描进行中/未开始活动：`Redis库存 vs DB库存`；
- 不一致：以 DB 为准 SET 回 Redis，并 error 日志（含 aid、两边值）；
- 同时校验 stock/info 键是否存在，缺失即重建（Redis 重启恢复）。
> 原则：Redis 可丢可重建，MySQL 不允许错（ADR-014）。

## 7. 订单号（ADR-016）
`yyyyMMddHHmmssSSS` + 6 位随机 → 落库撞 uk_order_no 则重试（≤3 次）。

## 8. 事务边界汇总
| 操作 | 事务内容 | 事务外动作 |
|---|---|---|
| 秒杀下单 | 方案A：扣库存+插订单；方案B：DB兜底扣+插订单 | Redis SETNX/Lua（前置）、失败补偿 |
| 取消 | 订单置取消 | Redis INCR + DEL（失败交对账） |
| 创建活动 | 活动+库存双 insert | Redis 预热（失败补偿：删数据或标记待预热） |
| 模拟支付 | 订单置已支付+pay_time | 无（幂等由状态机保证） |

## 9. 异常与日志
- 三类异常：参数(400)/业务(1001~2002)/系统(500)；业务异常只记 message，系统异常记堆栈；
- 下单链路日志必须含 activityId、userId、orderNo(成功)、结果码；日志带 traceId（MDC）；
- 禁止打印 password / token 明文。

## 10. 时间处理（ADR-017）
- 全部 `LocalDateTime` + 数据库 DATETIME（Asia/Shanghai）；
- 禁止用 `toEpochSecond(ZoneOffset.UTC)` 处理本地时间（历史教训：±8h 偏差）；
- 与 Redis 比较用 epoch 秒时，统一 `ZoneId.systemDefault()` 显式转换。

## 11. 异步落库（persist-mode=async，v1.1）
- 目标：把"抢购受理"与"DB 落库"解耦，等价真实秒杀 MQ 削峰的简化版；
- 流程：Redis 扣减成功 → 生成 orderNo → 投递到有界内存队列（100000）→ 立即返回；worker 单线程后台逐个执行"DB 条件更新 + 插订单"；
- 失败补偿：DB 写失败/业务失败 → Redis 库存 +1、删除防重标记，保证不丢库存；
- 语义：订单可见性变为最终一致（用户可能短暂看不到刚下的单）；默认 sync 模式保持即时一致。

## 12. 容量参数与测试口径
- application.yml 默认：Tomcat 500 线程/accept 1000、Hikari 50；
- 压测结论：300 并发瞬时突发 0 错误；>=500 出现 TCP 连接层拒绝（HttpHostConnectException，非业务错误），服务器侧始终零超卖；详见 perf/load-test-report.md。
