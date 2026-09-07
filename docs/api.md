# API 设计说明书（API Contract）

> 依据 requirements.md；本文件是 API.md 的完整契约。风格统一：RESTful + JSON。

## 1. 通用约定
- Base URL：`/api`；Content-Type：`application/json`；字符集 UTF-8；
- 鉴权：`Authorization: Bearer {token}`；缺失/失效 → 401；
- 时间格式：`yyyy-MM-dd HH:mm:ss`（Asia/Shanghai）；
- 统一返回体：
```json
{ "code": 200, "message": "success", "data": {} }
```
- 业务错误 HTTP 200 + body.code 区分；401/403/429 用对应 HTTP 状态；
- 分页：`page`(≥1, 默认1) / `size`(默认10, 最大100) → `data: { "total": n, "list": [] }`；
- 幂等性要求：秒杀下单、取消、模拟支付三处必须幂等（语义见各自契约）。

## 2. 错误码表（冻结）
| code | HTTP | 含义 |
|---|---|---|
| 200 | 200 | 成功 |
| 400 | 400 | 参数错误（message 含字段） |
| 401 | 401 | 未登录 / token 失效 |
| 403 | 403 | 无权限 |
| 404 | 404 | 资源不存在 |
| 429 | 429 | 触发限流 |
| 1001 | 200 | 活动未开始 |
| 1002 | 200 | 活动已结束 |
| 1003 | 200 | 活动不存在 |
| 2001 | 200 | 已抢完 |
| 2002 | 200 | 重复抢购 |
| 500 | 500 | 系统异常 |

## 3. 接口总表
| # | Method | Path | 鉴权 | 说明 |
|---|---|---|---|---|
| 1 | POST | /api/auth/register | 无 | 注册 |
| 2 | POST | /api/auth/login | 无 | 登录 |
| 3 | POST | /api/auth/logout | USER | 登出 |
| 4 | GET | /api/goods/{id} | 无 | 商品详情 |
| 5 | GET | /api/seckill/activities | 无 | 用户侧活动列表 |
| 6 | GET | /api/seckill/activities/{id} | 无 | 活动详情 |
| 7 | POST | /api/seckill/{activityId}/orders | USER | 秒杀下单 |
| 8 | GET | /api/orders/mine | USER | 我的订单 |
| 9 | GET | /api/orders/{orderNo} | USER | 订单详情(本人) |
| 10 | POST | /api/orders/{orderNo}/cancel | USER | 取消订单 |
| 11 | POST | /api/orders/{orderNo}/pay | USER | 模拟支付 |
| 12 | POST | /api/admin/activities | ADMIN | 创建活动 |
| 13 | GET | /api/admin/activities | ADMIN | 全部活动 |
| 14 | GET | /api/admin/activities/{id} | ADMIN | 活动详情 |
| 15 | POST | /api/admin/activities/{id}/stock/reload | ADMIN | 重载/预热 Redis 库存 |

## 4. 详细契约

### 4.1 注册
POST /api/auth/register
```json
请求: { "username": "alice", "password": "12345678", "nickname": "爱丽丝" }
成功: { "code": 200, "message": "success", "data": { "userId": 2 } }
失败: 400 用户名已存在/格式不合法
```
规则：username 4~50 位；password ≥8 位；默认角色 USER。

### 4.2 登录
POST /api/auth/login
```json
请求: { "username": "alice", "password": "12345678" }
成功: { "code": 200, "data": { "token": "uuid", "userId": 2, "nickname": "爱丽丝", "role": "USER" } }
失败: 400 用户名或密码错误
```
规则：token TTL 2h（Redis user:token）；失败提示统一，不泄露用户名是否存在。

### 4.3 登出
POST /api/auth/logout（Bearer）→ 200；删除服务端 token。

### 4.4 商品详情
GET /api/goods/{id}
```json
成功: { "code": 200, "data": { "id": 1, "goodsName": "iPhone 15", "goodsDesc": "...", "price": 5999.00, "status": 1 } }
失败: 404 商品不存在或已下架
```

