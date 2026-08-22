local stored_hash = redis.call('HGET', KEYS[1], 'codeHash')
if stored_hash == false then
    return 'MISSING'
end

if stored_hash ~= ARGV[1] then
    local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
    if attempts >= tonumber(ARGV[2]) then
        redis.call('DEL', KEYS[1])
        return 'LOCKED'
    end
    return 'WRONG'
end

redis.call('DEL', KEYS[1])
return 'VERIFIED'
