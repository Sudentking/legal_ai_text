# legal-ai-system

纯 Java + JDBC 的法律智能问答系统雏形，使用：
- MySQL：业务库（法律条文 + 用户/权限 + 日志）
- PostgreSQL + pgvector：向量库（RAG 检索）

当前包含：
- 法律条文结构化存储：`law_text` / `law_text_chunk`
- RAG：向量检索 + 条文重组 + 两阶段输出（法律依据 → 法律分析与建议）
- 法条原文/章节/全文结构化直通：命中后不走向量检索
- 用户注册/登录/会话/权限（`USER` / `SUPER_ADMIN`）
- Web 雏形（纯 JDK `HttpServer`）：登录/注册/提问页/后台

## 目录结构（关键入口）
- 配置：`src/main/resources/application.properties`
- 结构化法条查询：`src/main/java/ai/legal/rag/service/StructuredLawQueryService.java`
- RAG 问答服务：`src/main/java/ai/legal/rag/service/LegalRagQaService.java`
- Agent 主流程：`src/main/java/ai/legal/rag/agent/LegalAgentService.java`
- 认证与权限：`src/main/java/ai/legal/service/auth/AuthService.java`、`src/main/java/ai/legal/service/auth/AdminService.java`
- Web Server：`src/main/java/ai/legal/web/LegalWebServerApp.java`
- SQL 脚本：`sql/mysql_user_auth_schema.sql`、`sql/mysql_agent_log_schema.sql`
- 提示词使用：`PROMPT_ENGINEER.md`

## 环境依赖
- JDK 17
- Maven
- MySQL（默认库：`legal_dev`）
- PostgreSQL 16 + pgvector（默认库：`legal_vector`）

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

## 初始化数据库（MySQL）
在 `mysql.url` 指向的库中执行：
- `sql/mysql_user_auth_schema.sql`（用户/会话/权限：`user_account` / `user_session` / `user_permission`）
- `sql/mysql_agent_log_schema.sql`（日志：`qa_log` / `agent_task_history`）

说明：
- 法律条文表（`law_text` / `law_text_chunk`）为你的业务核心表，本仓库不自动创建（保持与你现有结构一致）。

## 普通用户 vs 超级用户
- 普通用户（`USER`）
  - Web：登录后进入 `/app`，只能向 Agent 提问
  - 默认注册会写入 `user_account`，并授予基础权限 `QA_ASK`
  - 无权访问 `/admin`，也不能查看日志/批量导入
- 超级用户（`SUPER_ADMIN`）
  - Web：登录后进入 `/admin`
  - 可查看用户使用日志：`qa_log` 与 `agent_task_history`
  - 可批量导入文档（写入 `law_text`，并触发分片/向量入库）
  - 可授予/撤销权限（`user_permission`）

创建/重置超级用户：
- 使用 CLI：运行 `ai.legal.console.LegalQaCli` 后输入 `:init-admin <username> <password>`
- 或代码调用：`AuthService.ensureSuperAdmin(username, password)`

## 构建
```bash
mvn clean package
```

说明：推荐在 IDEA 运行 main（Maven 依赖会自动加入 classpath）。若你用 `java -cp target/*.jar` 直接跑，请确保依赖 jar 也在 classpath 中。

## 运行（Web）
启动：
```bash
java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.web.LegalWebServerApp
```

页面：
- `http://localhost:8080/login`（登录）
- `http://localhost:8080/register`（注册）
- `http://localhost:8080/app`（普通用户提问页）
- `http://localhost:8080/admin`（超级用户后台：日志/批量导入/权限）

## 运行（CLI）
启动：
```bash
java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.console.LegalQaCli
```

常用命令：
- `:register <username> <password>`
- `:login <username> <password>`
- `:logout` / `:whoami`
- `:init-admin <username> <password>`
- `:grant <username> <PERMISSION_CODE>` / `:revoke <username> <PERMISSION_CODE>`
- `:list-user-perms <username>`

## RAG 与结构化直通（你需要知道的行为差异）
- 结构化直通（SQL）适用：法条原文/章节/全文请求（例如包含“原文”“第X条”“第X章”“第X编”“全文/全部内容”等）
  - 命中后：禁止走向量检索，直接查 MySQL 原文
  - 未命中：直接提示“未找到匹配条文”，不会用其他条文凑答案
- RAG（向量检索）适用：法律解释/适用/怎么做等开放问题
  - 输出为两段：`法律依据` → `法律分析与建议`
  - 若命中“条件/责任”类意图且事实不足，可能追问 1–3 个问题（同一 session 最多 2 轮）

## 提示词（给小白照抄）
见 `PROMPT_ENGINEER.md`。

## 常见报错排查
- 报错：`Table 'xxx.user_account' doesn't exist`
  - 原因：当前 `mysql.url` 指向的库里还没建用户/会话/权限表。
  - 处理：在 `mysql.url` 指向的库执行 `sql/mysql_user_auth_schema.sql`，然后重启 Web/CLI。
- 报错：`Table 'xxx.qa_log' doesn't exist` 或 `Table 'xxx.agent_task_history' doesn't exist`
  - 原因：日志表未创建。
  - 处理：在 `mysql.url` 指向的库执行 `sql/mysql_agent_log_schema.sql`，然后重启 Web/CLI。
- 你在 `legal_ai` 建表但程序写不到
  - 原因：程序只会连 `mysql.url` 指向的那个库；你实际运行时连到哪个库，以报错里的 `xxx.` 或 `mysql.url` 为准。
