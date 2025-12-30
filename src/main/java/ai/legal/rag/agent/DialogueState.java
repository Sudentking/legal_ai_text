package ai.legal.rag.agent;

/**
 * 会话级显式状态，避免无限追问。
 */
public enum DialogueState {
    INIT,
    FACT_COLLECTING,
    ANSWER_READY,
    ANSWERED,
    TERMINATED
}
