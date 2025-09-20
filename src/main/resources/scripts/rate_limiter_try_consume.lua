-- KEYS[1] = chave base da conta   (ex.: "rl:acc:rodrigo1")
-- KEYS[2] = chave base do produto (ex.: "rl:prod:11232")
-- ARGV[1]  = acc_enabled (0/1)
-- ARGV[2]  = acc_capacity (int)
-- ARGV[3]  = acc_window_ms (int)   -- normalmente 1000
-- ARGV[4]  = prod_enabled (0/1)
-- ARGV[5]  = prod_capacity (int)
-- ARGV[6]  = prod_window_ms (int)  -- normalmente 1000

local acc_base_key    = KEYS[1]
local prod_base_key   = KEYS[2]

local acc_enabled     = tonumber(ARGV[1]) == 1
local acc_capacity    = tonumber(ARGV[2])
local acc_window_ms   = tonumber(ARGV[3])

local prod_enabled    = tonumber(ARGV[4]) == 1
local prod_capacity   = tonumber(ARGV[5])
local prod_window_ms  = tonumber(ARGV[6])

-- tempo atual em ms
local now = redis.call("TIME")
local now_ms = now[1] * 1000 + math.floor(now[2] / 1000)

-- define bucket de 100ms
local function bucket_info(base, window_ms)
  if window_ms <= 0 then window_ms = 1000 end
  local bucket_size = 100 -- 100ms
  local bucket_id = math.floor(now_ms / bucket_size)
  local key = base .. ":" .. bucket_id
  local ttl = window_ms + bucket_size
  return key, bucket_id, ttl, bucket_size
end

-- soma os últimos N buckets de 100ms (N = window_ms / bucket_size)
local function sliding_sum(base, capacity, window_ms, bucket_size)
  local n_buckets = math.floor(window_ms / bucket_size)
  local cur_bucket = math.floor(now_ms / bucket_size)
  local sum = 0
  for i = 0, n_buckets - 1 do
    local k = base .. ":" .. (cur_bucket - i)
    local v = redis.call("GET", k)
    if v then sum = sum + tonumber(v) end
  end
  return sum
end

-- calcula consumo atual
local acc_sum = acc_enabled and sliding_sum(acc_base_key, acc_capacity, acc_window_ms, 100) or 0
local prod_sum = prod_enabled and sliding_sum(prod_base_key, prod_capacity, prod_window_ms, 100) or 0

-- checa capacidade
local acc_ok = (not acc_enabled) or (acc_sum + 1 <= acc_capacity)
local prod_ok = (not prod_enabled) or (prod_sum + 1 <= prod_capacity)

if (not acc_ok) or (not prod_ok) then
  local acc_remaining  = acc_enabled  and math.max(acc_capacity  - acc_sum,  0) or acc_capacity
  local prod_remaining = prod_enabled and math.max(prod_capacity - prod_sum, 0) or prod_capacity
  local ttl_ms = 100  -- sempre aguardar o próximo sub-bucket
  return {0, acc_remaining, ttl_ms, prod_remaining, ttl_ms}
end

-- incrementa no bucket atual
local acc_key, acc_bucket, acc_ttl = bucket_info(acc_base_key, acc_window_ms)
local prod_key, prod_bucket, prod_ttl = bucket_info(prod_base_key, prod_window_ms)

if acc_enabled then
  redis.call("INCRBY", acc_key, 1)
  redis.call("PEXPIRE", acc_key, acc_ttl)
end
if prod_enabled then
  redis.call("INCRBY", prod_key, 1)
  redis.call("PEXPIRE", prod_key, prod_ttl)
end

-- recalc remaining
local acc_remaining  = acc_enabled  and math.max(acc_capacity  - (acc_sum + 1), 0) or acc_capacity
local prod_remaining = prod_enabled and math.max(prod_capacity - (prod_sum + 1), 0) or prod_capacity

return {1, acc_remaining, 100, prod_remaining, 100}
