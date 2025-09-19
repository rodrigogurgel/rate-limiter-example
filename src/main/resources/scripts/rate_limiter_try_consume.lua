-- KEYS[1] = chave (ex.: "rl:{user}:endpoint")
-- ARGV[1] = limit (ex.: "60")
-- ARGV[2] = window_ms (ex.: "1000")

local key       = KEYS[1]
local limit     = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])

-- incrementa contador
local current = redis.call("INCR", key)

-- se for o primeiro, define a janela fixa
if current == 1 then
  redis.call("PEXPIRE", key, window_ms)
end

-- se estourou o limite, desfaz e bloqueia
if current > limit then
  redis.call("DECR", key)
  local ttl = redis.call("PTTL", key)
  if ttl < 0 then ttl = 0 end
  return {0, 0, ttl} -- {allowed=0, remaining=0, ttl_ms=ttl}
end

-- permitido
local remaining = limit - current
local ttl = redis.call("PTTL", key)
if ttl < 0 then ttl = window_ms end
return {1, remaining, ttl} -- {allowed=1, remaining, ttl_ms}
