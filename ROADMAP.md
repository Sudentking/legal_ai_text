# 中国法律知识智能问答系统（legal-ai-system）补全路线图

本文件用于把“最初目标 → 当前实现 → 欠缺点 → 逐步补全方案”固化下来，方便后续持续迭代，并保证代码风格一致。

## 0. 当前实现概况（已具备）
- 知识库导入：`.docx/.pdf/.txt` → 解析条文 → 写入 MySQL `law_text` → 分片 `law_text_chunk` → 向量入库 PostgreSQL `legal_embedding`
- 结构化法条直通：命中“原文/编/章/条/全文”类请求时，直接走 MySQL 结构化查询，禁止走向量
- RAG 问答：向量检索 + 条文重组 + 两阶段输出（法律依据 → 法律分析与建议）
- 智能 Agent：显式 FSM + 历史决策 + 有限追问（同一 session 最多 2 轮 FACT_CHECK）
- 用户/权限/日志：注册登录、SUPER_ADMIN 后台、`qa_log` 与 `agent_task_history` 两阶段写入（PENDING→SUCCESS/FAIL，异常必留 FAIL）

## 1. 欠缺点（影响上限）
### 1.1 召回质量地基不足（必须优先补）
- 当前向量 embedding 是“占位算法”，语义质量不足，导致召回不稳定（靠门控/SQL 兜底在补救）
- 缺少“可重建向量库”的工程化工具：无法稳定迭代 embedding 算法与检索策略
- 缺少检索评测：没有离线集来衡量 TopK/阈值/混合检索效果

### 1.2 知识库管理不完整
- 已有“导入”，但缺少管理能力：列表/预览/删除/重建/质量检查
- 缺少“导入后校验”：章节/条号错分、重复条文、缺失条文等

### 1.3 Agent 记忆与工具链不够通用
- `historyFacts` 目前主要依赖调用方传入；缺少系统内自动事实摘要/要件抽取（跨场景）
- 缺少“工具调用”式能力（例如：证据清单生成、流程清单生成、管辖/时效提示等）与可观测的工具执行记录

## 2. 补全总体策略（按优先级）
原则：先把“检索地基”工程化，再做“管理与可观测”，最后做“更强 Agent”。

### Phase A：检索/Embedding 工程化（优先）
目标：让检索可调、可重建、可验证，避免“看似智能但随机”。

交付（DoD）：
1) 统一 embedding 生成入口（导入与查询使用同一套实现），支持配置开关（保持兼容）
2) 提供向量重建工具（从 MySQL chunks 重建 PostgreSQL embeddings），支持 dry-run/分批/断点续跑
3) 引入更可靠的兜底检索（MySQL 关键词/全文索引）并与向量检索做确定性融合/门控
4) 增加一个离线评测脚手架：给定 query+期望 law/article，输出 hit@k、误召回示例

涉及模块：
- `ai.legal.util`：Embedding 生成器（新增）
- `ai.legal.service.importer.LawTextChunkService`：改为调用统一 embedding 生成器
- `ai.legal.rag.service.LegalRagQaService`：改为调用统一 embedding 生成器 + hybrid 检索
- `ai.legal.dao.mysql.LawTextDao`：增强关键词/全文检索能力
- `ai.legal.console`：新增重建工具 main

### Phase B：知识库管理闭环（Web/CLI）
目标：从“能导入”升级到“可管理、可清洗、可重建”。

交付（DoD）：
1) Admin 后台支持：law_text 列表/搜索/预览条文；显示分片数、是否已向量化
2) 支持删除一部法律或某条文（MySQL + PostgreSQL 同步清理）
3) 支持对某部法律执行“重分片/重向量化”（可选参数 chunkSize）
4) 导入质量检查：条号是否连续、章节错分检测（至少输出报告）

### Phase C：通用 Agent 记忆与工具链
目标：更像“法律助理”，而不是“只会问答/追问”。

交付（DoD）：
1) 会话事实结构化存储（按 session_id 存入 MySQL：已确认事实、来源轮次、更新时间）
2) 多场景 FactSufficiencyEvaluator：借贷/租赁/劳动/侵权等基础场景（规则优先，LLM 可选）
3) 工具链（deterministic）：证据清单、诉讼路径清单、风险点模板等，并写入任务历史

### Phase D：可观测与治理（可选但推荐）
目标：让系统可运营、可排障、可审计。

交付（DoD）：
1) 日志检索/导出（按 user/session/time/status）
2) 基础报表：日活、提问数、失败率、RAG 命中率、直通命中率
3) 权限体系完善：LOG_VIEW/LAW_IMPORT/KB_DELETE 等最小 RBAC 闭环

## 3. 代码规范与一致性约束（执行标准）
- 不引入 Spring/JPA；保持“纯 Java + JDBC + 明确依赖”风格
- 所有外部系统交互（MySQL/PostgreSQL/LLM）必须有：
  - 清晰的失败降级路径（不影响主流程）
  - 明确的错误信息（写入 `qa_log.error_message` / `agent_task_history.fail_reason`）
- 不在 Agent 内部引入隐式循环（while(true)）；状态迁移必须确定性
- 业务层（service）与数据层（dao）分离：SQL 不写在 Handler 中
- 新增配置优先放 `src/main/resources/application.properties`，并提供默认值与 README 说明

## 4. 下一步（本轮将落地）
Phase A 的第一批交付：
- Embedding 统一入口 + 配置开关（默认保持兼容）
- 向量重建/Reindex 工具（可断点续跑）
