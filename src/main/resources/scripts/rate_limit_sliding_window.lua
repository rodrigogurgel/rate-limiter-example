--[[
RATE LIMIT com janela deslizante por sub-buckets (1 escopo por execução; HASH)
=============================================================================

CHAVE:
  KEYS[1] = hash_key do escopo

ARGUMENTOS:
  ARGV[1] = enabled (0/1)                 -- liga/desliga o rate limit do escopo
  ARGV[2] = capacity (int)                -- máximo permitido por janela
  ARGV[3] = window_ms (int)               -- tamanho da janela (ex.: 1000 ms)
  ARGV[4] = bucket_size_ms (opcional)     -- granularidade (default: 100 ms)
  ARGV[5] = delta (opcional)              -- quanto consumir; 0 = apenas checar (default: 1)

SAÍDA (array de 6 elementos):
  [1] allowed (0/1)
      1 = permitido (se delta>0, já aplicou o incremento)
      0 = negado (nenhuma alteração foi feita)
  [2] remaining (int)
      Capacidade restante na janela após a decisão.
      Se allowed=1 e delta>0, considera o consumo aplicado; se allowed=0, é (capacity - used_before).
  [3] retry_after_ms (int)
      Sugestão de espera até a próxima borda de sub-bucket (tempo restante do bucket atual).
  [4] hash_key (string)
      A própria KEYS[1], retornada para facilitar compensação.
  [5] bucket_field (string)
      O field do hash correspondente ao bucket atual (ex.: "1987654321").
      Use com HINCRBY <hash_key> <bucket_field> -<delta> para compensar um consumo anterior.
  [6] used_in_window (int)
      Uso apurado na janela *antes* de aplicar o delta (telemetria/debug).

EXEMPLOS DE SAÍDA:
  Permitido (com delta=1):
    [ 1, 0, 42, "rl:acc:rodrigo1", "1987654321", 59 ]
      -> allowed=1; remaining=0 (60 - (59+1)); retry_after=42ms

  Negado (capacidade já cheia):
    [ 0, 0, 42, "rl:acc:rodrigo1", "1987654321", 60 ]
      -> allowed=0; remaining=0; retry_after=42ms

COMPENSAÇÃO (rollback) NO CLIENTE:
  Se um segundo escopo negar após o primeiro ter permitido e consumido, desfazer assim:
    HINCRBY <hash_key> <bucket_field> -<delta>
  (Não altere o TTL; deixe o hash expirar normalmente.)

OBSERVAÇÕES DE DESIGN:
  - 1 HASH por escopo: fields = bucket_id; reduz fragmentação de chaves.
  - HMGET para somar N buckets em 1 ida ao Redis (melhor que N GETs).
  - TTL aplicado no hash (PEXPIRE) com margem (window_ms + bucket_size_ms).
  - delta configurável (0 = pré-flight).
]]--

local hash_key       = KEYS[1]

local enabled        = tonumber(ARGV[1]) == 1
local capacity       = tonumber(ARGV[2]) or 0
local window_ms      = tonumber(ARGV[3]) or 1000
local BUCKET_SIZE    = tonumber(ARGV[4]) or 100
local delta          = tonumber(ARGV[5]) or 1   -- 0 => only-check (pré-flight)

-- ------------------------------------------------------------------
-- Tempo corrente do Redis em milissegundos (consistente entre nós)
-- ------------------------------------------------------------------
local now            = redis.call("TIME")                 -- {sec, usec}
local now_ms         = now[1] * 1000 + math.floor(now[2] / 1000)

-- ------------------------------------------------------------------
-- Sanitização de parâmetros e derivação de variáveis
-- ------------------------------------------------------------------
if window_ms <= 0 then window_ms = 1000 end
if BUCKET_SIZE <= 0 then BUCKET_SIZE = 100 end

-- n_buckets mínimo = 1 (mesmo se window < bucket_size)
local n_buckets      = math.floor(window_ms / BUCKET_SIZE)
if n_buckets < 1 then n_buckets = 1 end

-- bucket atual e seu field correspondente no hash
local cur_bucket     = math.floor(now_ms / BUCKET_SIZE)
local cur_field      = tostring(cur_bucket)

-- TTL alvo do hash: janela + 1 bucket de margem
local desired_ttl    = window_ms + BUCKET_SIZE

-- retry hint: tempo restante até a próxima borda de bucket
local ms_into_bucket = now_ms % BUCKET_SIZE
local retry_hint     = BUCKET_SIZE - ms_into_bucket

-- ------------------------------------------------------------------
-- Soma dos últimos N buckets via HMGET
-- ------------------------------------------------------------------
local function sliding_sum(hash, cur_b, n)
  -- monta a lista de fields: cur_b, cur_b-1, ..., cur_b-(n-1)
  local fields = {}
  for i = 0, n - 1 do
    fields[#fields + 1] = tostring(cur_b - i)
  end

  -- HMGET: 1 chamada para N fields
  local values = redis.call("HMGET", hash, unpack(fields))
  local sum = 0
  for i = 1, #values do
    local v = values[i]
    if v then
      local num = tonumber(v)
      if num then sum = sum + num end
      -- se field estiver poluído com não-numérico, ignora (soma 0)
    end
  end
  return sum
end

-- ------------------------------------------------------------------
-- Se desabilitado, permite sem alterar estado
-- ------------------------------------------------------------------
if not enabled then
  return { 1, capacity, retry_hint, hash_key, cur_field, 0 }
end

-- ------------------------------------------------------------------
-- Apuração do uso atual na janela (ANTES do delta)
-- ------------------------------------------------------------------
local used_before = sliding_sum(hash_key, cur_bucket, n_buckets)

-- Negar se used_before + delta ultrapassar a capacidade
if (used_before + delta) > capacity then
  local remaining = math.max(capacity - used_before, 0)
  return { 0, remaining, retry_hint, hash_key, cur_field, used_before }
end

-- ------------------------------------------------------------------
-- Pré-flight (delta=0): somente checa; não altera estado
-- ------------------------------------------------------------------
if delta == 0 then
  local remaining = math.max(capacity - used_before, 0)
  return { 1, remaining, retry_hint, hash_key, cur_field, used_before }
end

-- ------------------------------------------------------------------
-- Consumo: incrementa o bucket atual (field do hash)
-- ------------------------------------------------------------------
redis.call("HINCRBY", hash_key, cur_field, delta)

-- TTL condicional: só estende quando necessário
-- PTTL: -2 (não existe), -1 (sem TTL), >=0 (ms restantes)
local pttl = redis.call("PTTL", hash_key)
if pttl == -2 or pttl == -1 or pttl < desired_ttl then
  redis.call("PEXPIRE", hash_key, desired_ttl)
end

-- Remaining após aplicar delta
local remaining = math.max(capacity - (used_before + delta), 0)

-- ------------------------------------------------------------------
-- Retorno padronizado (6 itens) — ver documentação no cabeçalho
-- ------------------------------------------------------------------
return { 1, remaining, retry_hint, hash_key, cur_field, used_before }
