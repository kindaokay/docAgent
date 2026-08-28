# ai-example-spring-ai-memory

这个模块用来演示 3 种会话策略：

- 无记忆
- 滑动窗口记忆
- 摘要压缩记忆

还额外提供了一个对比接口，方便一次性把三种策略的效果跑出来。

## 启动方式

先准备环境变量：

```bash
export SILICONFLOW_API_KEY=你的Key
```

在项目根目录执行：

```bash
mvn -pl ai-example/ai-example-memory/ai-example-spring-ai-memory -am spring-boot:run
```

默认端口：

```text
http://localhost:8091
```

## 演示顺序建议

建议现场按下面这个顺序演示，节奏会比较顺：

1. 先演示无记忆，快速让大家看到“第二轮就断片”。
2. 再演示滑动窗口，展示“最近几轮能记住”。
3. 然后演示摘要压缩，展示“历史不会完全丢失”。
4. 最后调 `/memory/compare`，一次性把三种策略结果展开。

## 接口示例

### 1. 无记忆模式

```bash
curl -G 'http://localhost:8091/memory/no-memory/chat' \
  --data-urlencode 'question=Spring Bean 的作用域有哪些？'
```

继续问第二轮：

```bash
curl -G 'http://localhost:8091/memory/no-memory/chat' \
  --data-urlencode 'question=那它默认是哪一种？'
```

### 2. 滑动窗口模式

第一轮：

```bash
curl -G 'http://localhost:8091/memory/sliding-window/chat' \
  --data-urlencode 'sessionId=demo-memory-001' \
  --data-urlencode 'question=Spring Bean 的作用域有哪些？'
```

第二轮：

```bash
curl -G 'http://localhost:8091/memory/sliding-window/chat' \
  --data-urlencode 'sessionId=demo-memory-001' \
  --data-urlencode 'question=那它默认是哪一种？'
```

第三轮：

```bash
curl -G 'http://localhost:8091/memory/sliding-window/chat' \
  --data-urlencode 'sessionId=demo-memory-001' \
  --data-urlencode 'question=如果是 singleton，在并发下会不会有线程安全问题？'
```

查看当前窗口里还剩什么：

```bash
curl 'http://localhost:8091/memory/sessions/demo-memory-001'
```

### 3. 摘要压缩模式

第一轮：

```bash
curl -G 'http://localhost:8091/memory/summary/chat' \
  --data-urlencode 'sessionId=demo-summary-001' \
  --data-urlencode 'question=Spring Bean 的作用域有哪些？'
```

继续多轮追问：

```bash
curl -G 'http://localhost:8091/memory/summary/chat' \
  --data-urlencode 'sessionId=demo-summary-001' \
  --data-urlencode 'question=默认用的是哪一种？'
```

```bash
curl -G 'http://localhost:8091/memory/summary/chat' \
  --data-urlencode 'sessionId=demo-summary-001' \
  --data-urlencode 'question=那它在并发下会不会有线程安全问题？'
```

```bash
curl -G 'http://localhost:8091/memory/summary/chat' \
  --data-urlencode 'sessionId=demo-summary-001' \
  --data-urlencode 'question=如果换成 prototype，还会走完整生命周期回调吗？'
```

查看摘要和最近消息：

```bash
curl 'http://localhost:8091/memory/sessions/demo-summary-001'
```

### 4. 一键对比三种策略

```bash
curl 'http://localhost:8091/memory/compare'
```

这个接口最适合现场做压轴演示，因为它会直接返回：

- 每一轮问题
- 无记忆回答
- 滑动窗口回答
- 摘要压缩回答
- 当前摘要内容
- 摘要压缩触发次数

### 5. 重新演示前先清空会话

```bash
curl -X DELETE 'http://localhost:8091/memory/sessions/demo-memory-001'
```

```bash
curl -X DELETE 'http://localhost:8091/memory/sessions/demo-summary-001'
```
