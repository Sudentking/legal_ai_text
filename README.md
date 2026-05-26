# Legal AI System — 法律智能问答系统

基于 **RAG 架构 + Agent 有限状态机** 的法律知识检索与智能分析系统。支持法条原文直查、语义向量检索、多轮追问收敛、流式 SSE 输出，已完成从技术验证到工程化落地的完整演进。

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Java 17 |
| 构建 | Maven + maven-shade-plugin（Fat JAR） |
| LLM 框架 | LangChain4j 0.35.0 + DeepSeek OpenAI-compatible API |
| 向量数据库 | PostgreSQL 16 + pgvector（IVFFlat 索引） |
| 业务数据库 | MySQL 8.0（用户/权限/日志/事实记忆） |
| Web 服务 | JDK `HttpServer`（零框架，纯标准库） |
| 前端 | 原生 HTML/CSS/JS（无构建工具，SSE 流式渲染） |
| DI/配置 | Spring Boot Starter 3.3.6 |
| 文档解析 | Apache POI（Word）、Apache PDFBox（PDF） |

## 核心架构

```
┌─────────────────────────────────────────────────┐
│                   Web Layer                      │
│         JDK HttpServer + SSE Streaming           │
├─────────────────────────────────────────────────┤
│                Agent FSM Layer                   │
│  意图识别 → 状态机决策 → 法条直查 / 追问 / RAG    │
├─────────────────────────────────────────────────┤
│                  RAG Engine                      │
│  向量检索(pgvector) → 门控过滤 → SQL兜底 → LLM   │
├──────────────────┬──────────────────────────────┤
│   MySQL (业务)    │   PostgreSQL (向量)           │
│   law_text       │   legal_embedding             │
│   user_account   │   pgvector <-> L2 Distance    │
│   qa_log …       │                              │
└──────────────────┴──────────────────────────────┘
```

## 功能模块

### 法条原文直查
- 支持 `《法律名》第X编 第X章 第X条` 结构化查询
- 命中后 **不走向量检索**，直接查 MySQL 返回原文
- 未命中明确提示，不会用相近条文凑答案
- 中文数字 ↔ 阿拉伯数字自动转换（"第一条" ↔ "第1条"）

### RAG 智能问答（三级级联检索）
```
用户问题
  → ① pgvector 向量相似度检索（L2 距离 Top-30）
  → ② 门控过滤（关键词打分，score ≥ 2 才可用）
  → ③ MySQL 关键词 LIKE 兜底（向量不可用时降级）
  → ④ 两阶段 LLM 生成：法律依据 → 法律分析与建议
```
- 场景自适应关键词扩展：借款/租赁/劳动/数据安全/网络爬虫
- 强制引用条号 + 克制措辞 + 免责声明
- 流式 SSE 输出，实时逐字显示

### Agent 有限状态机
```
INIT → INTENT_DETECTED
         ├── DIRECT_LAW_QUERY → FINISHED（法条直查）
         ├── FACT_CHECK → LLM_ANSWER → FINISHED（追问，最多2轮）
         └── RAG_RETRIEVAL → LLM_ANSWER → FINISHED（语义检索+生成）
```
- 单向不可逆，防止 LLM 自由决策导致的无限循环
- 每步决策写入 `agent_task_history`，全程可审计

### 多轮追问 + 事实记忆
- 4 个专有场景模板（未成年人借款 / 借贷 / 租赁 / 劳动）
- 通用场景自适应追问（无具体事实时触发）
- 用户回答自动抽取事实存入 `session_fact`，后续不再重复问
- 支持 `不要追问` / `直接给结论` 强制跳过追问

### 用户体系
- 注册/登录/会话管理（Cookie + 7天有效期）
- 角色：`USER`（普通用户）/ `SUPER_ADMIN`（管理员）
- 权限：细粒度 `PermissionCode`，`QA_ASK` 控制问答权限
- 密码加盐哈希存储

### 管理后台
- 统计面板：已导入法律数 / 条文总数 / 问答记录数 / 活跃会话数
- QA 日志 + Agent 决策历史（表格视图 + 详情面板）
- 知识库管理：法律列表 / 条文浏览 / 质量检查 / 重建切片向量 / 删除
- 批量文档导入（.docx / .pdf / .txt）
- 权限管理：授予/撤销/查看用户权限

### 流式输出
- `POST /api/ask/stream` → `text/event-stream`
- 前端 `ReadableStream` + `innerHTML` 实时渲染
- 后端两阶段流式：先推法律依据 → 再流式推送分析 token

## 目录结构

