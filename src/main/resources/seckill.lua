-- KEYS[1] = seckill:stock:{activityId}
-- 返回值: 1=扣减成功, 0=已售罄, -1=库存键缺失(需重新预热)
local stock = redis.call('get', KEYS[1])
if not stock then
    return -1
end
local n = tonumber(stock)
if n <= 0 then
    return 0
end
redis.call('decr', KEYS[1])
return 1
