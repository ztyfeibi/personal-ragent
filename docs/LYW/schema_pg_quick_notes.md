# `schema_pg.sql` 表结构速记

来源文件: [resources/database/schema_pg.sql](D:/Dev/DevProjects/ragent/ragent/resources/database/schema_pg.sql)

## 1. 整体结构

- 技术栈: PostgreSQL + `pgvector`
- 向量扩展: `CREATE EXTENSION IF NOT EXISTS vector`
- 表数量: 17 张业务表 + 1 张向量表
- 通用风格:
  - 主键基本统一为 `VARCHAR(20)` 的 `id`
  - 大多数表都有 `create_time`、`update_time`
  - 绝大多数表有逻辑删除字段 `deleted`

## 2. 按业务域分组

### 用户与会话

- `t_user`
  - 用户主表
  - 关键字段: `username` 唯一, `password`, `role`, `avatar`

- `t_conversation`
  - 会话列表
  - 关键字段: `conversation_id`, `user_id`, `title`, `last_time`
  - 关键约束: `(conversation_id, user_id)` 唯一
  - 关键索引: `(user_id, last_time)`

- `t_conversation_summary`
  - 会话摘要, 与消息表分离
  - 关键字段: `conversation_id`, `user_id`, `last_message_id`, `content`
  - 关键索引: `(conversation_id, user_id)`

- `t_message`
  - 消息主表
  - 关键字段: `conversation_id`, `user_id`, `role`, `content`
  - 扩展字段: `thinking_content`, `thinking_duration`
  - 关键索引: `(conversation_id, user_id, create_time)`

- `t_message_feedback`
  - 消息反馈
  - 关键字段: `message_id`, `conversation_id`, `user_id`, `vote`, `reason`, `comment`
  - 关键约束: `(message_id, user_id)` 唯一

- `t_sample_question`
  - 示例问题库
  - 关键字段: `title`, `description`, `question`

### 知识库与文档

- `t_knowledge_base`
  - 知识库主表
  - 关键字段: `name`, `embedding_model`, `collection_name`, `created_by`
  - 关键约束: `collection_name` 唯一

- `t_knowledge_document`
  - 知识库文档
  - 关键字段:
    - 归属: `kb_id`
    - 文件: `doc_name`, `file_url`, `file_type`, `file_size`
    - 状态: `enabled`, `status`, `chunk_count`
    - 处理配置: `process_mode`, `chunk_strategy`, `chunk_config`, `pipeline_id`
    - 来源配置: `source_type`, `source_location`
    - 调度配置: `schedule_enabled`, `schedule_cron`

- `t_knowledge_chunk`
  - 文档分块主表
  - 关键字段: `kb_id`, `doc_id`, `chunk_index`, `content`
  - 统计字段: `content_hash`, `char_count`, `token_count`
  - 这是“文本分块明细表”，不是向量表

- `t_knowledge_document_chunk_log`
  - 分块执行日志
  - 关键字段:
    - 归属: `doc_id`
    - 状态: `status`
    - 配置快照: `process_mode`, `chunk_strategy`, `pipeline_id`
    - 耗时: `extract_duration`, `chunk_duration`, `embed_duration`, `persist_duration`, `total_duration`
    - 结果: `chunk_count`, `error_message`

### 知识库定时刷新

- `t_knowledge_document_schedule`
  - 文档刷新调度表
  - 关键字段:
    - 归属: `doc_id`, `kb_id`
    - 调度: `cron_expr`, `enabled`, `next_run_time`
    - 最近状态: `last_run_time`, `last_success_time`, `last_status`, `last_error`
    - 远程内容指纹: `last_etag`, `last_modified`, `last_content_hash`
    - 分布式锁: `lock_owner`, `lock_until`
  - 关键约束: `doc_id` 唯一

- `t_knowledge_document_schedule_exec`
  - 调度执行记录
  - 关键字段:
    - 归属: `schedule_id`, `doc_id`, `kb_id`
    - 执行: `status`, `message`, `start_time`, `end_time`
    - 文件快照: `file_name`, `file_size`, `content_hash`, `etag`, `last_modified`

### RAG 意图与查询改写

- `t_intent_node`
  - 意图树节点配置
  - 关键字段:
    - 树结构: `intent_code`, `parent_code`, `level`, `sort_order`
    - 展示与说明: `name`, `description`, `examples`
    - 检索关联: `kb_id`, `collection_name`, `top_k`
    - MCP/提示词: `mcp_tool_id`, `prompt_snippet`, `prompt_template`, `param_prompt_template`
    - 类型控制: `kind`, `enabled`

