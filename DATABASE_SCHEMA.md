# 法律AI系统 — 双数据库架构文档

> 探查时间：2026-05-24  
> MySQL：192.168.5.143:3306 / `legal_ai`  
> PostgreSQL：192.168.5.143:5432 / `legal_vector`

---

## 一、架构总览

```
┌─────────────────────────────────────────────────┐
│                   MySQL (legal_ai)                │
│              业务数据库 — 结构化事务处理            │
│         192.168.5.143:3306  |  7 张表              │
├─────────────────────────────────────────────────┤
│  law_text            1,956 行   法律条文原文       │
│  user_account            5 行   用户账号           │
│  user_permission         4 行   用户权限           │
│  user_session            9 行   用户会话           │
│  qa_log                 54 行   问答日志           │
│  agent_task_history     63 行   Agent决策历史      │
│  session_fact            5 行   会话事实记忆        │
└─────────────────────────────────────────────────┘
                         ↑
                    law_id 关联
                         ↓
┌─────────────────────────────────────────────────┐
│              PostgreSQL (legal_vector)            │
│           向量数据库 — 语义相似度检索               │
│      192.168.5.143:5432  |  2 张表                │
├─────────────────────────────────────────────────┤
│  legal_embedding      1,956 行  向量嵌入（活跃）   │
│  legal_embedding_text     0 行  向量嵌入（备用）   │
└─────────────────────────────────────────────────┘
```

**为什么需要两个数据库？**

| 维度 | MySQL | PostgreSQL |
|------|-------|------------|
| **角色** | 业务数据的事务性操作 | 语义相似度的高维向量检索 |
| **核心能力** | ACID、关系查询、SQL 过滤 | pgvector `<->` 算子、IVFFlat 索引 |
| **存什么** | 条文原文 + 用户/权限/日志/事实 | 1536 维 Embedding 向量 + 对应条文元数据 |
| **不能合并原因** | MySQL 不支持向量类型和算子 | pgvector 是 PostgreSQL 独有扩展 |

---

## 二、MySQL — 业务数据库（legal_ai）

### 2.1 `law_text` — 法律条文原文（核心知识库）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 主键 |
| `law_code` | VARCHAR(255) | NOT NULL | 法律唯一标识，如 `中华人民共和国民法典_20200528` |
| `law_title` | VARCHAR(255) | NOT NULL | 法律名称 + 章节标题 |
| `article_number` | VARCHAR(50) | NOT NULL | 条文编号，如 `第一条`、`第五百六十三条` |
| `full_text` | TEXT | NOT NULL | 条文完整原文 |
| `import_source` | VARCHAR(512) | NULL | 导入来源文件路径 |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：
- `PRIMARY KEY (id)`
- `INDEX idx_law_text_code_article (law_code, article_number)`

**作用**：存储全部法律条文的原文文本，是系统的知识底座。SQL 关键词检索兜底策略直接查询此表。

**统计**：1,956 行，1 部法律（《中华人民共和国民法典》）。

**样本数据**：
| id | law_code | article_number | law_title |
|----|----------|---------------|-----------|
| 1 | 中华人民共和国民法典_20200528 | 第一条 | 第一章 基本规定 |
| 2 | 中华人民共和国民法典_20200528 | 第二条 | 第一章 基本规定 |
| 535 | 中华人民共和国民法典_20200528 | 第五百三十五条 | 第五章 合同的保全 |

---

### 2.2 `user_account` — 用户账号

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 主键 |
| `username` | VARCHAR(64) | NOT NULL, UNIQUE | 登录用户名 |
| `password_hash` | VARCHAR(256) | NOT NULL | 加密后的密码哈希值 |
| `password_salt` | VARCHAR(64) | NOT NULL | 密码盐值 |
| `role` | VARCHAR(32) | NOT NULL | 角色：`USER` / `SUPER_ADMIN` |
| `status` | VARCHAR(16) | NOT NULL | 状态：`ACTIVE` / `DISABLED` |
| `last_login_at` | TIMESTAMP | NULL | 最后登录时间 |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY (username)`

**作用**：用户注册、登录认证与角色管理。密码通过 `PasswordHasher` 类加盐哈希安全存储。

**统计**：5 行（1 个 SUPER_ADMIN + 4 个普通用户）。

**样本数据**：
| id | username | role | status |
|----|----------|------|--------|
| 1 | superadmin | SUPER_ADMIN | ACTIVE |
| 2 | user1 | USER | ACTIVE |

---

### 2.3 `user_permission` — 用户权限

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT | NOT NULL | 关联 `user_account.id` |
| `permission_code` | VARCHAR(64) | NOT NULL | 权限码，如 `QA_ASK` |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_user_perm (user_id, permission_code)`
- `INDEX idx_user_perm_user (user_id)`

**作用**：细粒度权限控制。SUPER_ADMIN 自动拥有全部权限；普通用户需显式授予权限方可提问。

