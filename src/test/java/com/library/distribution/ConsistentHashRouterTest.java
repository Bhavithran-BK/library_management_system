package com.library.distribution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ConsistentHashRouterTest {

    private ConsistentHashRouter router;

    @BeforeEach
    public void setup() {
        router = new ConsistentHashRouter(150, List.of("node-1", "node-2", "node-3", "node-4"));
    }

    @Test
    @DisplayName("Router initializes with correct physical and virtual node count")
    public void testInitialization() {
        assertEquals(4, router.getNodeCount());
        assertEquals(150, router.getVirtualNodesPerNode());
        assertEquals(600, router.getTotalVirtualNodes());
        assertTrue(router.hasNode("node-1"));
        assertTrue(router.hasNode("node-4"));
        assertFalse(router.hasNode("node-5"));
    }

    @Test
    @DisplayName("Deterministic routing: identical key always maps to the same node")
    public void testDeterministicRouting() {
        String testKey = "BK-SNOWFLAKE-99214";
        ConsistentHashRouter.RouteResult r1 = router.route(testKey);
        ConsistentHashRouter.RouteResult r2 = router.route(testKey);

        assertEquals(r1.assignedNode(), r2.assignedNode());
        assertEquals(r1.keyHash(), r2.keyHash());
        assertEquals(r1.virtualNode(), r2.virtualNode());
        assertTrue(router.hasNode(r1.assignedNode()));
    }

    @Test
    @DisplayName("Adding and removing nodes updates virtual nodes on ring")
    public void testNodeAdditionAndRemoval() {
        router.addNode("node-5");
        assertEquals(5, router.getNodeCount());
        assertEquals(750, router.getTotalVirtualNodes());
        assertTrue(router.hasNode("node-5"));

        boolean removed = router.removeNode("node-5");
        assertTrue(removed);
        assertEquals(4, router.getNodeCount());
        assertEquals(600, router.getTotalVirtualNodes());
        assertFalse(router.hasNode("node-5"));
    }

    @Test
    @DisplayName("Scale Demonstration: Adding 5th node remaps ~20% of keys (theoretical 1/(N+1)), keeping ~80% undisturbed")
    public void testScaleDemonstrationNodeAddition() {
        List<String> testKeys = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            testKeys.add("BK-TEST-ID-" + i);
        }

        ConsistentHashRouter.RebalanceResult result = router.simulateNodeAddition("node-5", testKeys);

        assertEquals("ADD", result.operation());
        assertEquals("node-5", result.targetNode());
        assertEquals(2000, result.totalKeys());

        // Theoretical remap is 1 / (4 + 1) = 20.0%
        assertEquals(20.0, result.theoreticalRemapPercent(), 0.01);

        // Actual remap should be close to 20% (e.g. 17% - 24%) with 150 vnodes
        assertTrue(result.actualRemapPercent() >= 16.0 && result.actualRemapPercent() <= 25.0,
                "Actual remapped percent should be close to 20%, but was: " + result.actualRemapPercent() + "%");

        // Verify that the new node actually received keys
        assertTrue(result.distributionAfter().get("node-5") > 0, "New node must receive keys");

        // Verify that total keys before == total keys after
        int totalBefore = result.distributionBefore().values().stream().mapToInt(Integer::intValue).sum();
        int totalAfter = result.distributionAfter().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(totalBefore, totalAfter);
    }

    @Test
    @DisplayName("Scale Demonstration: Removing a node from 4 nodes remaps ~25% of keys (theoretical 1/N)")
    public void testScaleDemonstrationNodeRemoval() {
        List<String> testKeys = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            testKeys.add("MEM-TEST-ID-" + i);
        }

        ConsistentHashRouter.RebalanceResult result = router.simulateNodeRemoval("node-4", testKeys);

        assertEquals("REMOVE", result.operation());
        assertEquals("node-4", result.targetNode());
        assertEquals(25.0, result.theoreticalRemapPercent(), 0.01);

        assertTrue(result.actualRemapPercent() >= 20.0 && result.actualRemapPercent() <= 30.0,
                "Actual remapped percent should be close to 25%, but was: " + result.actualRemapPercent() + "%");
    }

    @Test
    @DisplayName("Uniform distribution across virtual nodes")
    public void testDistributionUniformity() {
        List<String> testKeys = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            testKeys.add("KEY-SAMPLE-" + i);
        }

        ConsistentHashRouter.DistributionAnalysis analysis = router.analyzeDistribution(testKeys);
        assertEquals(5000, analysis.totalKeys());
        assertEquals(4, analysis.perNodeCounts().size());

        // Each node should have roughly 1250 keys (25%). Allow variation within reasonable bounds for hashing.
        for (int count : analysis.perNodeCounts().values()) {
            assertTrue(count > 1000 && count < 1500, "Node count should be well balanced around 1250: " + count);
        }

        // Coefficient of variation should be low (< 10%) with 150 virtual nodes
        assertTrue(analysis.coefficientOfVariationPercent() < 10.0,
                "Coefficient of variation should be < 10%, was: " + analysis.coefficientOfVariationPercent());
    }
}
