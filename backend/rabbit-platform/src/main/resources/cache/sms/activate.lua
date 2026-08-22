if redis.call('HGET', KEYS[1], 'token') ~= ARGV[1] then
    return 'MISSING'
end

local pending_ttl = redis.call('PTTL', KEYS[1])
if pending_ttl <= 0 then
    return 'MISSING'
end

local pending_issued_at = tonumber(redis.call('HGET', KEYS[1], 'issuedAt'))
local active_issued_at = tonumber(redis.call('HGET', KEYS[2], 'issuedAt'))
if active_issued_at ~= nil and active_issued_at > pending_issued_at then
    redis.call('DEL', KEYS[1])
    return 'STALE'
end

redis.call('HSET', KEYS[2],
        'token', ARGV[1],
        'codeHash', redis.call('HGET', KEYS[1], 'codeHash'),
        'issuedAt', pending_issued_at,
        'attempts', 0)
redis.call('PEXPIRE', KEYS[2], pending_ttl)
redis.call('DEL', KEYS[1])
return 'ACTIVATED'
