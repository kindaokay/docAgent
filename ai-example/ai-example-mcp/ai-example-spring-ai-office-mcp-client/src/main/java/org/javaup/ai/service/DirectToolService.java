package org.javaup.ai.service;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
@Service
public class DirectToolService {

    private final List<McpSyncClient> mcpClients;
    
    public DirectToolService(List<McpSyncClient> mcpClients) {
        this.mcpClients = mcpClients;
    }
    
    /**
     * 直接调用指定Server的工具
     */
    public String callTool(String serverName, String toolName, Map<String, Object> params) {
        for (McpSyncClient client : mcpClients) {
            // 通过clientInfo或serverInfo判断是哪个Server
            McpSchema.Implementation clientInfo = client.getClientInfo();
            
            if (clientInfo.name().contains(serverName)) {
                // 构建调用请求
                McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
                        .name(toolName)
                        .arguments(params)
                        .build();
                
                // 执行调用
                McpSchema.CallToolResult result = client.callTool(request);
                
                // 返回结果
                return result.content().toString();
            }
        }
        
        throw new RuntimeException("未找到Server: " + serverName);
    }
    
    /**
     * 查询员工考勤的便捷方法
     */
    public String checkAttendance(String employeeId, String month) {
        Map<String, Object> params = new HashMap<>();
        params.put("employeeId", employeeId);
        params.put("month", month);
        
        return callTool("office", "checkAttendance", params);
    }
}