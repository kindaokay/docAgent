package org.javaup.example;

import com.google.gson.*;
import okhttp3.*;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 客户端组件
 * @author: 阿星不是程序员
 **/
/**
 * 大模型 API 非流式调用示例
 * 演示如何调用硅基流动的 API 进行对话
 */
public class LLMClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /**
     * API 配置
     * */
    private static final String API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    /**
     * 替换成你的 API Key
     * */
    private static final String API_KEY = "设置成你的apiKey";

    private static final String MODEL = "Qwen/Qwen3.5-122B-A10B";
    /**
     * 硅基流动文档中该模型支持 thinking 模式，默认开启时非流式调用可能等待较久。
     * 这里示例默认关闭，以减少直接运行 demo 时的超时概率。
     */
    private static final boolean ENABLE_THINKING = false;
    private static final int MAX_TOKENS = 512;
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 180;
    private static final int WRITE_TIMEOUT_SECONDS = 30;
    private static final int CALL_TIMEOUT_SECONDS = 180;

    /**
     * HTTP 客户端（复用以提高性能）
     * */
    private final OkHttpClient httpClient;
    private final Gson gson;

    public LLMClient() {
        // 配置 HTTP 客户端
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }

    /**
     * 发送对话请求
     * 
     * @param systemPrompt 系统提示词，定义模型角色
     * @param userMessage  用户消息
     * @return 模型的回复
     */
    public String chat(String systemPrompt, String userMessage) throws IOException {
        // 1. 构建请求体
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", MODEL);
        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", MAX_TOKENS);
        requestBody.addProperty("stream", false);
        requestBody.addProperty("enable_thinking", ENABLE_THINKING);

        // 构建 messages 数组
        JsonArray messages = new JsonArray();

        // 添加 system 消息
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemPrompt);
            messages.add(systemMsg);
        }

        // 添加 user 消息
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        requestBody.add("messages", messages);

        // 2. 构建 HTTP 请求
        Request request = new Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer " + API_KEY)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(
                requestBody.toString(),
                JSON
            ))
            .build();

        // 3. 发送请求并处理响应
        long requestStart = System.nanoTime();
        try (Response response = httpClient.newCall(request).execute()) {
            String traceId = response.header("x-siliconcloud-trace-id");
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStart);

            // 检查响应状态
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                throw new IOException(String.format(
                    "API 请求失败: %d, traceId=%s, body=%s",
                    response.code(),
                    traceId != null ? traceId : "N/A",
                    errorBody
                ));
            }

            // 解析响应 JSON
            if (response.body() == null) {
                throw new IOException("API 响应体为空");
            }
            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            // 提取回答内容
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                throw new IOException("响应中没有 choices");
            }

            if (traceId != null) {
                System.out.printf("[请求耗时] %d ms, traceId=%s%n", elapsedMs, traceId);
            } else {
                System.out.printf("[请求耗时] %d ms%n", elapsedMs);
            }

            String answer = choices.get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();

            // 打印 Token 使用情况
            if (json.has("usage")) {
                JsonObject usage = json.getAsJsonObject("usage");
                System.out.printf("[Token 使用] 输入: %d, 输出: %d, 总计: %d%n",
                    usage.get("prompt_tokens").getAsInt(),
                    usage.get("completion_tokens").getAsInt(),
                    usage.get("total_tokens").getAsInt());
            }

            if (choices.get(0).getAsJsonObject().has("finish_reason")) {
                String finishReason = choices.get(0).getAsJsonObject()
                    .get("finish_reason").getAsString();
                if ("length".equals(finishReason)) {
                    System.out.printf("[提示] 输出触发 max_tokens=%d 上限，如需更完整回答可调大该值，同时建议同步调大 readTimeout/callTimeout。%n",
                        MAX_TOKENS);
                }
            }

            return answer;
        } catch (SocketTimeoutException e) {
            throw new SocketTimeoutException(String.format(
                "调用硅基流动超时（%d 秒）。当前示例已默认关闭 enable_thinking；如果你改回 true 或更换为更慢的推理模型，建议使用 stream=true，或继续增大 readTimeout/callTimeout。原始错误: %s",
                CALL_TIMEOUT_SECONDS,
                e.getMessage()
            ));
        }
    }

    public static void main(String[] args) {
        LLMClient client = new LLMClient();

        String systemPrompt = """
            你是一个专业的 Java 技术顾问，擅长解答 Java 相关的技术问题。
            回答要准确、简洁，如果涉及代码，请给出示例。
            如果不确定答案，请明确说明。
            """;

        String userMessage = "请解释一下 Java 中的 volatile 关键字有什么作用？";

        try {
            System.out.println("发送问题: " + userMessage);
            System.out.printf("等待回复... [model=%s, enableThinking=%s]%n%n", MODEL, ENABLE_THINKING);

            String answer = client.chat(systemPrompt, userMessage);

            System.out.println("=== 模型回答 ===");
            System.out.println(answer);
        } catch (IOException e) {
            System.err.println("调用失败: " + e.getMessage());
        }
    }
}
