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


# framework

- 横切基础设施层：承接 Web、数据库、缓存、消息队列、链路追踪、幂等控制等通用能力
- 设计目标：把“每个业务模块都会重复写一次”的工程代码收敛到统一模块，业务层只关注自身领域逻辑
- 依赖组合：Spring Web、MyBatis-Plus、Redis / Redisson、Sa-Token、RocketMQ、TTL

## config

自动装配入口。
- `DataBaseConfiguration`：注册 MyBatis-Plus 分页拦截器和自动填充处理器，统一持久层基础配置。
- `RocketMQAutoConfiguration`：装配事务消息监听器和消息生产者适配器，对上层暴露统一 MQ 发送接口。
- `WebAutoConfiguration`：注册全局异常处理器，让业务模块默认获得一致的 Web 异常响应行为。

## convention

通用约定模型。
- `Result`：统一接口返回体，约束 `code`、`message`、`data`、`requestId` 等字段。
- `ChatMessage`、`ChatRequest`：抽象对话请求和消息结构，作为问答链路中的基础协议对象。
- `RetrievedChunk`：抽象检索结果分片，为后续重排、组装上下文提供统一载体。

## context

运行时上下文能力。
- `LoginUser`：定义登录用户快照，承载用户 ID、用户名、角色、头像等信息。
- `UserContext`：基于 `TransmittableThreadLocal` 维护用户上下文，保证异步线程池场景下身份信息不丢失。
- `ApplicationContextHolder`：提供 Spring 容器静态访问能力，方便基础设施层按需获取 Bean。

## database

数据库基础增强。
- `MyMetaObjectHandler`：接管 MyBatis-Plus 自动填充逻辑，把创建时间、更新时间这类公共字段处理集中化。

## distributedid

分布式 ID 能力。
- `SnowflakeIdInitializer`：启动时通过 Redis + Lua 申请 `workerId` 和 `datacenterId`，完成 Snowflake 初始化。
- `CustomIdentifierGenerator`：对接 MyBatis-Plus 主键生成流程，让业务实体直接复用统一 ID 方案。
- 这一层的价值不是“生成 ID”本身，而是避免多实例部署时主键冲突和各模块各写一套 ID 逻辑。

## cache

缓存键规范。
- `RedisKeySerializer`：统一 Redis key 的序列化方式，避免不同模块使用 Redis 时出现键格式不一致的问题。

## errorcode

错误码约定。
- `IErrorCode`：定义错误码抽象接口。
- `BaseErrorCode`：沉淀通用错误码枚举，给异常体系和统一返回体提供标准化编码。

## exception

三层异常模型。
- `AbstractException`：统一业务异常基类，承载错误码和错误消息。
- `ClientException`：面向调用方输入、状态不合法等客户端错误。
- `ServiceException`：面向服务内部业务执行失败。
- `RemoteException`：面向远程调用或外部系统异常。
- `VectorCollectionAlreadyExistsException`：对知识库向量集合重复创建场景做语义化封装。

## idempotent

双场景幂等控制。
- `IdempotentSubmit` + `IdempotentSubmitAspect`：面向接口重复提交，基于 Redisson 分布式锁控制表单或命令型请求只执行一次。
- `IdempotentConsume` + `IdempotentConsumeAspect`：面向 MQ 重复消费，基于 Redis 状态位和 Lua 脚本实现消费中、已消费、异常回滚的状态流转。
- `SpELUtil`：支持通过注解表达式抽取业务键，避免幂等 key 构造硬编码。
- 这组组件说明 `framework` 不只是“工具箱”，而是在为分布式一致性问题提供可复用方案。

## mq

消息队列抽象。
- `MessageWrapper`：统一消息体包装格式，把业务载荷和消息 key 收口到一个对象里。
- `MessageQueueProducer`：定义统一消息发送接口，屏蔽具体 MQ 客户端细节。
- `RocketMQProducerAdapter`：落地普通消息和事务消息发送。
- `DelegatingTransactionListener`：把 RocketMQ 的事务回查机制抽象成按 topic 注册的检查器模型。
- `TransactionChecker`：给各业务模块预留事务回查扩展点。

## trace

