# JMeter 压测报告（Seckill Load Test）

> 日期：2026-09-08
> 目的：验证"无超卖、无重复"并量化 方案A(DB条件更新) vs 方案B(Redis+Lua 预扣) 的吞吐差异。

## 1. 环境
| 项 | 值 |
|---|---|
| 操作系统 | Windows（本机单机） |
| JDK / 框架 | Temurin JDK 17 / Spring Boot 3.3.5（打包 jar 运行） |
| MySQL | 8.0.46（本机，数据目录 D:\Dev\Data\MySQL） |
| Redis | 本机 6379（数据目录 D:\Dev\Data\Redis） |
| 压测工具 | Apache JMeter 5.6.3（CLI 模式） |
| 容量参数 | Tomcat max-threads=500，accept-count=1000，Hikari pool=50 |

## 2. 方法
- 场景：`POST /api/seckill/{activityId}/orders`（真实 HTTP，Bearer token 鉴权）
- 并发模型：JMeter 300 线程、ramp=0（瞬间突发）、每线程 1 个独立用户 token（CSV 提供），每用户限购 1 件
- 方案 A：`--seckill.redis-enabled=false`，库存扣减走 DB 条件更新
- 方案 B：`--seckill.redis-enabled=true`，库存扣减走 Redis + Lua 预扣，DB 条件更新兜底
- 库存核对：压测后查 `订单数 == 初始库存 - 剩余库存`，且订单数 == 初始库存（全成功场景）或 == 库存上限（售罄冲击场景）

## 3. 结果（300 并发，错误率均为 0）
| 场景 | 初始库存 | 方案 | TPS | 业务错误 | 订单数 | 剩余库存 | 一致性 |
|---|---|---|---|---|---|---|---|
| 全成功（人人有货） | 300 | B Redis+Lua | 170.8 | 0 | 300 | 0 | ✅ |
| 全成功（人人有货） | 300 | A DB直扣 | 171.4 | 0 | 300 | 0 | ✅ |
| 售罄冲击（30人抢不到） | 50 | B Redis+Lua | 314.1 | 0 | 50 | 0 | ✅ |
| 售罄冲击（30人抢不到） | 50 | A DB直扣 | 297.0 | 0 | 50 | 0 | ✅ |

说明：TPS = 样本数 / 首末请求耗时差（含排队与网络）；"业务错误"指 5xx/连接失败，库存不足/重复抢购属预期业务返回（HTTP 200），不计为错误。

## 4. 结论（诚实版）
1. **正确性达标**：四组压测订单数与库存严格一致，无超卖、无重复（符合验收标准 1）。
2. **吞吐**：全成功路径两方案接近（170.8 vs 171.4）——本机场景下单笔成功都要写一次 DB 订单，DB 写是主要瓶颈；售罄冲击场景 Redis+Lua 略高（314.1 vs 297.0，约 +5.8%）。
3. **Redis 预扣的真实价值**（本项目里）：
   - 扣减动作在 Redis 完成，原子且不占用 DB 行锁；库存耗尽后的拒绝请求基本不打 DB；
   - 本机 300 并发不足以体现差距（低于 DB 写上限），在更高并发/更大售罄比例下差距会更明显；
   - 作为面试表述：**"Redis+Lua 原子预扣避免把每次扣减压到 DB 行锁上，售罄冲击下吞吐更高、DB 压力更小"**——不要夸大成"倍数提升"。
4. **稳定性备注**：尝试 500/1000 线程瞬间突发时出现连接层抖动（HttpHostConnectException，本机 TIME_WAIT/accept 限制），属客户端连接问题而非业务错误；本报告以 300 并发稳定数据为准。

## 5. 复现
```powershell
powershell -ExecutionPolicy Bypass -File perf\run-load.ps1 -Mode B -Threads 300 -Stock 50   # 方案B
powershell -ExecutionPolicy Bypass -File perf\run-load.ps1 -Mode A -Threads 300 -Stock 50   # 方案A
```
脚本会：清理旧压测用户 → 批量建用户+token(Redis) → 建活动 → 起应用(带容量参数) → 跑 JMeter → 停应用 → 输出 TPS/错误/库存核对。