- `t_query_term_mapping`
  - 查询词归一化映射
  - 关键字段: `domain`, `source_term`, `target_term`, `match_type`, `priority`, `enabled`
  - 用途: 查询改写前的术语标准化
  - 关键索引: `domain`, `source_term`

### Trace / 可观测性

- `t_rag_trace_run`
  - 一次 Trace 运行主记录
  - 关键字段:
    - 业务关联: `trace_id`, `trace_name`, `entry_method`, `conversation_id`, `task_id`, `user_id`
    - 运行状态: `status`, `error_message`
    - 时间: `start_time`, `end_time`, `duration_ms`
    - 扩展: `extra_data`
  - 关键约束: `trace_id` 唯一

- `t_rag_trace_node`
  - Trace 节点明细
  - 关键字段:
    - 关联: `trace_id`, `node_id`, `parent_node_id`, `depth`
    - 节点信息: `node_type`, `node_name`, `class_name`, `method_name`
    - 运行状态: `status`, `error_message`, `duration_ms`
    - 扩展: `extra_data`
  - 关键约束: `(trace_id, node_id)` 唯一

### Ingestion 流水线

- `t_ingestion_pipeline`
  - 流水线定义主表
  - 关键字段: `name`, `description`
  - 关键约束: `(name, deleted)` 唯一

- `t_ingestion_pipeline_node`
  - 流水线节点定义
  - 关键字段:
    - 归属: `pipeline_id`
    - 编排: `node_id`, `node_type`, `next_node_id`
    - 配置: `settings_json`, `condition_json`
  - 关键约束: `(pipeline_id, node_id, deleted)` 唯一

- `t_ingestion_task`
  - 流水线执行任务
  - 关键字段:
    - 归属: `pipeline_id`
    - 来源: `source_type`, `source_location`, `source_file_name`
    - 状态: `status`, `chunk_count`, `error_message`
    - 执行数据: `logs_json`, `metadata_json`, `started_at`, `completed_at`

- `t_ingestion_task_node`
  - 任务节点执行明细
  - 关键字段:
    - 归属: `task_id`, `pipeline_id`
    - 节点: `node_id`, `node_type`, `node_order`
    - 状态: `status`, `duration_ms`, `message`, `error_message`
    - 输出: `output_json`

### 向量存储

- `t_knowledge_vector`
  - 统一的 pgvector 向量表
  - 当前结构只有 4 个核心字段:
    - `id`: 分块 ID
    - `content`: 分块文本
    - `metadata`: `JSONB`
    - `embedding`: `vector(1536)`
  - 关键索引:
    - `idx_kv_metadata`: `GIN(metadata)`
    - `idx_kv_embedding`: `HNSW(embedding vector_cosine_ops)`
  - 重要结论:
    - 这张表现在没有 `chunk_id`、`kb_id`、`doc_id`、`chunk_index`
    - 文档归属关系走 `metadata` 中的 `collection_name`、`doc_id` 等字段

## 3. 最值得记住的几个“主线”

### 会话主线

- 用户: `t_user`
- 会话列表: `t_conversation`
- 消息明细: `t_message`
- 摘要: `t_conversation_summary`
- 反馈: `t_message_feedback`

### 知识库主线

- 知识库: `t_knowledge_base`
- 文档: `t_knowledge_document`
- 文本分块: `t_knowledge_chunk`
- 向量分块: `t_knowledge_vector`
- 分块执行日志: `t_knowledge_document_chunk_log`

### 定时刷新主线

- 调度配置: `t_knowledge_document_schedule`
- 调度执行记录: `t_knowledge_document_schedule_exec`

### RAG 主线

- 意图树: `t_intent_node`
- 术语归一化: `t_query_term_mapping`
- Trace 运行: `t_rag_trace_run`
- Trace 节点: `t_rag_trace_node`

### 摄取流水线主线

- 流水线定义: `t_ingestion_pipeline`
- 流水线节点: `t_ingestion_pipeline_node`
- 执行任务: `t_ingestion_task`
- 执行节点: `t_ingestion_task_node`

## 4. 近期最容易踩坑的点

- `t_knowledge_vector` 已经是新结构:
  - `id / content / metadata / embedding`
- 如果代码或测试还在用老字段:
  - `chunk_id / kb_id / doc_id / chunk_index`
  - 就会直接报列不存在

- `t_query_term_mapping` 是应用启动期会访问的表
  - 测试环境如果没初始化这个表
  - `@SpringBootTest` 会在 ApplicationContext 启动阶段直接失败

## 5. 一句话记忆版

- 用户会话 6 张
- 知识库文档 6 张
- RAG/Trace 4 张
- 摄取流水线 4 张
- 向量统一落到 1 张 `t_knowledge_vector`
