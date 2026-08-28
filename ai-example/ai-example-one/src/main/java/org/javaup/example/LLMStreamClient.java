package org.javaup.example;

import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料 
 * @description: 客户端组件
 * @author: 阿星不是程序员
 **/
/**
 * 大模型 API 流式调用示例
 * 实现类似 ChatGPT 的打字机效果
 */
public class LLMStreamClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    private static final String API_KEY = "设置成你的apiKey";
    private static final String MODEL = "Qwen/Qwen3.5-122B-A10B";
    /**
     * 该模型支持 thinking 模式；流式返回时 reasoning_content 可能出现，而 content 为 null。
     * 示例默认关闭 thinking，避免首次体验时既慢又需要额外解析思考流。
     */
    private static final boolean ENABLE_THINKING = false;
    private static final int MAX_TOKENS = 512;
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 300;
    private static final int WRITE_TIMEOUT_SECONDS = 30;
    private static final int CALL_TIMEOUT_SECONDS = 300;

    private final OkHttpClient httpClient;
    private final Gson gson;

    public LLMStreamClient() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

        this.gson = new Gson();
    }

    /**
     * 回调接口，用于处理流式数据
     */
    public interface StreamCallback {
        /**
         * 收到新的文本片段
         */
        void onContent(String content);

        /**
         * 流式传输完成
         */
        void onComplete(String fullContent);

        /**
         * 发生错误
         */
        void onError(Exception e);
    }

    /**
     * 流式对话
     */
    public void chatStream(String systemPrompt, String userMessage, StreamCallback callback) {
        // 1. 构建请求体
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", MODEL);
        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", MAX_TOKENS);
        requestBody.addProperty("stream", true);
        requestBody.addProperty("enable_thinking", ENABLE_THINKING);

        JsonArray messages = new JsonArray();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemPrompt);
            messages.add(systemMsg);
        }

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        requestBody.add("messages", messages);

        // 2. 构建请求
        Request request = new Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer " + API_KEY)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(RequestBody.create(
                requestBody.toString(),
                JSON
            ))
            .build();

        // 3. 异步执行请求
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                long requestStart = System.nanoTime();
                try (response) {
                    String traceId = response.header("x-siliconcloud-trace-id");
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No error body";
                        callback.onError(new IOException(String.format(
                            "API 请求失败: %d, traceId=%s, body=%s",
                            response.code(),
                            traceId != null ? traceId : "N/A",
                            errorBody
                        )));
                        return;
                    }

                    if (response.body() == null) {
                        callback.onError(new IOException("API 响应体为空"));
                        return;
                    }

                    StringBuilder fullContent = new StringBuilder();
                    String finishReason = null;

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream()))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank() || !line.startsWith("data:")) {
                                continue;
                            }

                            String data = line.substring(5).trim();
                            if ("[DONE]".equals(data)) {
                                break;
                            }

                            try {
                                JsonObject chunk = gson.fromJson(data, JsonObject.class);
                                JsonArray choices = chunk.getAsJsonArray("choices");
                                if (choices == null || choices.size() == 0) {
                                    continue;
                                }

                                JsonObject choice = choices.get(0).getAsJsonObject();
                                JsonObject delta = getAsJsonObject(choice, "delta");
                                if (delta == null) {
                                    continue;
                                }

                                String content = getNullableString(delta, "content");
                                if (content != null && !content.isEmpty()) {
                                    fullContent.append(content);
                                    callback.onContent(content);
                                }

                                String chunkFinishReason = getNullableString(choice, "finish_reason");
                                if (chunkFinishReason != null) {
                                    finishReason = chunkFinishReason;
                                }
                            } catch (JsonParseException | IllegalStateException e) {
                                // SSE 中可能夹杂非 JSON 行，或字段结构与普通 completion 不同，跳过即可。
                            }
                        }

                        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStart);
                        System.out.printf("%n[流式请求耗时] %d ms, traceId=%s%n",
                            elapsedMs,
                            traceId != null ? traceId : "N/A");
                        if ("length".equals(finishReason)) {
                            System.out.printf("[提示] 输出触发 max_tokens=%d 上限，如需更完整回答可调大该值。%n",
                                MAX_TOKENS);
                        }
                        callback.onComplete(fullContent.toString());
                    } catch (SocketTimeoutException e) {
                        callback.onError(new SocketTimeoutException(String.format(
                            "流式调用超时（%d 秒）。如果切换回 thinking 模式或增大 max_tokens，建议同步调大 readTimeout/callTimeout。原始错误: %s",
                            CALL_TIMEOUT_SECONDS,
                            e.getMessage()
                        )));
                    }
                } catch (IOException e) {
                    callback.onError(e);
                } catch (RuntimeException e) {
                    callback.onError(new IOException("解析流式响应失败: " + e.getMessage(), e));
                }
            }
        });
    }

    private static JsonObject getAsJsonObject(JsonObject parent, String memberName) {
        if (parent == null || !parent.has(memberName)) {
            return null;
        }
        JsonElement element = parent.get(memberName);
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
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

    public static void main(String[] args) throws InterruptedException {
        LLMStreamClient client = new LLMStreamClient();

        String systemPrompt = "你是一个编程助手，用简洁清晰的方式解答问题。";
        String userMessage = "用 Java 写一个单例模式的示例";

        System.out.println("发送问题: " + userMessage);
        System.out.printf("模型: %s, enableThinking=%s%n", MODEL, ENABLE_THINKING);
        System.out.println("\n=== 模型回答（流式）===\n");

        // 用于等待异步调用完成
        Object lock = new Object();

        client.chatStream(systemPrompt, userMessage, new LLMStreamClient.StreamCallback() {
            @Override
            public void onContent(String content) {
                // 实时打印收到的内容（打字机效果）
                System.out.print(content);
                System.out.flush();
            }

            @Override
            public void onComplete(String fullContent) {
                System.out.println("\n\n=== 回答完成 ===");
                System.out.println("总长度: " + fullContent.length() + " 字符");
                
                synchronized (lock) {
                    lock.notify();
                }
            }

            @Override
            public void onError(Exception e) {
                System.err.println("\n发生错误: " + e.getMessage());
                
                synchronized (lock) {
                    lock.notify();
                }
            }
        });

        // 等待异步调用完成
        synchronized (lock) {
            // 最多等待 5 分钟
            lock.wait(300000);  
        }
    }
}