```
legal_ai/
├── pom.xml
├── README.md
├── PROMPT_ENGINEER.md          # 用户提问指南
├── DATABASE_SCHEMA.md           # 数据库架构文档
├── sql/                         # DDL 脚本
│   ├── mysql_user_auth_schema.sql
│   ├── mysql_agent_log_schema.sql
│   └── mysql_session_fact_schema.sql
└── src/main/
    ├── java/ai/legal/
    │   ├── config/              # 数据库连接配置
    │   ├── console/             # CLI 入口 / 初始化脚本
    │   ├── dao/                 # 数据访问层 (MySQL + pgvector)
    │   ├── importer/            # 文档导入解析 (.docx/.pdf/.txt)
    │   ├── model/               # 数据模型
    │   ├── rag/
    │   │   ├── agent/           # Agent FSM 状态机
    │   │   ├── intent/          # 意图识别
    │   │   ├── prompt/          # LLM Prompt 构建（4 个 Builder）
    │   │   └── service/         # RAG / LLM / 法条直查 / 事实评估
    │   ├── security/            # 密码哈希
    │   ├── service/             # 业务服务层
    │   │   ├── auth/            # 认证与权限
    │   │   ├── importer/        # 向量导入
    │   │   ├── kb/              # 知识库维护/质量
    │   │   ├── memory/          # 事实记忆 / 追问生成
    │   │   └── tool/            # 确定性工具建议
    │   ├── util/                # Embedding / 分词 / 法条编号
    │   └── web/                 # HttpServer 路由与处理器
    └── resources/
        ├── application.properties
        └── web/                 # 前端资源
            ├── admin.html       # 管理后台
            ├── app.html         # 用户提问页
            ├── login.html       # 登录页
            ├── register.html    # 注册页
            └── assets/
                ├── admin.js
                ├── app.js
                ├── common.js
                ├── login.js
                ├── register.js
                └── styles.css
```

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- PostgreSQL 16+ + pgvector 扩展

### 1. 配置数据库

编辑 `src/main/resources/application.properties`：

```properties
# PostgreSQL (向量库)
db.url=jdbc:postgresql://YOUR_HOST:5432/legal_vector
db.user=postgres
db.password=YOUR_PASSWORD

# MySQL (业务库)
mysql.url=jdbc:mysql://YOUR_HOST:3306/legal_ai?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
mysql.user=root
mysql.password=YOUR_PASSWORD

# Embedding 模式
embedding.mode=legacy

# DeepSeek
deepseek.api.key=YOUR_API_KEY
deepseek.base-url=https://api.deepseek.com
deepseek.model=deepseek-chat
deepseek.temperature=0.0
```

### 2. 初始化数据库

```bash
mysql -h YOUR_HOST -u root -p legal_ai < sql/mysql_user_auth_schema.sql
mysql -h YOUR_HOST -u root -p legal_ai < sql/mysql_agent_log_schema.sql
mysql -h YOUR_HOST -u root -p legal_ai < sql/mysql_session_fact_schema.sql
```

### 3. 构建 & 运行

```bash
mvn clean package -DskipTests
java -jar target/legal-ai-system-1.0-SNAPSHOT.jar
```

启动后访问：
| 页面 | 地址 |
|------|------|
| 登录 | `http://localhost:8080/login` |
| 注册 | `http://localhost:8080/register` |
| 提问 | `http://localhost:8080/app` |
| 后台 | `http://localhost:8080/admin` |

### 4. 创建超级管理员

```bash
java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.console.InitSuperAdminMain root_user root_password
```

### 5. 导入法律数据

在管理后台 → 批量导入，输入文件路径（每行一个）：
```
/path/to/民法典.docx
/path/to/网络安全法.pdf
/path/to/劳动法.txt
```

## 使用指南

详细提问模板见 [PROMPT_ENGINEER.md](PROMPT_ENGINEER.md)，三种主要用法：

| 类型 | 示例 | 处理方式 |
|------|------|----------|
| 法条原文 | `《中华人民共和国民法典》第一条原文` | SQL 直查，不走 LLM |
| 按关键词 | `《网络安全法》关于个人信息的法条` | SQL LIKE 关键词匹配 |
| 法律分析 | `欠钱不还怎么办` + 已知事实 | RAG 两阶段生成 |

## 设计原则

- **准确性优先**：法条原文直查不依赖 LLM，零幻觉风险；RAG 路径强制引用条号
- **可审计性**：Agent 每一步决策写入 `agent_task_history`，全程可追溯
- **零框架开销**：Web 层纯 JDK HttpServer，无 Spring MVC / Tomcat
- **防御性设计**：权限空列表 = 拒绝访问；段落级 XSS 防护；危险操作二次确认

## CLI 模式

```bash
java -cp target/legal-ai-system-1.0-SNAPSHOT.jar ai.legal.console.LegalQaCli
```

常用命令：
- `:register <username> <password>` — 注册
- `:login <username> <password>` — 登录
- `:init-admin <username> <password>` — 创建超级用户
- `:grant <username> <PERMISSION_CODE>` — 授予权限
- `:revoke <username> <PERMISSION_CODE>` — 撤销权限

## License

MIT
