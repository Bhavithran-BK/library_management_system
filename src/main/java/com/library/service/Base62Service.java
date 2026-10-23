package com.library.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base62 Encoding and Short Code Generation Service (Member 4).
 * Converts 64-bit distributed counters or hash values into compact, URL-safe alphanumeric strings.
 */
@Service
public class Base62Service {

    private static final Logger log = LoggerFactory.getLogger(Base62Service.class);

    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = BASE62.length();
    private static final long COUNTER_OFFSET = 100_000L; // Produces pleasant ~4-6 character codes
    private static final String REDIS_COUNTER_KEY = "url_shortener:counter";

    private final StringRedisTemplate stringRedisTemplate;
    private final AtomicLong inMemoryFallbackCounter = new AtomicLong(COUNTER_OFFSET);

    public Base62Service(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Encodes a positive 64-bit integer into a Base62 string.
     *
     * @param value Positive integer
     * @return Base62 encoded string
     */
    public String encode(long value) {
        if (value < 0) {
            value = Math.abs(value);
        }
        if (value == 0) {
            return String.valueOf(BASE62.charAt(0));
        }

        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            int remainder = (int) (value % BASE);
            sb.append(BASE62.charAt(remainder));
            value /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string back to a 64-bit integer.
     *
     * @param code Base62 string
     * @return Decoded integer value
     */
    public long decode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Short code cannot be null or empty");
        }

        long result = 0;
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            int index = BASE62.indexOf(ch);
            if (index == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: " + ch);
            }
            result = result * BASE + index;
        }
        return result;
    }

    /**
     * Generates a unique Base62 short code.
     * Tier 1: Uses atomic Redis counter INCR url_shortener:counter.
     * Fallback: Uses cryptographic SHA-256 hash truncated to 48-bit unsigned integer.
     *
     * @param originalUrl Original destination target URL
     * @param bookId Optional associated book identifier
     * @return Short alphanumeric code
     */
    public String generateShortCode(String originalUrl, String bookId) {
        try {
            Long nextVal = stringRedisTemplate.opsForValue().increment(REDIS_COUNTER_KEY);
            if (nextVal != null) {
                long counterWithOffset = nextVal + COUNTER_OFFSET;
                return encode(counterWithOffset);
            }
        } catch (Exception ex) {
            log.warn("Redis unavailable for shortener counter, falling back to hash/in-memory: {}", ex.getMessage());
        }

        // Fallback option: Hash input or atomic fallback counter
        try {
            String input = (originalUrl != null ? originalUrl : "") + ":" + (bookId != null ? bookId : "") + ":" + inMemoryFallbackCounter.incrementAndGet();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            long val = 0;
            for (int i = 0; i < 6; i++) {
                val = (val << 8) | (hash[i] & 0xFF);
            }
            val = Math.abs(val) % 56_800_235_584L; // 62^6
            return encode(val > 0 ? val : inMemoryFallbackCounter.get());
        } catch (NoSuchAlgorithmException e) {
            return encode(inMemoryFallbackCounter.incrementAndGet());
        }
    }
}
