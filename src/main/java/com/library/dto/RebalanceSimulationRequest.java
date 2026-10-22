package com.library.dto;

public class RebalanceSimulationRequest {
    private String nodeName;
    private String operation = "ADD"; // "ADD" or "REMOVE"
    private Integer keyCount; // if null or <=0, use real keys from db

    public RebalanceSimulationRequest() {}

    public RebalanceSimulationRequest(String nodeName, String operation, Integer keyCount) {
        this.nodeName = nodeName;
        this.operation = operation;
        this.keyCount = keyCount;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Integer getKeyCount() {
        return keyCount;
    }

    public void setKeyCount(Integer keyCount) {
        this.keyCount = keyCount;
    }
}
