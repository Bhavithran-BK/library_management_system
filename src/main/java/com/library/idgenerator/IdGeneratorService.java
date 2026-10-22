package com.library.idgenerator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service providing ID generation to all modules.
 * Decouples domain logic from concrete ID generation algorithms.
 */
@Service
public class IdGeneratorService {

    private final Map<String, IdGenerator> generators;
    private volatile IdGenerator activeGenerator;

    public IdGeneratorService(
            ApplicationContext context,
            @Value("${app.id-generator.strategy:snowflake}") String defaultStrategy) {
        this.generators = context.getBeansOfType(IdGenerator.class);
        selectStrategy(defaultStrategy);
    }

    public synchronized void selectStrategy(String strategy) {
        if ("tsid".equalsIgnoreCase(strategy)) {
            this.activeGenerator = generators.get("tsidIdGenerator");
        } else {
            this.activeGenerator = generators.get("snowflakeIdGenerator");
        }
        if (this.activeGenerator == null && !generators.isEmpty()) {
            this.activeGenerator = generators.values().iterator().next();
        }
    }

    public String generateBookId() {
        return activeGenerator.generateId(IdPrefix.BOOK);
    }

    public String generateMemberId() {
        return activeGenerator.generateId(IdPrefix.MEMBER);
    }

    public String generateLoanId() {
        return activeGenerator.generateId(IdPrefix.LOAN);
    }

    public String generateReservationId() {
        return activeGenerator.generateId(IdPrefix.RESERVATION);
    }

    public String generateTransactionId() {
        return activeGenerator.generateId(IdPrefix.TRANSACTION);
    }

    public String getActiveStrategyName() {
        return activeGenerator != null ? activeGenerator.getStrategyName() : "Unknown";
    }

    public IdGenerator getActiveGenerator() {
        return activeGenerator;
    }
}
