package ai.legal.rag.agent;

/**
 * 显式的 Agent 状态机，避免依赖 LLM 推断。
 */
public enum AgentState {
    INIT,
    INTENT_RECOGNIZED,
    FACT_CHECK,
    SOLUTION_GENERATED,
    NEED_FOLLOW_UP,
    FINISHED
}
