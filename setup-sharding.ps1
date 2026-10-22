# ==============================================================================
# Automated MongoDB Sharded Cluster Deployment Script (Member 3)
# ==============================================================================

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Starting MongoDB Sharded Cluster Setup (Member 3)...     " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Start containers defined in docker-compose-sharding.yml
Write-Host "`n[Step 1/4] Starting Docker containers (Config Server, Shards, Mongos, Redis)..." -ForegroundColor Yellow
docker compose -f docker-compose-sharding.yml up -d

Write-Host "`nWaiting 10 seconds for MongoDB instances to initialize..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# 2. Initiate Config Server Replica Set
Write-Host "`n[Step 2/4] Initiating Config Server Replica Set (configsvr:27019)..." -ForegroundColor Yellow
docker exec -it library-configsvr mongosh --port 27019 --eval 'rs.initiate({ _id: "configsvr-rs", configsvr: true, members: [{ _id: 0, host: "configsvr:27019" }] })'

# 3. Initiate Shard Replica Sets
Write-Host "`n[Step 3/4] Initiating Shard 1 and Shard 2 Replica Sets..." -ForegroundColor Yellow
docker exec -it library-shard1 mongosh --port 27018 --eval 'rs.initiate({ _id: "shard1-rs", members: [{ _id: 0, host: "shard1:27018" }] })'
docker exec -it library-shard2 mongosh --port 27028 --eval 'rs.initiate({ _id: "shard2-rs", members: [{ _id: 0, host: "shard2:27028" }] })'

Start-Sleep -Seconds 5

# 4. Configure Mongos Router
Write-Host "`n[Step 4/4] Configuring Mongos Query Router & Sharding Collections..." -ForegroundColor Yellow
docker cp init-sharding.js library-mongos:/init-sharding.js
docker exec -it library-mongos mongosh --port 27017 /init-sharding.js

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " MongoDB Sharded Cluster Successfully Configured!          " -ForegroundColor Green
Write-Host " Mongos router listening on: localhost:27017               " -ForegroundColor Green
Write-Host " Redis cache listening on:   localhost:6379                " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
