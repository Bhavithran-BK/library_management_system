package com.library.service;

import com.library.dto.BenchmarkRequest;
import com.library.dto.BenchmarkResultDto;
import com.library.idgenerator.IdGenerator;
import com.library.idgenerator.IdGeneratorService;
import com.library.idgenerator.IdPrefix;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Service
public class BenchmarkService {

    private final IdGeneratorService idGeneratorService;
    private final ApplicationContext applicationContext;

    public BenchmarkService(IdGeneratorService idGeneratorService, ApplicationContext applicationContext) {
        this.idGeneratorService = idGeneratorService;
        this.applicationContext = applicationContext;
    }

    public BenchmarkResultDto runBenchmark(BenchmarkRequest request) {
        int totalCount = Math.max(100, Math.min(request.getCount(), 200000));
        int threads = Math.max(1, Math.min(request.getThreads(), 32));

        IdGenerator generator;
        if ("tsid".equalsIgnoreCase(request.getStrategy())) {
            generator = (IdGenerator) applicationContext.getBean("tsidIdGenerator");
        } else {
            generator = (IdGenerator) applicationContext.getBean("snowflakeIdGenerator");
        }

        Set<String> generatedIds = ConcurrentHashMap.newKeySet(totalCount);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        int perThread = totalCount / threads;
        int remainder = totalCount % threads;

        long startTime = System.nanoTime();
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int countForThread = perThread + (i == 0 ? remainder : 0);
            executor.submit(() -> {
                try {
                    for (int j = 0; j < countForThread; j++) {
                        String id = generator.generateId(IdPrefix.BOOK);
                        generatedIds.add(id);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }

        long endTime = System.nanoTime();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        if (durationMs == 0) durationMs = 1;

        double idsPerSec = (totalCount * 1000.0) / durationMs;
        int collisions = totalCount - generatedIds.size();

        List<String> sampleIds = new ArrayList<>();
        int sampleLimit = 10;
        for (String id : generatedIds) {
            sampleIds.add(id);
            if (sampleIds.size() >= sampleLimit) break;
        }

        return new BenchmarkResultDto(
                totalCount,
                threads,
                durationMs,
                idsPerSec,
                collisions,
                generator.getStrategyName(),
                sampleIds
        );
    }
}
