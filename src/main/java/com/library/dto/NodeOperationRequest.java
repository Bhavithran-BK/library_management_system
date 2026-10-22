package com.library.dto;

public class NodeOperationRequest {
    private String nodeName;

    public NodeOperationRequest() {}

    public NodeOperationRequest(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }
}
