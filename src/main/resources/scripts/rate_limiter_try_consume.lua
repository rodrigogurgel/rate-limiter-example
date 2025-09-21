-- KEYS[1] = chave base da conta   (ex.: "rl:acc:rodrigo1")
-- KEYS[2] = chave base do produto (ex.: "rl:prod:11232")
-- ARGV[1]  = acc_enabled (0/1)
-- ARGV[2]  = acc_capacity (int)            -- capacidade/máximo por janela da conta
-- ARGV[3]  = acc_window_ms (int)           -- janela em ms (p.ex., 1000)
-- ARGV[4]  = prod_enabled (0/1)
-- ARGV[5]  = prod_capacity (int)           -- capacidade/máximo por janela do produto
-- ARGV[6]  = prod_window_ms (int)          -- janela em ms (p.ex., 1000)
-- ARGV[7]  = bucket_size_ms (opcional)     -- tamanho do sub-bucket; default = 100ms

local acc_base_key    = KEYS[1]
local prod_base_key   = KEYS[2]

local acc_enabled     = tonumber(ARGV[1]) == 1
local acc_capacity    = tonumber(ARGV[2])
local acc_window_ms   = tonumber(ARGV[3])

local prod_enabled    = tonumber(ARGV[4]) == 1
local prod_capacity   = tonumber(ARGV[5])
local prod_window_ms  = tonumber(ARGV[6])

-- --------- CONFIG GLOBAL ---------
-- BUCKET_SIZE define a granularidade do contador por janela deslizante.
-- Menor = janela “mais contínua”, porém mais chaves; Maior = menos chaves, porém mais granulação.
local BUCKET_SIZE = tonumber(ARGV[7]) or 100
-- ---------------------------------

-- Tempo atual obtido do relógio do Redis.
-- redis.call("TIME") retorna { segundos, microssegundos } desde 1970-01-01.
-- Convertido para milissegundos inteiros (floor) para indexar buckets.
local now = redis.call("TIME")
local now_ms = now[1] * 1000 + math.floor(now[2] / 1000)

--[[
bucket_info(base, window_ms) -> key, bucket_id, ttl_ms, bucket_size
Descrição:
  - Calcula informações do sub-bucket “corrente” (de largura BUCKET_SIZE) com base no now_ms.
Parâmetros:
  - base: prefixo da chave (ex.: "rl:acc:<id>" ou "rl:prod:<id>")
  - window_ms: tamanho da janela deslizante em ms (ex.: 1000)
Retorno:
  - key: chave Redis do bucket corrente (ex.: "base:<bucket_id>")
  - bucket_id: índice global do bucket, floor(now_ms / BUCKET_SIZE); cresce +1 a cada BUCKET_SIZE ms
  - ttl_ms: tempo de vida do bucket = window_ms + BUCKET_SIZE (margem p/ não expirar antes do último uso)
  - bucket_size: apenas para referência (BUCKET_SIZE)
Invariantes:
  - ttl_ms >= BUCKET_SIZE e ttl_ms > window_ms (evita expirar cedo demais).
Complexidade: O(1)
--]]
local function bucket_info(base, window_ms)
  if not window_ms or window_ms <= 0 then window_ms = 1000 end
  local bucket_id = math.floor(now_ms / BUCKET_SIZE)
  local key = base .. ":" .. bucket_id
  local ttl = window_ms + BUCKET_SIZE
  return key, bucket_id, ttl, BUCKET_SIZE
end

