package com.library.dto;

import java.util.List;
import java.util.Map;

/**
 * Overview of Consistent Hashing and MongoDB Sharding status (Member 3).
 */
public record DistributionOverviewDto(
        ConsistentHashRingStatus hashRing,
        MongoShardingStatus mongoSharding,
        KeyDistributionStatus keyDistribution,
        CooperationModel cooperationModel
) {
    public record ConsistentHashRingStatus(
            int activeNodeCount,
            List<String> physicalNodes,
            int virtualNodesPerNode,
            int totalVirtualNodes,
            String hashAlgorithm
    ) {}

    public record MongoShardingStatus(
            boolean isShardedCluster,
            String clusterType,
            String connectionUri,
            List<ShardInfo> shards,
            Map<String, String> shardKeys,
            String configServer,
            String mongosRouter
    ) {}

    public record ShardInfo(
            String id,
            String host,
            String state,
            Map<String, Object> details
    ) {}

    public record KeyDistributionStatus(
            int totalKeys,
            Map<String, Integer> perNodeCounts,
            Map<String, Double> perNodePercentages,
            int minKeys,
            int maxKeys,
            double standardDeviation,
            double coefficientOfVariationPercent
    ) {}

    public record CooperationModel(
            String applicationRouterRole,
            String databaseShardingRole,
            String synergySummary
    ) {}
}
