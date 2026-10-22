package com.library.idgenerator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Distributed Snowflake ID Generator.
 * 
 * Bit Layout (64 bits):
 * - 1 sign bit (always 0)
 * - 41 bits timestamp (milliseconds since custom epoch) => ~69 years
 * - 5 bits datacenter ID (0-31)
 * - 5 bits worker ID (0-31)
 * - 12 bits sequence number (0-4095 per ms) => up to 4,096,000 IDs/sec/node
 * 
 * Result is converted to Base62 for compact URL-safe representation with domain prefix:
 * e.g., BK-7aB8c2D, MEM-7aB8c3E, LN-7aB8c4F
 */
@Component("snowflakeIdGenerator")
public class SnowflakeIdGenerator implements IdGenerator {

    // Custom epoch: 2024-01-01 00:00:00 UTC (1704067200000 ms)
    private static final long CUSTOM_EPOCH = 1704067200000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);       // 31
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS); // 31
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);         // 4095

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final String BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final long workerId;
    private final long datacenterId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    private final Object lock = new Object();

    public SnowflakeIdGenerator(
            @Value("${app.id-generator.worker-id:1}") long workerId,
            @Value("${app.id-generator.datacenter-id:1}") long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(String.format("Worker ID can't be greater than %d or less than 0", MAX_WORKER_ID));
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("Datacenter ID can't be greater than %d or less than 0", MAX_DATACENTER_ID));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    @Override
    public String generateId(IdPrefix prefix) {
        long rawId = nextRawId();
        String encoded = toBase62(rawId);
        return prefix.getPrefix() + "-" + encoded;
    }

    @Override
    public String getStrategyName() {
        return "Distributed Snowflake (Base62)";
    }

    /**
     * Generates raw 64-bit integer ID thread-safely.
     */
    public long nextRawId() {
        synchronized (lock) {
            long timestamp = timeGen();

            if (timestamp < lastTimestamp) {
                long offset = lastTimestamp - timestamp;
                if (offset <= 5) {
                    try {
                        lock.wait(offset << 1);
                        timestamp = timeGen();
                        if (timestamp < lastTimestamp) {
                            throw new RuntimeException(String.format("Clock moved backwards. Refusing to generate id for %d ms", lastTimestamp - timestamp));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Thread interrupted while waiting for clock", e);
                    }
                } else {
                    throw new RuntimeException(String.format("Clock moved backwards. Refusing to generate id for %d ms", offset));
                }
            }

            if (lastTimestamp == timestamp) {
                sequence = (sequence + 1) & SEQUENCE_MASK;
                if (sequence == 0) {
                    timestamp = tilNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }

            lastTimestamp = timestamp;

            return ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                    | (datacenterId << DATACENTER_ID_SHIFT)
                    | (workerId << WORKER_ID_SHIFT)
                    | sequence;
        }
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }

    private static String toBase62(long value) {
        if (value == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder(11);
        while (value > 0) {
            int remainder = (int) (value % 62);
            sb.append(BASE62_ALPHABET.charAt(remainder));
            value /= 62;
        }
        return sb.reverse().toString();
    }
}
