-- KEYS[1] = chave da conta
-- KEYS[2] = chave do produto
-- ARGV[1]  = acc_enabled (0/1)
-- ARGV[2]  = acc_capacity (int)
-- ARGV[3]  = acc_window_ms (int)
-- ARGV[4]  = prod_enabled (0/1)
-- ARGV[5]  = prod_capacity (int)
-- ARGV[6]  = prod_window_ms (int)

local acc_key        = KEYS[1]
local prod_key       = KEYS[2]

local acc_enabled    = tonumber(ARGV[1]) == 1
local acc_capacity   = tonumber(ARGV[2])
local acc_window_ms  = tonumber(ARGV[3])

local prod_enabled   = tonumber(ARGV[4]) == 1
local prod_capacity  = tonumber(ARGV[5])
local prod_window_ms = tonumber(ARGV[6])

-- Helper para ler estado atual (valor e ttl) de um limite
local function read_state(k)
  local cur = tonumber(redis.call("GET", k))
  if not cur then cur = 0 end
  local ttl = redis.call("PTTL", k)
  -- ttl pode ser -1 (sem expiração) ou -2 (não existe). Uniformizamos:
  if ttl < 0 then ttl = -1 end
  return cur, ttl
end

-- Helper que decide se tem espaço (cur + 1 <= capacity)
local function can_take(cur, capacity)
  return (cur + 1) <= capacity
end

-- Lê estados atuais
local acc_cur, acc_ttl = read_state(acc_key)
local prod_cur, prod_ttl = read_state(prod_key)

-- Pré-cálculo de capacidade disponível (sem consumir ainda)
local acc_ok = (not acc_enabled) or can_take(acc_cur, acc_capacity)
local prod_ok = (not prod_enabled) or can_take(prod_cur, prod_capacity)

-- Se qualquer um falhar, não incrementa nenhum
if (not acc_ok) or (not prod_ok) then
  local acc_remaining = acc_enabled and math.max(acc_capacity - acc_cur, 0) or acc_capacity
  local prod_remaining = prod_enabled and math.max(prod_capacity - prod_cur, 0) or prod_capacity

  -- ttl de retorno: se desabilitado, devolve janela como referência
  local acc_ttl_ms = acc_enabled and (acc_ttl >= 0 and acc_ttl or acc_window_ms) or acc_window_ms
  local prod_ttl_ms = prod_enabled and (prod_ttl >= 0 and prod_ttl or prod_window_ms) or prod_window_ms

  -- allowed, acc_remaining, acc_ttl_ms, prod_remaining, prod_ttl_ms
  return {0, acc_remaining, acc_ttl_ms, prod_remaining, prod_ttl_ms}
end

-- Ambos têm capacidade (ou estão disabled) -> agora consumimos de forma atômica
-- Consome conta (se habilitado)
local new_acc = acc_cur
if acc_enabled then
  new_acc = redis.call("INCRBY", acc_key, 1)
  -- Se ainda não tem TTL, aplica janela
  if acc_ttl < 0 then
    redis.call("PEXPIRE", acc_key, acc_window_ms)
    acc_ttl = acc_window_ms
  else
    acc_ttl = redis.call("PTTL", acc_key)
  end
end

-- Consome produto (se habilitado)
local new_prod = prod_cur
if prod_enabled then
  new_prod = redis.call("INCRBY", prod_key, 1)
  if prod_ttl < 0 then
    redis.call("PEXPIRE", prod_key, prod_window_ms)
    prod_ttl = prod_window_ms
  else
    prod_ttl = redis.call("PTTL", prod_key)
  end
end

-- Calcula remaining/ttl de resposta
local acc_remaining = acc_enabled and math.max(acc_capacity - new_acc, 0) or acc_capacity
local prod_remaining = prod_enabled and math.max(prod_capacity - new_prod, 0) or prod_capacity

local acc_ttl_ms = acc_enabled and acc_ttl or acc_window_ms
local prod_ttl_ms = prod_enabled and prod_ttl or prod_window_ms

-- allowed, acc_remaining, acc_ttl_ms, prod_remaining, prod_ttl_ms
return {1, acc_remaining, acc_ttl_ms, prod_remaining, prod_ttl_ms}
