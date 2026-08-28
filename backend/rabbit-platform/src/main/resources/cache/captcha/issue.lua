redis.call('HSET', KEYS[1],
        'codeHash', ARGV[1],
        'attempts', 0)
redis.call('PEXPIRE', KEYS[1], ARGV[2])
return 'ISSUED'
