package com.library.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.dto.NodeOperationRequest;
import com.library.dto.RebalanceSimulationRequest;
import com.library.service.DistributionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class DistributionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DistributionService distributionService;

    @Test
    @DisplayName("GET /api/admin/distribution returns valid status for Consistent Hash Ring and MongoDB Sharding")
    public void testGetDistributionOverview() throws Exception {
        mockMvc.perform(get("/api/admin/distribution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hashRing.activeNodeCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.hashRing.virtualNodesPerNode").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.mongoSharding.shardKeys").isMap())
                .andExpect(jsonPath("$.data.keyDistribution.perNodeCounts").isMap())
                .andExpect(jsonPath("$.data.cooperationModel.applicationRouterRole").exists())
                .andExpect(jsonPath("$.data.cooperationModel.databaseShardingRole").exists());
    }

    @Test
    @DisplayName("POST /api/admin/distribution/nodes and DELETE /api/admin/distribution/nodes/{name} manages ring topology")
    public void testAddAndRemoveNode() throws Exception {
        String testNode = "storage-node-test-controller";

        // Add node
        mockMvc.perform(post("/api/admin/distribution/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NodeOperationRequest(testNode))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nodeName").value(testNode));

        // Route a key to verify
        mockMvc.perform(get("/api/admin/distribution/route").param("key", "BK-TEST-KEY-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignedNode").exists())
                .andExpect(jsonPath("$.data.keyHash").isNumber());

        // Remove node
        mockMvc.perform(delete("/api/admin/distribution/nodes/" + testNode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/admin/distribution/rebalance-simulation computes theoretical vs actual remapped keys")
    public void testRebalanceSimulation() throws Exception {
        RebalanceSimulationRequest req = new RebalanceSimulationRequest("storage-node-sim", "ADD", 500);

        mockMvc.perform(post("/api/admin/distribution/rebalance-simulation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.operation").value("ADD"))
                .andExpect(jsonPath("$.data.totalKeys").value(500))
                .andExpect(jsonPath("$.data.theoreticalRemapPercent").isNumber())
                .andExpect(jsonPath("$.data.actualRemapPercent").isNumber())
                .andExpect(jsonPath("$.data.conclusion").exists());
    }

    @Test
    @DisplayName("POST /api/admin/distribution/benchmark runs uniformity test")
    public void testUniformityBenchmark() throws Exception {
        mockMvc.perform(post("/api/admin/distribution/benchmark").param("sampleSize", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sampleSize").value(1000))
                .andExpect(jsonPath("$.data.balanceScorePercent").isNumber());
    }
}