**统计**：4 行，全部为 `QA_ASK` 权限。

---

### 2.4 `user_session` — 用户会话

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 主键 |
| `session_id` | VARCHAR(128) | NOT NULL, UNIQUE | UUID 格式会话标识 |
| `user_id` | BIGINT | NOT NULL | 关联 `user_account.id` |
| `status` | VARCHAR(16) | NOT NULL | 状态：`ACTIVE` / `REVOKED` |
| `expires_at` | TIMESTAMP | NULL | 过期时间 |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY (session_id)`
- `INDEX idx_user_session_user (user_id)`

**作用**：无状态 HTTP 的登录状态维持。Cookie `SESSION_ID` 查此表确认身份，有效期 7 天。

**统计**：9 行。

---

### 2.5 `qa_log` — 问答日志

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 主键 |
| `user_id` | BIGINT | NOT NULL | 提问用户 ID |
| `session_id` | VARCHAR(128) | NOT NULL | 会话 ID |
| `question` | TEXT | NOT NULL | 用户原始问题 |
| `answer` | TEXT | NULL | 系统最终回答 |
| `agent_state` | VARCHAR(50) | NULL | 处理结束时的状态机状态 |
| `status` | VARCHAR(20) | NOT NULL | 状态：`PENDING` / `SUCCESS` / `FAIL` |
| `error_message` | TEXT | NULL | 错误信息 |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：
- `PRIMARY KEY (id)`
- `INDEX idx_qa_log_session (session_id)`
- `INDEX idx_qa_log_user (user_id)`

**作用**：记录每次问答的完整输入输出，用于审计、调试、事实记忆回溯。

**统计**：54 行。

---

### 2.6 `agent_task_history` — Agent 决策流水

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 主键 |
| `session_id` | VARCHAR(64) | NOT NULL | 会话 ID |
| `user_query` | TEXT | NOT NULL | 用户问题原文 |
| `intent_type` | VARCHAR(50) | NULL | 意图类型：`LAW_TEXT_QUERY` / `LEGAL_ANALYSIS` |
| `agent_state` | VARCHAR(50) | NULL | 状态机当前状态 |
| `decision_reason` | TEXT | NULL | 决策理由 |
| `strategy_used` | VARCHAR(50) | NULL | 执行策略：`SQL` / `VECTOR` / `FACT_CHECK` / `TOOL` |
| `result_status` | VARCHAR(20) | NULL | 结果：`PENDING` / `SUCCESS` / `FAIL` |
| `fail_reason` | TEXT | NULL | 失败原因 |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：
- `PRIMARY KEY (id)`
- `INDEX idx_agent_task_session (session_id)`
- `INDEX idx_agent_task_created (created_at)`

**作用**：记录 Agent 状态机每一轮的决策全过程——识别了什么意图、选择了什么策略、成功与否。这是幻觉控制和追问收敛的核心决策依据。

**统计**：63 行。

---

### 2.7 `session_fact` — 多轮对话事实记忆

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 主键 |
| `session_id` | VARCHAR(128) | NOT NULL | 会话 ID |
| `fact_key` | VARCHAR(100) | NOT NULL | 事实键，如 `debtor.attitude` |
| `fact_value` | TEXT | NULL | 事实值，如 `拒不履行/失联` |
| `source_type` | VARCHAR(20) | NULL | 来源：`USER` / `AGENT` |
| `source_message` | TEXT | NULL | 来源消息原文 |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | TIMESTAMP | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_session_fact (session_id, fact_key)`
- `INDEX idx_session_fact_session (session_id)`

**作用**：在多轮追问中持久化用户提供的关键事实，避免每轮重复询问。例如用户首次说"借款人失联了"，系统记录 `debtor.attitude=失联`，后续轮次不再重复追问。

**统计**：5 行。

**样本数据**：
| session_id | fact_key | fact_value |
|------------|----------|------------|
| sess_063e... | debtor.attitude | 拒不履行/失联 |
| sess_0a98... | debtor.attitude | 拒不履行/失联 |

---

## 三、PostgreSQL — 向量数据库（legal_vector）

### 3.1 `legal_embedding` — 向量嵌入（活跃表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | bigserial | PK | 主键，自增 |
| `law_id` | int8 | NOT NULL | **关联 MySQL `law_text.id`** |
| `article_no` | varchar(50) | NULL | 条文编号 |
| `chunk_index` | int4 | NOT NULL | 分块索引（长条文会被切分） |
| `embedding` | **vector(1536)** | NOT NULL | ★ **pgvector 向量，1536 维** |
| `content` | text | NOT NULL | 条文原文内容 |
| `source` | varchar(255) | NULL | 来源标识 |
| `created_at` | timestamp | DEFAULT now() | 创建时间 |

