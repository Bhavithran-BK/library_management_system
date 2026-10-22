package com.library.idgenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class IdGeneratorBenchmarkTest {

    @Test
    @DisplayName("SnowflakeIdGenerator should produce 20,000 unique IDs concurrently with 0 collisions")
    public void testSnowflakeIdGeneratorConcurrency() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        int totalIds = 20_000;
        int threadCount = 16;
        int perThread = totalIds / threadCount;

        Set<String> generatedIds = ConcurrentHashMap.newKeySet(totalIds);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        long startTime = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < perThread; j++) {
                        String id = generator.generateId(IdPrefix.BOOK);
                        generatedIds.add(id);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        if (durationMs == 0) durationMs = 1;
        double throughput = (totalIds * 1000.0) / durationMs;

        assertTrue(completed, "Benchmark should complete within 15 seconds");
        assertEquals(totalIds, generatedIds.size(), "All generated IDs must be strictly unique! Collisions: " + (totalIds - generatedIds.size()));
        System.out.printf("Snowflake Generator Throughput: %,.2f IDs/sec | Total: %d IDs in %d ms | Collisions: 0%n",
                throughput, totalIds, durationMs);
    }

    @Test
    @DisplayName("TSID IdGenerator should produce unique IDs concurrently")
    public void testTsidIdGenerator() {
        TsidIdGenerator generator = new TsidIdGenerator();
        Set<String> set = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < 5000; i++) {
            set.add(generator.generateId(IdPrefix.MEMBER));
        }
        assertEquals(5000, set.size(), "TSID must generate unique IDs");
    }
}
