-- KEYS[1] = seckill:stock:{activityId}
-- ARGV[1] = 数据库当前库存（key 缺失时用于初始化）
-- ARGV[2] = key 过期秒数
-- 返回: 1=扣减成功, 0=已售罄
local stock = redis.call('get', KEYS[1])
if not stock then
    redis.call('set', KEYS[1], ARGV[1])
    redis.call('expire', KEYS[1], ARGV[2])
    stock = ARGV[1]
end
local n = tonumber(stock)
if n <= 0 then
    return 0
end
redis.call('decr', KEYS[1])
return 1
