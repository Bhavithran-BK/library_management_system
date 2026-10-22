// ==============================================================================
// MongoDB Sharded Cluster Initialization & Verification Script
// Member 3 — Distributed Storage & Consistent Hashing
// ==============================================================================
// This script configures and verifies horizontal data partitioning across
// MongoDB shards using hashed shard keys.
// ==============================================================================

print("\n--- [Step 1] Initializing Config Server Replica Set (port 27019) ---");
try {
    let cfg = rs.status();
    print("Config server replica set already initiated.");
} catch (e) {
    rs.initiate({
        _id: "configsvr-rs",
        configsvr: true,
        members: [{ _id: 0, host: "configsvr:27019" }]
    });
    print("Config server replica set initiated successfully.");
}

print("\n--- [Step 2] Initializing Shard 1 Replica Set (port 27018) ---");
// Executed on shard1 node:
// rs.initiate({ _id: "shard1-rs", members: [{ _id: 0, host: "shard1:27018" }] });

print("\n--- [Step 3] Initializing Shard 2 Replica Set (port 27028) ---");
// Executed on shard2 node:
// rs.initiate({ _id: "shard2-rs", members: [{ _id: 0, host: "shard2:27028" }] });

print("\n--- [Step 4] Adding Shard Replica Sets to Mongos Query Router ---");
try {
    sh.addShard("shard1-rs/shard1:27018");
    print("Added shard1-rs to cluster.");
} catch (e) {
    print("Shard 1 already added or error: " + e.message);
}

try {
    sh.addShard("shard2-rs/shard2:27028");
    print("Added shard2-rs to cluster.");
} catch (e) {
    print("Shard 2 already added or error: " + e.message);
}

print("\n--- [Step 5] Enabling Sharding on Database 'library_db' ---");
sh.enableSharding("library_db");

print("\n--- [Step 6] Sharding Collections with Hashed Shard Keys ---");
// 1. Shard books collection on bookId (hashed)
// Justification: Snowflake IDs have time-based prefixes. Ranged sharding would cause
// write hotspotting on the latest shard. Hashed sharding guarantees completely uniform
// write/read distribution across all physical disks.
try {
    sh.shardCollection("library_db.books", { "bookId": "hashed" });
    print("Sharded collection library_db.books on { bookId: 'hashed' }");
} catch (e) {
    print("books collection sharding notice: " + e.message);
}

// 2. Shard members collection on memberId (hashed)
// Justification: Distributes member accounts and authentication lookups evenly.
try {
    sh.shardCollection("library_db.members", { "memberId": "hashed" });
    print("Sharded collection library_db.members on { memberId: 'hashed' }");
} catch (e) {
    print("members collection sharding notice: " + e.message);
}

print("\n--- [Step 7] Shard Verification & Chunk Distribution ---");
printjson(sh.status());

print("\n--- Record Distribution per Shard: Books ---");
try {
    printjson(db.getSiblingDB("library_db").books.getShardDistribution());
} catch (e) {
    print("Shard distribution info available once documents are populated.");
}

print("\n--- Record Distribution per Shard: Members ---");
try {
    printjson(db.getSiblingDB("library_db").members.getShardDistribution());
} catch (e) {
    print("Shard distribution info available once documents are populated.");
}
