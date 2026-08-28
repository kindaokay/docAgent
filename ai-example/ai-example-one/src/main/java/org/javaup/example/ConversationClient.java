package org.javaup.example;

import com.google.gson.*;
import okhttp3.*;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 客户端组件
 * @author: 阿星不是程序员
 **/
/**
 * 多轮对话客户端
 * 维护对话历史，实现连续对话
 */
public class ConversationClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    private static final String API_KEY = "设置成你的apiKey";
    private static final String MODEL = "Qwen/Qwen3.5-122B-A10B";
    private static final boolean ENABLE_THINKING = false;
    private static final int MAX_TOKENS = 256;
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 180;
    private static final int CALL_TIMEOUT_SECONDS = 180;

    /**
     * 最大历史消息数（防止上下文过长）
     * */
    private static final int MAX_HISTORY_SIZE = 20;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String systemPrompt;
    private final List<Message> conversationHistory;

    /**
     * 消息类
     */
    public static class Message {
        public String role;
        public String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public ConversationClient(String systemPrompt) {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

        this.gson = new Gson();
        this.systemPrompt = systemPrompt;
        this.conversationHistory = new ArrayList<>();
    }

    /**
     * 发送消息并获取回复
     */
    public String send(String userMessage) throws IOException {
        List<Message> requestMessages = buildRequestMessages(userMessage);

        // 1. 构建请求
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", MODEL);
        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", MAX_TOKENS);
        requestBody.addProperty("stream", false);
        requestBody.addProperty("enable_thinking", ENABLE_THINKING);

        // 构建 messages：system + 历史对话
        JsonArray messages = new JsonArray();

        // 添加 system 消息
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);

        // 添加历史对话（最近的 MAX_HISTORY_SIZE 条）
        for (Message msg : requestMessages) {
            JsonObject msgJson = new JsonObject();
            msgJson.addProperty("role", msg.role);
            msgJson.addProperty("content", msg.content);
            messages.add(msgJson);
        }

        requestBody.add("messages", messages);

        // 3. 发送请求
        Request request = new Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer " + API_KEY)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(
                requestBody.toString(),
                JSON
            ))
            .build();

        long requestStart = System.nanoTime();
        try (Response response = httpClient.newCall(request).execute()) {
            String traceId = response.header("x-siliconcloud-trace-id");
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                throw new IOException(String.format(
                    "请求失败: %d, traceId=%s, body=%s",
                    response.code(),
                    traceId != null ? traceId : "N/A",
                    errorBody
                ));
            }

            if (response.body() == null) {
                throw new IOException("API 响应体为空");
            }

            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);

            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                throw new IOException("响应中没有 choices");
            }

            String answer = json.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();

            // 4. 请求成功后再提交本轮历史，避免失败重试造成重复上下文
            conversationHistory.add(new Message("user", userMessage));
            conversationHistory.add(new Message("assistant", answer));

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStart);
            System.out.printf("[请求耗时] %d ms, traceId=%s%n",
                elapsedMs,
                traceId != null ? traceId : "N/A");

            // 打印 Token 使用情况
            if (json.has("usage")) {
                JsonObject usage = json.getAsJsonObject("usage");
                System.out.printf("[Token] 本轮: %d, 历史消息数: %d%n",
                    usage.get("total_tokens").getAsInt(),
                    conversationHistory.size());
            }

            String finishReason = getNullableString(choices.get(0).getAsJsonObject(), "finish_reason");
            if ("length".equals(finishReason)) {
                System.out.printf("[提示] 输出触发 max_tokens=%d 上限，如需更完整回答可调大该值，同时建议同步调大 readTimeout/callTimeout。%n",
                    MAX_TOKENS);
            }

            return answer;
        } catch (SocketTimeoutException e) {
            throw new SocketTimeoutException(String.format(
                "多轮对话调用超时（%d 秒）。当前示例已默认关闭 enable_thinking；如果改回 true 或调大 max_tokens，建议同步调大 readTimeout/callTimeout，或直接改成流式输出。原始错误: %s",
                CALL_TIMEOUT_SECONDS,
                e.getMessage()
            ));
        }
    }

    private List<Message> buildRequestMessages(String userMessage) {
        int startIndex = Math.max(0, conversationHistory.size() - MAX_HISTORY_SIZE);
        List<Message> requestMessages = new ArrayList<>(conversationHistory.subList(startIndex, conversationHistory.size()));
        requestMessages.add(new Message("user", userMessage));
        return requestMessages;
    }

    private static String getNullableString(JsonObject parent, String memberName) {
        if (parent == null || !parent.has(memberName)) {
            return null;
        }
        JsonElement element = parent.get(memberName);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    /**
     * 清空对话历史
     */
    public void clearHistory() {
        conversationHistory.clear();
        System.out.println("对话历史已清空");
    }

    /**
     * 获取对话历史
     */
    public List<Message> getHistory() {
        return new ArrayList<>(conversationHistory);
    }

    public static void main(String[] args) throws IOException {
        String systemPrompt = """
            你是一个友好的编程助手。
            记住用户之前说过的话，在对话中保持连贯性。
            回答要简洁明了。
            """;

        ConversationClient client = new ConversationClient(systemPrompt);
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== 多轮对话演示 ===");
        System.out.println("输入 'quit' 退出，输入 'clear' 清空历史\n");

        while (true) {
            System.out.print("你: ");
            String input = scanner.nextLine().trim();

            if ("quit".equalsIgnoreCase(input)) {
                System.out.println("再见！");
                break;
            }

            if ("clear".equalsIgnoreCase(input)) {
                client.clearHistory();
                continue;
            }

            if (input.isEmpty()) {
                continue;
            }

            try {
                System.out.printf("助手: 正在思考... [model=%s, enableThinking=%s]%n",
                    MODEL,
                    ENABLE_THINKING);
                String answer = client.send(input);
                System.out.println("\n助手: " + answer + "\n");
            } catch (IOException e) {
                System.err.println("发生错误: " + e.getMessage());
            }
        }

        scanner.close();
    }
}