RAG 链路追踪基础。
- `RagTraceContext`：维护 `traceId`、`taskId` 和节点栈，并通过 TTL 透传到异步执行链路。
- `RagTraceNode`、`RagTraceRoot`：为方法级链路打点提供注解语义，区分根节点和普通节点。
- 这部分是上层 RAG 编排、检索、生成可观测性的底座，不直接做业务，但决定后续排障和调优成本。

## web

Web 层统一出口。
- `GlobalExceptionHandler`：集中处理参数校验、认证鉴权、上传大小限制和兜底异常，统一返回 `Result`。
- `Results`：提供成功 / 失败结果的快捷构造方法，减少控制器层样板代码。
- `SseEmitterSender`：对 `SseEmitter` 做线程安全封装，统一流式输出、连接关闭和异常收口逻辑。

## 总结

`framework` 的定位不是直接承载 RAG 业务，而是给整个系统提供一层“工程基础设施底座”：
- 往上，`bootstrap` 等业务模块可以直接复用上下文、异常、幂等、MQ、Trace 等能力。
- 往下，它把 Redis、RocketMQ、MyBatis-Plus、Sa-Token 这些中间件细节隔离成稳定约定。
- 从工程视角看，`framework` 决定了这个项目是否具备统一规范、低重复开发成本和可运维性。

# bootstrap

- 业务编排层：真正把 `infra-ai` 提供的模型能力、`framework` 提供的工程底座、`mcp-server` 提供的工具能力装配成可对外提供服务的应用
- 模块定位：不是“纯 Controller 层”，而是包含问答编排、知识库管理、文档摄取、用户鉴权、后台运营等完整业务域
- 结构特点：按业务域拆包，每个域基本遵循 `controller -> service -> dao / core / config` 的组织方式，既方便读代码，也便于未来继续拆分服务

## 模块总览

- `rag`：对话主链路，负责流式问答、Prompt 组装、检索、意图识别、Query Rewrite、会话记忆、MCP 工具调用、链路追踪与限流控制
- `knowledge`：知识库管理域，负责知识库、文档、分块、向量写入、启停、调度刷新，以及文档状态管理
- `ingestion`：文档摄取流水线域，负责把“抓取 -> 解析 -> 增强 -> 分块 -> 建索引”抽象成可编排的节点流水线
- `user`：用户与认证域，负责登录登出、用户信息、角色信息以及请求身份上下文接入
- `admin`：后台运营与观测域，负责首页看板、趋势、性能指标等管理端数据聚合
- `core`：跨业务复用的领域能力，沉淀文档解析与 Chunk 切分等不适合归入单一业务域的核心逻辑

## 它和其它模块的关系

- 对上：`bootstrap` 是应用入口模块，直接暴露 HTTP / SSE 接口，承接前端或外部调用
- 对下：它依赖 `infra-ai` 完成 LLM、Embedding、Rerank 等模型调用
- 对侧：它依赖 `framework` 提供统一异常、上下文、幂等、MQ、Trace、Web 约定
- 对外扩展：它可以通过 `rag.core.mcp` 对接远程 MCP 工具，把外部能力纳入问答链路

## rag

RAG 主业务域，也是整个系统最核心的“在线问答编排层”。

- `RAGChatController`：暴露 `/rag/v3/chat` SSE 流式问答入口和 `/rag/v3/stop` 任务取消入口
- `RAGChatService` / `RAGChatServiceImpl`：对外收口问答服务，负责组装 `StreamChatContext`，启动流式任务
- `service.pipeline.StreamChatPipeline`：问答主链路编排器，承担“请求进入后到底按什么顺序处理”的责任
- `core.retrieve`：检索层，封装 PgVector / Milvus 检索、多路召回、去重与重排后处理
- `core.prompt`：Prompt 构建层，负责模板加载、上下文格式化、按场景拼装 Prompt
- `core.rewrite`：问题改写层，负责 Query Rewrite、多问题拆分、术语映射缓存
- `core.intent` / `core.guidance`：意图识别与引导层，决定是否进入知识库检索、意图树匹配或系统引导回答
- `core.memory`：会话记忆层，负责多轮对话历史与摘要记忆
- `core.mcp`：工具调用层，负责工具注册、参数提取、远程 MCP 执行
- `service.handler`：流式事件处理层，负责把模型增量输出、安全收尾、任务取消等动作转换成 SSE 事件
- `aop`：问答横切控制，包含限流、排队限制和 RAG Trace 打点

