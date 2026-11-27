package com.yuexin.bmp.agent.runtime.handler;

import java.util.Map;

public interface AgentHandler {
    String agentKey();
    String bizType();
    void handle(Map<String, Object> task);
}
