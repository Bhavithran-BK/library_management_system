-- Redis Lua Script: Token Bucket Rate Limiter (Member 4)
-- KEYS[1]: Rate limit key (e.g., rate:ip:127.0.0.1 or rate:user:john)
-- ARGV[1]: Bucket capacity (e.g., 100)
-- ARGV[2]: Refill rate per millisecond (e.g., 0.01 for 10 tokens/sec)
-- ARGV[3]: Current timestamp in milliseconds
-- ARGV[4]: Requested tokens (usually 1)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local data = redis.call("HMGET", key, "tokens", "last_updated")
local tokens = tonumber(data[1])
local last_updated = tonumber(data[2])

if tokens == nil then
    tokens = capacity
    last_updated = now
else
    local elapsed = math.max(0, now - last_updated)
    tokens = math.min(capacity, tokens + elapsed * refill_rate)
    last_updated = now
end

if tokens >= requested then
    tokens = tokens - requested
    redis.call("HMSET", key, "tokens", tokens, "last_updated", last_updated)
    local safe_rate = refill_rate > 0 and refill_rate or 0.001
    local ttl = math.max(1, math.ceil(capacity / safe_rate))
    redis.call("PEXPIRE", key, ttl)
    return {1, math.floor(tokens), 0} -- {allowed (1=true), remaining_tokens, retry_after_sec}
else
    local missing = requested - tokens
    local safe_refill_per_sec = (refill_rate * 1000) > 0 and (refill_rate * 1000) or 1
    local retry_after = math.max(1, math.ceil(missing / safe_refill_per_sec))
    redis.call("HMSET", key, "tokens", tokens, "last_updated", last_updated)
    local safe_rate = refill_rate > 0 and refill_rate or 0.001
    local ttl = math.max(1, math.ceil(capacity / safe_rate))
    redis.call("PEXPIRE", key, ttl)
    return {0, math.floor(tokens), retry_after} -- {allowed (0=false), remaining_tokens, retry_after_sec}
end
