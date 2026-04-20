# Infra-ai

- 底层基础设施：config、enums、http、token、util
- 路由核心：model--提供模型选择、健康检查、故障转移
- 能力子系统：chat、embedding、rerank -- 三条并行业务线

![完整调用流程](docs/LYW/assets/fullCallSample.svg)

## config

配置核心。

- `AIModelProperties`：将 YAML 配置绑定为类型安全的 Java 对象。

## enums

类型词汇表。

- `ModelProvider`：供应商枚举。
- `ModelCapability`：能力枚举。

## model

路由核心。

- `ModelSelector`：负责选择模型。
- `ModelHealthStore`：负责维护模型健康状态。
- `ModelRoutingExecutor`：负责执行模型路由调用。
- `ModelTarget`：表示调用目标。
- `ModelCaller`：函数式调用接口。

## http

HTTP 基础设施。

- `ModelUrlResolver`：URL 解析。
- `HttpResponseHelper`：响应工具。
- `ModelClientException`：统一客户端异常。
- `ModelClientErrorType`：错误分类。
- `HttpMediaTypes`：HTTP 媒体类型常量。

## chat

LLM 对话子系统。

- `LLMService`：对话业务接口。
- `ChatClient`：供应商客户端接口。
- `AbstractOpenAIStyleChatClient`：OpenAI 风格客户端模板基类。
- 三个供应商实现：负责具体模型提供方接入。
- `RoutingLLMService`：对话路由服务。
- 流式相关组件：负责流式输出链路。

## embedding

向量化子系统。

- `EmbeddingService`：向量化业务接口。
- `EmbeddingClient`：供应商客户端接口。
- 两个供应商实现：负责具体 embedding 能力接入。
- `RoutingEmbeddingService`：向量化路由服务。

## rerank

重排序子系统。

- `RerankService`：重排业务接口。
- `RerankClient`：供应商客户端接口。
- `BaiLianRerankClient`：百炼重排实现。
- `NoopRerankClient`：空对象实现。
- `RoutingRerankService`：重排路由服务。

## token

Token 估算。

- `TokenCounterService`：Token 统计接口。
- `HeuristicTokenCounterService`：启发式统计实现。

## util

工具类。

- `LLMResponseCleaner`：清理大模型响应中的 Markdown 代码围栏。
