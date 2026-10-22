package com.library.idgenerator;

/**
 * Strategy interface for Unique ID Generation.
 * Provides pluggability so the team can modify or replace the generation strategy
 * (e.g. Snowflake, TSID, UUIDv7, Nanoid) without altering core business services.
 */
public interface IdGenerator {

    /**
     * Generates a unique, collision-resistant identifier with the specified entity prefix.
     *
     * @param prefix Entity prefix indicating domain object type (BK, MEM, LN, RES, TXN)
     * @return Formatted unique identifier string
     */
    String generateId(IdPrefix prefix);

    /**
     * Returns the name of the generation strategy.
     */
    String getStrategyName();
}