--[[
sliding_sum(base, window_ms) -> sum
Descrição:
  - Soma os contadores dos últimos N buckets consecutivos, onde N = floor(window_ms / BUCKET_SIZE),
    cobrindo a janela deslizante imediatamente anterior ao tempo “agora”.
Parâmetros:
  - base: prefixo da chave (ex.: "rl:acc:<id>")
  - window_ms: tamanho da janela em ms (ex.: 1000)
Retorno:
  - sum: inteiro representando quantas operações ocorreram na janela (últimos N buckets)
Observações:
  - Usa IDs globais de bucket (cur_bucket, cur_bucket-1, ..., cur_bucket-(N-1)).
  - Buckets inexistentes (chave ausente) contam como zero.
  - Se window_ms não for múltiplo de BUCKET_SIZE, efetivamente arredonda para baixo.
Complexidade:
  - O(N) leituras de GET no Redis, com N = floor(window_ms / BUCKET_SIZE).
Trade-offs:
  - BUCKET_SIZE menor → N maior → mais GETs (mais precisão/“alisamento”).
  - BUCKET_SIZE maior → N menor → menos GETs (mais granulação).
--]]
local function sliding_sum(base, window_ms)
  if not window_ms or window_ms <= 0 then return 0 end
  local n_buckets = math.floor(window_ms / BUCKET_SIZE)
  if n_buckets <= 0 then return 0 end
  local cur_bucket = math.floor(now_ms / BUCKET_SIZE)
  local sum = 0
  for i = 0, n_buckets - 1 do
    local k = base .. ":" .. (cur_bucket - i)
    local v = redis.call("GET", k)
    if v then sum = sum + tonumber(v) end
  end
  return sum
end

-- Consumo atual por escopo (janela deslizante)
local acc_sum  = acc_enabled  and sliding_sum(acc_base_key,  acc_window_ms)  or 0
local prod_sum = prod_enabled and sliding_sum(prod_base_key, prod_window_ms) or 0

-- Checagem de capacidade PRÉ-incremento (pessimista):
-- Permite somente se a soma atual + 1 (o hit presente) não ultrapassa a capacidade.
local acc_ok  = (not acc_enabled)  or (acc_sum  + 1 <= acc_capacity)
local prod_ok = (not prod_enabled) or (prod_sum + 1 <= prod_capacity)

if (not acc_ok) or (not prod_ok) then
  local acc_remaining  = acc_enabled  and math.max(acc_capacity  - acc_sum,  0) or acc_capacity
  local prod_remaining = prod_enabled and math.max(prod_capacity - prod_sum, 0) or prod_capacity
  local ttl_ms = BUCKET_SIZE -- sugestão de espera para o próximo sub-bucket
  return {
    0,               -- [1] allowed: 0 = bloqueado (limite atingido em conta ou produto)
    acc_remaining,   -- [2] remaining (account): quanto ainda cabe na janela da conta
    ttl_ms,          -- [3] retry/ttl hint (account): sugestão de aguardo (ms) até próximo sub-bucket
    prod_remaining,  -- [4] remaining (product): quanto ainda cabe na janela do produto
    ttl_ms           -- [5] retry/ttl hint (product): sugestão de aguardo (ms) até próximo sub-bucket
  }
end

-- Passou na checagem: incrementa no bucket corrente e renova TTL (janela + margem de 1 bucket)
local acc_key, _, acc_ttl = bucket_info(acc_base_key,  acc_window_ms)
local prod_key, _, prod_ttl = bucket_info(prod_base_key, prod_window_ms)

if acc_enabled then
  redis.call("INCRBY", acc_key, 1)      -- incrementa o contador do bucket corrente (conta)
  redis.call("PEXPIRE", acc_key, acc_ttl) -- garante que viva até o fim da janela + margem
end
if prod_enabled then
  redis.call("INCRBY", prod_key, 1)       -- incrementa o contador do bucket corrente (produto)
  redis.call("PEXPIRE", prod_key, prod_ttl) -- idem
end

-- Remaining PÓS-incremento (já conta o hit atual)
local acc_remaining  = acc_enabled  and math.max(acc_capacity  - (acc_sum + 1), 0) or acc_capacity
local prod_remaining = prod_enabled and math.max(prod_capacity - (prod_sum + 1), 0) or prod_capacity

return {
  1,               -- [1] allowed: 1 = permitido
  acc_remaining,   -- [2] remaining (account): capacidade residual da conta após este hit
  BUCKET_SIZE,     -- [3] retry/ttl hint (account): sugerimos BUCKET_SIZE ms (próximo sub-bucket)
  prod_remaining,  -- [4] remaining (product): capacidade residual do produto após este hit
  BUCKET_SIZE      -- [5] retry/ttl hint (product): sugerimos BUCKET_SIZE ms (próximo sub-bucket)
}
