-- KEYS[1] = chave base da conta   (ex.: "rl:acc:rodrigo1")
-- KEYS[2] = chave base do produto (ex.: "rl:prod:11232")
-- ARGV[1]  = acc_enabled (0/1)
-- ARGV[2]  = acc_capacity (int)
-- ARGV[3]  = acc_window_ms (int)
-- ARGV[4]  = prod_enabled (0/1)
-- ARGV[5]  = prod_capacity (int)
-- ARGV[6]  = prod_window_ms (int)

local acc_base_key    = KEYS[1]
local prod_base_key   = KEYS[2]

local acc_enabled     = tonumber(ARGV[1]) == 1
local acc_capacity    = tonumber(ARGV[2])
local acc_window_ms   = tonumber(ARGV[3])

local prod_enabled    = tonumber(ARGV[4]) == 1
local prod_capacity   = tonumber(ARGV[5])
local prod_window_ms  = tonumber(ARGV[6])

-- Agora/tempo em ms (usa comando TIME do Redis)
local now = redis.call("TIME")                -- {sec, usec}
local now_ms = now[1] * 1000 + math.floor(now[2] / 1000)

-- Calcula a chave da janela fixa e o TTL restante desta janela
local function window_key_and_ttl(base, window_ms)
  if window_ms <= 0 then
    -- fallback defensivo: evita divisão por zero / modulo inválido
    window_ms = 1000
  end
  local window_start = now_ms - (now_ms % window_ms)
  local key = base .. ":" .. window_start
  local ttl_remaining = (window_start + window_ms) - now_ms
  if ttl_remaining < 0 then ttl_remaining = 0 end
  return key, ttl_remaining
end

local acc_key, acc_ttl_remaining = window_key_and_ttl(acc_base_key,  acc_window_ms)
local prod_key, prod_ttl_remaining = window_key_and_ttl(prod_base_key, prod_window_ms)

-- Lê contadores atuais do bucket da janela corrente (0 se não existir)
local function read_count(k)
  local v = redis.call("GET", k)
  if v then return tonumber(v) else return 0 end
end

local acc_cur  = acc_enabled  and read_count(acc_key)  or 0
local prod_cur = prod_enabled and read_count(prod_key) or 0

-- Verifica capacidade antes de consumir
local function can_take(cur, cap) return (cur + 1) <= cap end

local acc_ok  = (not acc_enabled)  or can_take(acc_cur,  acc_capacity)
local prod_ok = (not prod_enabled) or can_take(prod_cur, prod_capacity)

if (not acc_ok) or (not prod_ok) then
  local acc_remaining  = acc_enabled  and math.max(acc_capacity  - acc_cur,  0) or acc_capacity
  local prod_remaining = prod_enabled and math.max(prod_capacity - prod_cur, 0) or prod_capacity

  -- TTL de resposta: sempre o resto da janela corrente (se desabilitado, devolve a janela configurada)
  local acc_ttl_ms  = acc_enabled  and acc_ttl_remaining  or acc_window_ms
  local prod_ttl_ms = prod_enabled and prod_ttl_remaining or prod_window_ms

  return {0, acc_remaining, acc_ttl_ms, prod_remaining, prod_ttl_ms}
end

-- Consome de forma atômica: incrementa nas chaves da janela fixa
local new_acc  = acc_cur
local new_prod = prod_cur

-- Pequeno buffer para garantir expiração logo após terminar a janela (não influencia no TTL retornado)
local function safe_expire_ms(ttl_remaining)
  -- expira um pouquinho depois do fim da janela para tolerar clocks/redes
  local buf = 50
  local v = ttl_remaining + buf
  if v < 1 then v = 1 end
  return v
end

if acc_enabled then
  new_acc = redis.call("INCRBY", acc_key, 1)
  -- Define expiração para o fim da janela atual (+ buffer), não renova a janela
  redis.call("PEXPIRE", acc_key, safe_expire_ms(acc_ttl_remaining))
end

if prod_enabled then
  new_prod = redis.call("INCRBY", prod_key, 1)
  redis.call("PEXPIRE", prod_key, safe_expire_ms(prod_ttl_remaining))
end

-- Calcula remaining e TTL para resposta
local acc_remaining  = acc_enabled  and math.max(acc_capacity  - new_acc,  0) or acc_capacity
local prod_remaining = prod_enabled and math.max(prod_capacity - new_prod, 0) or prod_capacity

local acc_ttl_ms  = acc_enabled  and acc_ttl_remaining  or acc_window_ms
local prod_ttl_ms = prod_enabled and prod_ttl_remaining or prod_window_ms

-- Retorno:
-- allowed, acc_remaining, acc_ttl_ms, prod_remaining, prod_ttl_ms
return {1, acc_remaining, acc_ttl_ms, prod_remaining, prod_ttl_ms}
