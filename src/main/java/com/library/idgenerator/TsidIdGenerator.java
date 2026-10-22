package com.library.idgenerator;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Alternative ID Generation Strategy: Time-Sorted Hybrid Identifier (TSID-like).
 * Useful for demonstrating pluggable ID generation strategies.
 */
@Component("tsidIdGenerator")
public class TsidIdGenerator implements IdGenerator {

    private static final String BASE32_CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private final SecureRandom random = new SecureRandom();
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public String generateId(IdPrefix prefix) {
        long time = System.currentTimeMillis();
        int count = counter.incrementAndGet() & 0x0FFF; // 12-bit counter
        int rnd = random.nextInt(0x0FFF);               // 12-bit entropy
        
        long combined = ((time & 0xFFFFFFFFFFFFL) << 16) | ((long) count << 4) | (rnd & 0x0F);
        return prefix.getPrefix() + "-" + toBase32(combined);
    }

    @Override
    public String getStrategyName() {
        return "Time-Sorted Identifier (TSID Base32)";
    }

    private static String toBase32(long val) {
        StringBuilder sb = new StringBuilder(13);
        while (val > 0) {
            sb.append(BASE32_CROCKFORD.charAt((int) (val % 32)));
            val /= 32;
        }
        return sb.reverse().toString();
    }
}
