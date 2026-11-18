local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local window = tonumber(ARGV[1])
local threshold = tonumber(ARGV[2])
local added = tonumber(ARGV[3])

local function split(member)
    local separator = string.find(member, ':', 1, true)
    return tonumber(string.sub(member, 1, separator - 1)), tonumber(string.sub(member, separator + 1))
end

-- Each point is a cumulative count. Keep both indexes so trimming old buckets does
-- not require walking a potentially large request history in Lua.
local tail = redis.call('ZREVRANGE', KEYS[1], 0, 0)[1]
if not tail then
    tail = '0:0'
    redis.call('ZADD', KEYS[1], 0, tail)
    redis.call('ZADD', KEYS[2], 0, tail)
end
local tailTime, total = split(tail)
-- Redis TIME is shared by all app instances. Preserve order if its clock moves back.
now = math.max(now, tailTime)
if tailTime == now then
    redis.call('ZREM', KEYS[1], tail)
    redis.call('ZREM', KEYS[2], tail)
end
total = total + added
local point = string.format('%.0f:%.0f', now, total)
redis.call('ZADD', KEYS[1], now, point)
redis.call('ZADD', KEYS[2], total, point)

local cutoff = string.format('(%.0f', now - window)
local beforeWindow = redis.call('ZREVRANGEBYSCORE', KEYS[1], cutoff, '-inf', 'LIMIT', 0, 1)[1]
local first = beforeWindow or redis.call('ZRANGE', KEYS[1], 0, 0)[1]
local firstTime, firstTotal = split(first)
local count = total - firstTotal

local status = 0
if count > threshold and redis.call('EXISTS', KEYS[3]) == 0 then
    if redis.call('XLEN', KEYS[4]) < tonumber(ARGV[5]) then
        redis.call('XADD', KEYS[4], '*', 'application', ARGV[4],
                'count', string.format('%.0f', count), 'threshold', ARGV[2],
                'window', ARGV[1], 'observed_at', string.format('%.0f', now))
        redis.call('PSETEX', KEYS[3], window, '1')
        status = 1
    else
        status = -1
    end
end

-- Once the retained suffix alone exceeds the threshold, older points cannot affect
-- the threshold decision. A later alert can report that suffix as a lower bound.
local beforeEnough = redis.call('ZREVRANGEBYSCORE', KEYS[2], total - threshold - 1, '-inf', 'LIMIT', 0, 1)[1]
if beforeEnough then
    local enoughTime, enoughTotal = split(beforeEnough)
    if enoughTime > firstTime then
        firstTime, firstTotal = enoughTime, enoughTotal
    end
end
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', string.format('(%.0f', firstTime))
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', string.format('(%.0f', firstTotal))
redis.call('PEXPIRE', KEYS[1], window + 1000)
redis.call('PEXPIRE', KEYS[2], window + 1000)
return status
