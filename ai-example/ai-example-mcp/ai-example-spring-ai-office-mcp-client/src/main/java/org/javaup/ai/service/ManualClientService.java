package org.javaup.ai.service;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
@Service
public class ManualClientService {

    private final ChatModel chatModel;
    private ChatClient chatClient;
    
    public ManualClientService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }
    
    @PostConstruct
    public void init() {
        // 创建Stdio Client
        //McpSyncClient stdioClient = createStdioClient();
        
        // 创建Streamable HTTP Client
        McpSyncClient httpClient = createStreamableHttpClient();
        
        // 汇总所有Client
        //List<McpSyncClient> clients = List.of(stdioClient, httpClient);
        List<McpSyncClient> clients = List.of(httpClient);
        
        // 构建工具回调
        SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
                .mcpClients(clients)
                .build();
        
        ToolCallback[] callbacks = provider.getToolCallbacks();
        
        // 构建ChatClient
        this.chatClient = ChatClient.builder(chatModel)
                .defaultToolCallbacks(callbacks)
                .build();
    }
    
    /**
     * 创建Stdio类型的Client
     */
    private McpSyncClient createStdioClient() {
        ServerParameters params = ServerParameters.builder("java")
                .args("-jar", "/path/to/local-server.jar")
                .build();
        
        StdioClientTransport transport = new StdioClientTransport(
                params, 
                McpJsonMapper.createDefault()
        );
        
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("local-client", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        
        // 初始化连接
        client.initialize();
        
        return client;
    }
    
    /**
     * 创建Streamable HTTP类型的Client
     */
    private McpSyncClient createStreamableHttpClient() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:7090")
                .endpoint("/mcp")
                .build();
        
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("http-client", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(60))
                .build();
        
        client.initialize();
        
        return client;
    }
    
    /**
     * 创建SSE类型的Client（了解即可）
     */
    private McpSyncClient createSseClient() {
        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder("http://localhost:7090")
                .sseEndpoint("/sse")
                .build();
        
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("sse-client", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(60))
                .build();
        
        client.initialize();
        
        return client;
    }
    
    public String chat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }
}