从职责上看，`rag` 不是单一 Service，而是一整套“在线推理编排系统”。如果把整个项目只看一条主线，`rag` 就是离用户问题最近的核心域。

### 请求链路

下面按一次 `/rag/v3/chat` 流式问答请求的实际链路，解释 `bootstrap.rag` 是如何把“用户提问”变成“SSE 持续返回答案”的。

#### 1. 入口层：建立 SSE 通道并创建任务上下文

- `RAGChatController` 接收 `question`、`conversationId`、`deepThinking` 参数，创建 `SseEmitter`
- `@IdempotentSubmit` 先按用户维度做幂等控制，避免同一用户并发重复发起多个问答请求
- `RAGChatServiceImpl` 负责补齐 `conversationId` 和 `taskId`，然后通过 `StreamCallbackFactory` 创建 `StreamChatEventHandler`
- `StreamChatEventHandler` 初始化时会先发送 `meta` 事件，把 `conversationId` 和 `taskId` 返回给前端，同时向 `StreamTaskManager` 注册取消句柄

这一层的作用不是做 RAG 本身，而是先把“长连接会话”建立起来。因为后续链路是异步流式返回，所以必须在一开始就把 SSE 通道、任务 ID、取消控制准备好。

#### 2. 流水线编排层：把一个问题加工成可检索、可生成的上下文

- `StreamChatPipeline.execute` 是主编排入口
- 第一步 `loadMemory`：调用 `ConversationMemoryService.loadAndAppend`，把历史消息和当前用户问题合并成对话上下文
- 第二步 `rewriteQuery`：调用 `QueryRewriteService.rewriteWithSplit`，对问题做改写，并在需要时拆成多个子问题
- 第三步 `resolveIntents`：调用 `IntentResolver.resolve`，给每个子问题匹配意图节点，区分知识库意图、系统意图、MCP 工具意图

这三步解决的是“模型真正该回答什么”和“系统该去哪里找证据”的问题。项目没有直接拿原始问题去查向量库，而是先经过记忆补全、问题改写和意图解析，这是为了降低多轮对话、省略问法、复合问题带来的检索偏差。

#### 3. 分流判断：是否需要提前短路

- `handleGuidance`：如果 `IntentGuidanceService` 判断当前问题存在歧义，就直接返回引导文案，不进入检索和生成
- `handleSystemOnly`：如果所有意图都属于 system-only，则直接走 `streamSystemResponse`，只用系统 Prompt + 历史消息回答，不查知识库
- 如果既不歧义也不属于纯系统问题，才继续进入检索阶段

这一层体现了项目的一个重要设计取舍：不是所有问题都应该强行走 RAG。先做分流，可以减少无意义检索，也能避免在系统类问题上引入噪声上下文。

#### 4. 检索层：并行构建 KB 上下文和 MCP 上下文

- `StreamChatPipeline.retrieve` 调用 `RetrievalEngine.retrieve`
- `RetrievalEngine` 以“子问题”为粒度并行构建上下文，每个子问题都会拆成两类意图
- `NodeScoreFilters.kb(...)` 提取知识库意图，交给 `retrieveAndRerank`
- `NodeScoreFilters.mcp(...)` 提取工具意图，交给 `executeMcpAndMerge`

也就是说，这个系统的“检索”不是只有向量检索，而是把知识库召回和外部工具调用统一看成“证据获取”过程，最后都合并到 `RetrievalContext`。

#### 5. 知识库检索：多通道并行召回，再做后处理

- `retrieveAndRerank` 内部调用 `MultiChannelRetrievalEngine.retrieveKnowledgeChannels`
- `MultiChannelRetrievalEngine` 会筛出启用的 `SearchChannel`，并发执行各检索通道
- 当前设计支持按通道扩展，例如全局向量检索、按意图定向检索等
- 通道结果不会直接原样返回，而是进入 `SearchResultPostProcessor` 链
- 后处理器负责去重、重排、裁剪等动作，最终得到一组 `RetrievedChunk`
- `ContextFormatter.formatKbContext` 再把这些 Chunk 整理成适合放入 Prompt 的 KB 文本上下文

这样设计的原因是：召回不是一步到位的动作。先多路召回，再做统一后处理，比“某一个检索器写死所有策略”更容易扩展和调优。

