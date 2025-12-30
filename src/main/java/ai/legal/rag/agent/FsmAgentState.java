package ai.legal.rag.agent;

/**
 * 显式有限状态机的状态定义。
 */
public enum FsmAgentState {
    INIT,
    INTENT_DETECTED,
    FACT_CHECK,
    DIRECT_LAW_QUERY,
    RAG_RETRIEVAL,
    LLM_ANSWER,
    FINISHED
}
