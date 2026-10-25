package com.library.distribution;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Thread-safe Consistent Hash Ring Router with Virtual Nodes (Member 3).
 * Routes keys across distributed storage nodes, balancing load and
 * minimizing key migration during node additions/removals.
 */
public class ConsistentHashRouter {

    private final int virtualNodesPerNode;
    private final ConcurrentSkipListMap<Long, VirtualNode> ring;
    private final Set<String> physicalNodes;
    private final Map<String, List<Long>> nodeToHashes;

    public ConsistentHashRouter(int virtualNodesPerNode, Collection<String> initialNodes) {
        if (virtualNodesPerNode <= 0) {
            throw new IllegalArgumentException("Virtual nodes must be greater than 0");
        }
        this.virtualNodesPerNode = virtualNodesPerNode;
        this.ring = new ConcurrentSkipListMap<>();
        this.physicalNodes = ConcurrentHashMap.newKeySet();
        this.nodeToHashes = new ConcurrentHashMap<>();

        if (initialNodes != null) {
            for (String node : initialNodes) {
                addNode(node);
            }
        }
    }

    public ConsistentHashRouter(int virtualNodesPerNode) {
        this(virtualNodesPerNode, Collections.emptyList());
    }

    public synchronized void addNode(String nodeName) {
        if (nodeName == null || nodeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Node name cannot be blank");
        }
        String cleanNode = nodeName.trim();
        if (physicalNodes.contains(cleanNode)) {
            return;
        }

        physicalNodes.add(cleanNode);
        List<Long> hashes = new ArrayList<>(virtualNodesPerNode);

        for (int i = 0; i < virtualNodesPerNode; i++) {
            String vNodeKey = cleanNode + "#VN" + i;
            long hash = MurmurHash3.hash32(vNodeKey);
            // Handle virtual node hash collision on the ring
            while (ring.containsKey(hash)) {
                hash = (hash + 1) & 0xFFFFFFFFL;
            }
            VirtualNode vNode = new VirtualNode(cleanNode, i, hash);
            ring.put(hash, vNode);
            hashes.add(hash);
        }

        nodeToHashes.put(cleanNode, hashes);
    }

    public synchronized boolean removeNode(String nodeName) {
        if (nodeName == null) return false;
        String cleanNode = nodeName.trim();
        if (!physicalNodes.contains(cleanNode)) {
            return false;
        }

        List<Long> hashes = nodeToHashes.remove(cleanNode);
        if (hashes != null) {
            for (Long hash : hashes) {
                ring.remove(hash);
            }
        }
        physicalNodes.remove(cleanNode);
        return true;
    }

    public boolean hasNode(String nodeName) {
        return nodeName != null && physicalNodes.contains(nodeName.trim());
    }

    public Set<String> getNodes() {
        return Collections.unmodifiableSet(new TreeSet<>(physicalNodes));
    }

    public int getNodeCount() {
        return physicalNodes.size();
    }

    public int getVirtualNodesPerNode() {
        return virtualNodesPerNode;
    }

    public int getTotalVirtualNodes() {
        return ring.size();
    }

    /**
     * Resolves the target storage node for a given key on the consistent hash ring.
     */
    public RouteResult route(String key) {
        if (physicalNodes.isEmpty()) {
            throw new IllegalStateException("Consistent hash ring is empty (no active nodes)");
        }
        if (key == null) {
            key = "";
        }

        long keyHash = MurmurHash3.hash32(key);

        // Find the first virtual node whose hash is >= keyHash (clockwise on ring)
        Map.Entry<Long, VirtualNode> entry = ring.ceilingEntry(keyHash);
        if (entry == null) {
            // Wrap around to the start of the circular ring
            entry = ring.firstEntry();
        }

        VirtualNode targetVNode = entry.getValue();
        double ringPositionPercent = (keyHash / 4294967295.0) * 100.0;

        return new RouteResult(
                key,
                keyHash,
                targetVNode.physicalNode(),
                targetVNode.nodeName(),
                targetVNode.hash(),
                Math.round(ringPositionPercent * 100.0) / 100.0
        );
    }

    /**
     * Computes the distribution of keys across nodes and uniformity statistics.
     */
    public DistributionAnalysis analyzeDistribution(List<String> keys) {
        if (keys == null || keys.isEmpty() || physicalNodes.isEmpty()) {
            return new DistributionAnalysis(Collections.emptyMap(), 0, 0, 0, 0.0, 0.0);
        }

        Map<String, Integer> counts = new HashMap<>();
        for (String node : physicalNodes) {
            counts.put(node, 0);
        }

        for (String key : keys) {
            RouteResult res = route(key);
            counts.put(res.assignedNode(), counts.getOrDefault(res.assignedNode(), 0) + 1);
        }

        int totalKeys = keys.size();
        double mean = (double) totalKeys / physicalNodes.size();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        double sumSqDiff = 0.0;

        for (int count : counts.values()) {
            min = Math.min(min, count);
            max = Math.max(max, count);
            sumSqDiff += Math.pow(count - mean, 2);
        }

        double stdDev = Math.sqrt(sumSqDiff / physicalNodes.size());
        double coefficientOfVariation = mean > 0 ? (stdDev / mean) * 100.0 : 0.0;

        return new DistributionAnalysis(counts, totalKeys, min, max, stdDev, coefficientOfVariation);
    }

    /**
     * Simulates adding a new node and measures key migration against the theoretical 1/(N+1) expectation.
     */
    public RebalanceResult simulateNodeAddition(String newNode, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return new RebalanceResult(newNode, "ADD", 0, 0, 0.0, 0.0, Collections.emptyMap(), Collections.emptyMap());
        }