#### 6. MCP 工具调用：把外部动态能力并入上下文

- `executeMcpAndMerge` 会先根据意图节点找到对应 `MCPTool`
- `MCPParameterExtractor` 根据用户问题和工具定义提取参数
- `MCPToolRegistry` 找到对应执行器，执行后得到 `MCPResponse`
- 成功的工具结果最终通过 `ContextFormatter.formatMcpContext` 转成模型可读的动态数据片段

这一段的意义在于：系统不是只靠静态知识库回答问题。对于天气、票务、销售数据这类动态信息，它会先调工具拿实时数据，再把结果作为证据送给大模型。

#### 7. Prompt 组装：根据场景选择模板并拼接证据

- `StreamChatPipeline.streamLLMResponse` 把检索结果封装成 `PromptContext`
- `RAGPromptService.buildStructuredMessages` 负责生成最终消息列表
- 如果只有 KB，上 `answer-chat-kb.st` 一类模板
- 如果只有 MCP，上 `answer-chat-mcp.st` 一类模板
- 如果 KB + MCP 混合，则走 `answer-chat-mcp-kb-mixed.st`
- 消息结构不是简单一段文本，而是 `system prompt + MCP 证据 + KB 证据 + history + user question`
- 如果是多子问题场景，用户消息会显式编号，降低模型漏答某个子问题的概率

这里的关键不是“拼字符串”，而是通过 Prompt 场景化，把不同来源的证据按稳定格式喂给模型。这样做有利于控制回答风格，也有利于后续持续调模板。

#### 8. 模型流式生成：把 structured messages 交给 LLMService

- `StreamChatPipeline` 最终调用 `llmService.streamChat`
- 如果是纯 system-only 路径，走 `streamSystemResponse`
- 如果是 RAG 路径，走 `streamLLMResponse`
- `ChatRequest` 中会带上 `thinking`、`temperature`、`topP` 等参数
- MCP 场景下温度略高，纯 KB 场景温度更低，说明项目在生成稳定性和开放性之间做了场景化权衡
- 返回值是 `StreamCancellationHandle`，并绑定到 `StreamTaskManager`，支持后续取消任务

这一步开始，控制权就从业务编排层转向了 `infra-ai` 的模型客户端与流式解析链路。

#### 9. SSE 出口层：把模型增量输出转成前端事件

- `StreamChatEventHandler` 实现了 `StreamCallback`
- `onThinking` 把思考内容按 `message` 事件持续推送，类型为 `think`
- `onContent` 把正式回复按 `message` 事件持续推送，类型为 `response`
- `sendChunked` 会按配置的 `messageChunkSize` 拆小片，避免前端单次渲染负担过大
- `SSEEventType` 统一约定了 `meta`、`message`、`finish`、`done`、`cancel` 等事件名

这里的设计重点是“前后端协议稳定”。模型输出是 token 流，但前端真正消费的是项目自定义的 SSE 事件协议。

#### 10. 收尾：落库、补标题、关闭连接

- `onComplete` 会把完整答案和 thinking 内容封装成 `ChatMessage.assistant(...)`
- `ConversationMemoryService.append` 负责把最终消息写回会话存储
- 如果当前会话还没有标题，`StreamChatEventHandler` 会在完成事件里附带标题信息
- 然后发送 `finish` 事件和 `done` 事件，最后关闭 SSE 连接
- 如果用户调用 `/rag/v3/stop`，`RAGChatServiceImpl.stopTask` 会通过 `StreamTaskManager` 取消任务

这一步非常重要，因为流式输出只是“展示层完成”，而把消息、思考内容、标题、任务状态写回存储，才意味着这次对话真正闭环。

#### 链路小结

如果把整条请求链路压缩成一句话，可以概括为：

- `Controller` 建立 SSE 通道
- `Service` 生成会话与任务上下文
- `Pipeline` 完成记忆加载、问题改写、意图解析、分流判断
- `RetrievalEngine` 并行拼出 KB / MCP 证据
- `RAGPromptService` 组装结构化消息
- `LLMService` 流式生成
- `StreamChatEventHandler` 把模型输出转换成前端事件并完成持久化

