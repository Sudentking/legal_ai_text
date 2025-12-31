# legal-ai-system

纯 Java + JDBC 的法律条文向量化与检索示例，使用 MySQL 作为业务库、PostgreSQL（pgvector）作为向量库。当前实现了条文切片、向量生成（占位算法）、向量入库及增量导入管道。

## 目录结构
- `src/main/resources/application.properties`：PostgreSQL 与 MySQL 连接配置。
- `ai/legal/config/DatabaseConfig.java`：PostgreSQL 连接。
- `ai/legal/config/MySqlConfig.java`：MySQL 连接。
- `ai/legal/model/*`：数据模型（LawText、LawTextChunk、LegalEmbedding）。
- `ai/legal/dao/mysql/LawTextDao.java`：读取 `law_text`、写入 `law_text_chunk`。
- `ai/legal/dao/LegalEmbeddingDao.java`：向 PostgreSQL `legal_embedding` 插入与查询。
- `ai/legal/util/TextSplitter.java`：300–500 字分片，按段落聚合后切分。
- `ai/legal/service/VectorSearchService.java`：封装向量插入/查询。
- `ai/legal/service/importer/*`：增量导入管道（策略、结果统计、分片服务、入口）。
- `ai/legal/App.java`：示例插入与 Top-K 查询。
- `ai/legal/rag/prompt/LegalRagPromptBuilder.java`：RAG 场景 Prompt 拼装。
- `ai/legal/rag/prompt/ClarificationPromptBuilder.java`：不足以作答时的补充事实提问 Prompt。
- `ai/legal/rag/service/LegalRagQaService.java`：RAG 问答闭环（向量检索 + Prompt + LLM 调用）。
- `ai/legal/rag/service/LlmClient.java`：LLM 客户端接口，占位便于接入厂商 SDK。
- `ai/legal/rag/intent/*`：意图识别（关键词规则）。
- `ai/legal/rag/agent/LegalAgentService.java`：Agent 主流程（意图识别 → 决策 → RAG → LLM）。

## 依赖与环境
- JDK 17
- Maven
- MySQL（库 `legal_dev`，表 `law_text`、`law_text_chunk` 已存在）
- PostgreSQL 16 + pgvector（库 `legal_vector`，表 `legal_embedding` 已存在，向量维度 1536）

## 配置
编辑 `src/main/resources/application.properties`：
```properties
# PostgreSQL
db.url=jdbc:postgresql://localhost:5432/legal_vector
db.user=postgres
db.password=postgres
# MySQL
mysql.url=jdbc:mysql://localhost:3306/legal_dev?useSSL=false&serverTimezone=UTC
mysql.user=root
mysql.password=root123456
```

## 构建
```bash
mvn clean package
```

## 运行
- 批量导入（增量、跳过已处理）：  
  ```bash
  java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.service.importer.LawTextToVectorImporter
  ```
- 修改策略（全量且不跳过）：将入口中的 `ImportPolicy.defaultPolicy()` 替换为 `new ImportPolicy(false, false, 400)` 再运行。
- 单条示例：  
  ```bash
  java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.App
  ```

## 用户注册 / 登录 / 超级用户（CLI）
1) 先在 MySQL（`mysql.url` 对应库）执行：
- `sql/mysql_user_auth_schema.sql`（用户/会话/权限表）
- `sql/mysql_agent_log_schema.sql`（问答日志/任务历史表，可选但推荐）

2) 运行命令行：
```bash
java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.console.LegalQaCli
```

3) 常用命令：
- `:register <username> <password>`
- `:login <username> <password>`
- `:init-admin <username> <password>`（创建/重置超级用户，仅建议本地/运维使用）
- `:grant <username> <PERMISSION_CODE>` / `:revoke ...`（超级用户）
- `:list-user-perms <username>`（超级用户）

## 后续接 Web 的入口（预留）
- 注册/登录/会话鉴权：`src/main/java/ai/legal/service/auth/AuthService.java:1`
- 超级用户权限管理：`src/main/java/ai/legal/service/auth/AdminService.java:1`

## Web 雏形（HttpServer）
1) 先在 MySQL（`mysql.url` 对应库）执行：
- `sql/mysql_user_auth_schema.sql`
- `sql/mysql_agent_log_schema.sql`（否则后台日志页面会查询不到表）

2) 启动 Web Server：
```bash
java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.web.LegalWebServerApp
```

3) 打开：
- `http://localhost:8080/login`（登录）
- `http://localhost:8080/register`（注册）
- `http://localhost:8080/app`（普通用户提问页）
- `http://localhost:8080/admin`（超级用户后台：日志/批量导入/权限）

## 导入逻辑概述
- `ImportPolicy`：控制增量/跳过已处理、分片长度。
- `LawTextDao.findUnprocessed()`：仅取未切片的条文；`hasChunks()` 判重。
- `LawTextChunkService.processLawText(...)`：对单条条文分片 → 生成占位向量 → 写 PG `legal_embedding` → 写 MySQL `law_text_chunk`（vector_id = embedding.id），统计成功/失败。
- `LawTextToVectorImporter`：遍历待处理条文，汇总成功/失败/跳过 ID 与耗时。
- 向量生成目前为占位算法，后续接入真实 1536 维模型/API 时替换 `generateEmbedding`。

## RAG 问答闭环
- `LegalRagQaService.answer(userQuestion)`：将问题生成 1536 维占位向量 → `searchTopK` 取 5 条上下文 → `LegalRagPromptBuilder.buildPrompt` 拼装 Prompt → 调用 `LlmClient.chat` 返回回复。
- `LlmClient` 为大模型接口占位，按需实现（如 HTTP 调用厂商 API）。
- `LegalAgentService.answer(userQuestion)`：先用 `LegalIntentClassifier` 判断意图，若信息不足则用 `ClarificationPromptBuilder` 引导补充事实，否则走 `LegalRagQaService` 的标准 RAG 流程。

## 结果验证
- PostgreSQL（legal_vector）：查看新增向量  
  ```sql
  SELECT id, law_id, article_no, chunk_index, source, created_at
  FROM legal_embedding
  ORDER BY id DESC
  LIMIT 20;
  ```
- MySQL（legal_dev）：查看切片与 vector_id  
  ```sql
  SELECT id, document_id, vector_id, chunk_order, LEFT(chunk_text, 50) AS preview, created_at
  FROM law_text_chunk
  ORDER BY id DESC
  LIMIT 20;

  SELECT document_id, COUNT(*) AS chunk_count
  FROM law_text_chunk
  GROUP BY document_id
  ORDER BY document_id DESC;
  ```
确认 `law_text_chunk.vector_id` 等于 PostgreSQL `legal_embedding.id`，分片数量与切分一致。

## 约束提醒
- 仅使用 Java 标准库 + JDBC；禁止引入 Spring/JPA/Lombok 等。
- 所有 SQL 使用 PreparedStatement。
- 不修改既有表结构，向量维度固定 1536。
- 后续每次代码更新请同步维护本 README。 
