local now = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])
local resend = tonumber(ARGV[5])
local hour_window = tonumber(ARGV[9])
local day_window = tonumber(ARGV[10])

redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', now - day_window)
redis.call('ZREMRANGEBYSCORE', KEYS[4], '-inf', now - hour_window)

if redis.call('EXISTS', KEYS[2]) == 1 then
    return 'RESEND_LIMIT'
end
if redis.call('ZCOUNT', KEYS[3], now - hour_window, '+inf') >= tonumber(ARGV[6]) then
    return 'PHONE_HOUR_LIMIT'
end
if redis.call('ZCARD', KEYS[3]) >= tonumber(ARGV[7]) then
    return 'PHONE_DAY_LIMIT'
end
if redis.call('ZCARD', KEYS[4]) >= tonumber(ARGV[8]) then
    return 'IP_HOUR_LIMIT'
end

redis.call('SET', KEYS[2], ARGV[1], 'PX', resend)
redis.call('HSET', KEYS[1],
        'token', ARGV[1],
        'codeHash', ARGV[2],
        'issuedAt', ARGV[3])
redis.call('PEXPIRE', KEYS[1], ttl)
redis.call('ZADD', KEYS[3], now, ARGV[1])
redis.call('PEXPIRE', KEYS[3], day_window)
redis.call('ZADD', KEYS[4], now, ARGV[1])
redis.call('PEXPIRE', KEYS[4], hour_window)
return 'RESERVED'