这套设计的核心价值在于：把“一个问答请求”拆成了多个边界清晰的阶段，每一段都可以单独替换、调优和观测，而不是把所有逻辑堆在一个大 Service 里。

## knowledge

知识库管理域，负责把“静态文档资源”转成“可检索、可维护、可调度”的知识资产。

- `KnowledgeBaseController`：知识库元信息管理入口
- `KnowledgeDocumentController`：文档上传、分块触发、启停、分页查询、日志查询等入口
- `KnowledgeDocumentServiceImpl`：知识文档主服务，负责上传文件、选择处理模式、执行分块、向量落库、调度同步
- `KnowledgeChunkService`：文档 Chunk 的持久化与状态管理
- `schedule`：文档定时刷新子系统，负责 URL 文档的周期拉取、锁控制、调度状态管理
- `mq`：分块异步处理链路，利用事务消息把“状态更新”和“异步分块”衔接起来
- `config`：知识库并发控制与调度配置，例如信号量初始化、调度周期配置

这个模块的价值在于，它把“上传一个文件”提升成了“构建一个可运营知识库文档”的完整生命周期管理。

## ingestion

摄取流水线域，负责把文档处理过程抽象成可配置、可扩展的节点编排系统。

- `IngestionPipelineController` / `IngestionTaskController`：流水线定义与执行任务管理入口
- `IngestionEngine`：流水线执行引擎，按节点连线顺序驱动整个处理过程
- `node`：节点实现层，包含 `FetcherNode`、`ParserNode`、`EnhancerNode`、`EnricherNode`、`ChunkerNode`、`IndexerNode`
- `strategy.fetcher`：数据获取策略层，支持本地文件、HTTP URL、S3、飞书等来源
- `domain.pipeline`：流水线定义模型，描述节点、连线、条件、配置
- `domain.context`：流水线运行态上下文，承载原始字节、解析结果、结构化文档、日志、错误等信息
- `prompt`：摄取链路中的增强 / 富化 Prompt 管理

它的设计重点不是“把文档切块”这么简单，而是把原本写死在代码里的入库流程提升成一个可配置编排引擎。这样后续扩展 OCR、摘要增强、标签提取、结构化抽取时，不需要改主流程控制器。

## user

用户与认证域，负责系统的访问身份与账号管理。

- `AuthController`：登录、登出入口
- `UserController`：用户分页、创建、更新、当前用户查询等入口
- `AuthServiceImpl`：认证逻辑实现
- `UserServiceImpl`：用户管理逻辑实现
- `config`：对接 Sa-Token 与用户上下文拦截，把登录态转换成业务层可用的 `UserContext`

这个模块虽然不是 RAG 算法核心，但它决定了系统是否具备“面向真实用户运行”的基础能力。

## admin

后台运营域，负责给管理端提供聚合指标与趋势数据。

- `DashboardController`：看板总览、性能、趋势接口入口
- `DashboardServiceImpl`：聚合问答、文档、反馈等多类业务数据，输出管理端视图对象
- `controller.vo`：面向前端展示的 Dashboard 视图模型

这说明项目并不只是做“能回答问题”，而是在往“可运营、可观测”的产品形态推进。

## core

跨业务复用的领域核心组件，承担 `rag` 与 `knowledge` 都会依赖的底层语义处理能力。

- `parser`：统一文档解析抽象，当前包含基于 Tika 的通用解析和 Markdown 解析
- `chunk`：统一分块抽象，包含固定大小切分、结构感知切分、分块配置与向量化服务
- `ChunkEmbeddingService`：把 Chunk 与 Embedding 过程衔接起来，避免每个业务域自己重复写一遍向量化逻辑

`core` 的存在说明作者在业务模块之上又做了一层“领域能力沉淀”，避免 `rag` 和 `knowledge` 彼此复制解析与切块逻辑。

## 总结

从整体分层看，`bootstrap` 可以理解为系统的“应用装配层”：

- `framework` 解决通用工程问题
- `infra-ai` 解决模型访问与路由问题
- `bootstrap` 解决具体业务编排问题

如果读者准备继续深入源码，建议先从 `bootstrap.rag` 和 `bootstrap.knowledge` 开始，因为它们最能体现这个项目作为 RAG Agent 应用的核心价值。

工具类。

- `LLMResponseCleaner`：清理大模型响应中的 Markdown 代码围栏。