        int nBefore = physicalNodes.size();
        double theoreticalRemapPercent = (1.0 / (nBefore + 1.0)) * 100.0;

        // Current routing
        Map<String, String> beforeRouting = new HashMap<>();
        Map<String, Integer> beforeDistribution = new HashMap<>();
        for (String node : physicalNodes) {
            beforeDistribution.put(node, 0);
        }
        for (String key : keys) {
            String node = route(key).assignedNode();
            beforeRouting.put(key, node);
            beforeDistribution.put(node, beforeDistribution.get(node) + 1);
        }

        // Clone ring and add node
        ConsistentHashRouter testRouter = this.copy();
        testRouter.addNode(newNode);

        int movedKeys = 0;
        Map<String, Integer> afterDistribution = new HashMap<>();
        for (String node : testRouter.getNodes()) {
            afterDistribution.put(node, 0);
        }
        Map<String, Integer> movedFromNodes = new HashMap<>();

        for (String key : keys) {
            String newNodeAssigned = testRouter.route(key).assignedNode();
            afterDistribution.put(newNodeAssigned, afterDistribution.get(newNodeAssigned) + 1);

            String oldNode = beforeRouting.get(key);
            if (!newNodeAssigned.equals(oldNode)) {
                movedKeys++;
                movedFromNodes.put(oldNode, movedFromNodes.getOrDefault(oldNode, 0) + 1);
            }
        }

        double actualRemapPercent = ((double) movedKeys / keys.size()) * 100.0;

        return new RebalanceResult(
                newNode,
                "ADD",
                keys.size(),
                movedKeys,
                Math.round(actualRemapPercent * 100.0) / 100.0,
                Math.round(theoreticalRemapPercent * 100.0) / 100.0,
                beforeDistribution,
                afterDistribution
        );
    }

    /**
     * Simulates removing a node and measures key migration to remaining nodes against the theoretical 1/N expectation.
     */
    public RebalanceResult simulateNodeRemoval(String nodeToRemove, List<String> keys) {
        if (keys == null || keys.isEmpty() || !physicalNodes.contains(nodeToRemove)) {
            return new RebalanceResult(nodeToRemove, "REMOVE", 0, 0, 0.0, 0.0, Collections.emptyMap(), Collections.emptyMap());
        }

        int nBefore = physicalNodes.size();
        double theoreticalRemapPercent = (1.0 / (double) nBefore) * 100.0;

        Map<String, String> beforeRouting = new HashMap<>();
        Map<String, Integer> beforeDistribution = new HashMap<>();
        for (String node : physicalNodes) {
            beforeDistribution.put(node, 0);
        }
        for (String key : keys) {
            String node = route(key).assignedNode();
            beforeRouting.put(key, node);
            beforeDistribution.put(node, beforeDistribution.get(node) + 1);
        }

        ConsistentHashRouter testRouter = this.copy();
        testRouter.removeNode(nodeToRemove);

        int movedKeys = 0;
        Map<String, Integer> afterDistribution = new HashMap<>();
        for (String node : testRouter.getNodes()) {
            afterDistribution.put(node, 0);
        }

        for (String key : keys) {
            String newNodeAssigned = testRouter.route(key).assignedNode();
            afterDistribution.put(newNodeAssigned, afterDistribution.get(newNodeAssigned) + 1);

            String oldNode = beforeRouting.get(key);
            if (!newNodeAssigned.equals(oldNode)) {
                movedKeys++;
            }
        }

        double actualRemapPercent = ((double) movedKeys / keys.size()) * 100.0;

        return new RebalanceResult(
                nodeToRemove,
                "REMOVE",
                keys.size(),
                movedKeys,
                Math.round(actualRemapPercent * 100.0) / 100.0,
                Math.round(theoreticalRemapPercent * 100.0) / 100.0,
                beforeDistribution,
                afterDistribution
        );
    }

    public List<com.library.dto.RingPointDto> getRingPoints() {
        List<com.library.dto.RingPointDto> points = new ArrayList<>(ring.size());
        for (Map.Entry<Long, VirtualNode> entry : ring.entrySet()) {
            double percent = (entry.getKey() / 4294967295.0) * 100.0;
            points.add(new com.library.dto.RingPointDto(
                    entry.getValue().physicalNode(),
                    entry.getValue().nodeName(),
                    entry.getKey(),
                    Math.round(percent * 100.0) / 100.0
            ));
        }
        return points;
    }

    public ConsistentHashRouter copy() {
        ConsistentHashRouter copy = new ConsistentHashRouter(this.virtualNodesPerNode);
        for (String node : this.physicalNodes) {
            copy.addNode(node);
        }
        return copy;
    }

    // Records
    public record VirtualNode(String physicalNode, int replicaIndex, long hash) {
        public String nodeName() {
            return physicalNode + "#VN" + replicaIndex;
        }
    }

    public record RouteResult(
            String key,
            long keyHash,
            String assignedNode,
            String virtualNode,
            long virtualNodeHash,
            double ringPositionPercent
    ) {}

    public record DistributionAnalysis(
            Map<String, Integer> perNodeCounts,
            int totalKeys,
            int minKeys,
            int maxKeys,
            double standardDeviation,
            double coefficientOfVariationPercent
    ) {}

    public record RebalanceResult(
            String targetNode,
            String operation,
            int totalKeys,
            int movedKeys,
            double actualRemapPercent,
            double theoreticalRemapPercent,
            Map<String, Integer> distributionBefore,
            Map<String, Integer> distributionAfter
    ) {}
}