**索引**：
- `PRIMARY KEY (legal_embedding_pkey)` — 主键
- `INDEX legal_embedding_ivfflat_idx (embedding)` — **IVFFlat 向量索引，加速 `<->` 算子**

**作用**：RAG 检索引擎核心。将用户问题转为 1536 维向量，通过 `embedding <-> query_vector`（L2 欧氏距离）查找语义最相似的法条，实现语义级别而非关键词级别的检索。

**统计**：1,956 行，与 MySQL `law_text` 一一对应。

**样本数据**：
| id | law_id | article_no | source |
|----|--------|------------|--------|
| 1343 | 1 | 第一条 | 中华人民共和国民法典_20200528 第一章 基本规定 |
| 1344 | 2 | 第二条 | 中华人民共和国民法典_20200528 第一章 基本规定 |

> ⚠️ **当前使用 `embedding.mode=legacy`**（占位向量，由 hashCode 生成），不是真正的语义模型 Embedding。如需高质量语义检索请切换为 `hash_ngram_v1` 模式或接入外部 Embedding API。

---

### 3.2 `legal_embedding_text` — 向量嵌入（备用表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | bigserial | PK | 主键 |
| `law_id` | int8 | NOT NULL | 关联 MySQL `law_text.id` |
| `article_no` | varchar(50) | NULL | 条文编号 |
| `chunk_index` | int4 | NOT NULL | 分块索引 |
| `embedding` | vector(1536) | NOT NULL | 向量 |
| `content` | text | NOT NULL | 条文原文 |
| `source` | varchar(255) | NULL | 来源 |
| `created_at` | timestamp | DEFAULT now() | 创建时间 |

**索引**：
- `PRIMARY KEY (legal_embedding_text_pkey)`

**作用**：结构与 `legal_embedding` 完全相同，当前为**空表**（0 行）。可能为未来基于全文文本的 Embedding 预留。

---

## 四、双数据库协作流程

```
用户提问
    │
    ▼
┌─────────────────────────────────────────────────┐
│  ① 向量检索 (PostgreSQL)                         │
│  EmbeddingUtil.embed(question) → 1536 维向量      │
│  SELECT ... FROM legal_embedding                  │
│  ORDER BY embedding <-> query_vector              │
│  LIMIT 30                                        │
│  → 语义相似度 Top-30                              │
└────────┬────────────────────────────────────────┘
         │  门控过滤：关键词打分（score >= 2 才认为可用）
         │  若向量结果不可用 ↓
         ▼
┌─────────────────────────────────────────────────┐
│  ② SQL 关键词兜底 (MySQL)                        │
│  SELECT * FROM law_text                          │
│  WHERE full_text LIKE '%关键词%'                  │
│  → 保守门控：最高分 < 2 则放弃，避免胡乱引用         │
└────────┬────────────────────────────────────────┘
         │  通过 law_id 关联 MySQL law_text
         │  聚合上下文字段
         ▼
┌─────────────────────────────────────────────────┐
│  ③ 两阶段 LLM 生成（DeepSeek）                    │
│  阶段1: LegalBasisPromptBuilder                   │
│         → "法律依据：第X条 规定..."                │
│  阶段2: LegalAnalysisPromptBuilder                │
│         → "法律分析与建议：根据上述条文..."          │
│  → 流式 SSE 推送至前端                            │
└─────────────────────────────────────────────────┘
```

---

## 五、表间关系图

```
┌──────────────────┐
│   user_account    │
│  (用户账号)       │────<  user_permission
└────────┬─────────┘       (用户权限)
         │
         ├────<  user_session
         │       (登录会话)
         │
         ├────<  qa_log
         │       (问答日志)
         │
         └────<  agent_task_history
                 (Agent 决策流水)

┌──────────────────┐         ┌──────────────────────┐
│    law_text       │──law_id─│  legal_embedding     │
│  (MySQL 条文原文)  │         │  (PG 向量嵌入,活跃)  │
└──────────────────┘         └──────────────────────┘
                                     │
                                     └── legal_embedding_text
                                         (PG 向量嵌入,备用,空)

┌──────────────────┐
│   session_fact    │  独立于上述，按 session_id 关联
│  (会话事实记忆)    │
└──────────────────┘
```

---

## 六、统计数据总结

| 数据库 | 表名 | 行数 | 用途 |
|--------|------|------|------|
| MySQL | `law_text` | 1,956 | 民法典全部条文原文 |
| MySQL | `user_account` | 5 | 注册用户 |
| MySQL | `user_permission` | 4 | 权限分配 |
| MySQL | `user_session` | 9 | 登录会话 |
| MySQL | `qa_log` | 54 | 问答历史 |
| MySQL | `agent_task_history` | 63 | Agent 决策记录 |
| MySQL | `session_fact` | 5 | 多轮事实记忆 |
| PG | `legal_embedding` | 1,956 | 1536 维向量（活跃） |
| PG | `legal_embedding_text` | 0 | 1536 维向量（备用） |