### 4.5 用户侧活动列表
GET /api/seckill/activities?page=1&size=10
```json
data: { "total": 3, "list": [ { "id": 1, "goodsId": 1, "goodsName": "iPhone 15",
        "seckillPrice": 4999.00, "startTime": "2026-09-10 10:00:00",
        "endTime": "2026-09-10 12:00:00", "status": 1, "leftSeconds": 3600 } ] }
```
规则：只返回进行中+即将开始；status 由服务端按时间推导。

### 4.6 活动详情
GET /api/seckill/activities/{id}
```json
data: { "id": 1, "goodsId": 1, "goodsName": "iPhone 15", "seckillPrice": 4999.00,
        "startTime": "...", "endTime": "...", "status": 1, "leftSeconds": 60,
        "soldOut": false }
```
规则：soldOut 布尔（有货/售罄），不暴露精确剩余库存；404 活动不存在。

### 4.7 秒杀下单（核心，幂等）
POST /api/seckill/{activityId}/orders（Bearer）
```json
请求: 无 body
成功: { "code": 200, "data": { "orderNo": "202609081200001234567", "status": 0 } }
失败:
  1001 活动未开始 / 1002 已结束 / 1003 不存在
  2001 已抢完 / 2002 重复抢购 / 401 未登录 / 429 限流
```
规则：
- 不允许客户端传 userId / 数量（恒 1）；
- 重复请求语义：同一用户同一活动已存在活跃订单 → 2002；已取消后再次请求 → 若库存允许正常下单（ADR-007）；
- 服务端保证不产生第二笔活跃订单（uk_user_active 兜底）。

### 4.8 我的订单
GET /api/orders/mine?page=1&size=10（Bearer）
```json
data: { "total": 1, "list": [ { "orderNo": "...", "goodsName": "iPhone 15",
        "seckillPrice": 4999.00, "status": 0, "createTime": "..." } ] }
```
规则：仅返回 token 对应用户的订单。

### 4.9 订单详情
GET /api/orders/{orderNo}（Bearer）
规则：仅本人可看；他人订单 → 403；不存在 → 404。
```json
data: { "orderNo": "...", "goodsName": "...", "seckillPrice": 4999.00,
        "status": 0, "createTime": "...", "payTime": null, "cancelTime": null }
```

### 4.10 取消订单（幂等）
POST /api/orders/{orderNo}/cancel（Bearer）
```json
成功: { "code": 200, "data": { "orderNo": "...", "status": 2 } }
失败: 400 仅待支付可取消（已支付）；404 不存在；403 他人订单
```
规则：待支付→已取消并回补库存；已取消重复调用 → 200（幂等）。

### 4.11 模拟支付（幂等）
POST /api/orders/{orderNo}/pay（Bearer）
```json
成功: { "code": 200, "data": { "orderNo": "...", "status": 1, "payTime": "..." } }
失败: 400 已取消订单不可支付；404/403 同上
```
规则：待支付→已支付；已支付重复调用 → 200（幂等，不重复生效）。

### 4.12 创建活动（ADMIN）
POST /api/admin/activities
```json
请求: { "goodsId": 1, "seckillPrice": 4999.00,
        "startTime": "2026-09-10 10:00:00", "endTime": "2026-09-10 12:00:00",
        "stock": 100 }
成功: { "code": 200, "data": { "activityId": 1 } }
失败: 400 校验不通过（价格≤0 / stock∉[1,100000] / 时间非法 / 商品不存在或下架）；403 非ADMIN
```
规则：同一次请求内完成 落库 + Redis 预热；失败不产生半成品数据（事务+补偿）。

### 4.13 全部活动（ADMIN）
GET /api/admin/activities?page&size → 全量含 status（含已结束）。

### 4.14 活动详情（ADMIN）
GET /api/admin/activities/{id} → 含 stock（DB 精确剩余）与 Redis 侧库存（诊断用）。

### 4.15 重载 Redis 库存（ADMIN）
POST /api/admin/activities/{id}/stock/reload
规则：以 DB 为准重建 seckill:stock / seckill:info；供对账/恢复使用；返回两边数值。

## 5. 安全清单
- 所有写接口鉴权；管理接口校验 role=ADMIN（403）；
- 越权：订单类接口一律用 token userId 过滤，禁止路径参数指定他人；
- 参数校验：DTO + jakarta validation + 全局异常；
- 防注入：MyBatis 预编译（#{}），禁止 ${} 拼接用户输入。
