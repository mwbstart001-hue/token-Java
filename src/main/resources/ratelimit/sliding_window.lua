local key = KEYS[1]
local limit = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])
local currentTime = tonumber(ARGV[3])

local windowStart = currentTime - windowMs

redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)

local currentCount = redis.call('ZCARD', key)

if currentCount >= limit then
    return 0
end

redis.call('ZADD', key, currentTime, currentTime .. ':' .. math.random())
redis.call('PEXPIRE', key, windowMs)

return 1
