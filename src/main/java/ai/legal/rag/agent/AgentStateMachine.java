package ai.legal.rag.agent;

import ai.legal.rag.intent.LegalIntentType;

/**
 * 确定性的 Agent 状态机，控制一次请求内的状态迁移，避免隐式循环。
 */
public class AgentStateMachine {

    private FsmAgentState state = FsmAgentState.INIT;

    /**
     * 重置为初始状态。
     */
    public void reset() {
        state = FsmAgentState.INIT;
    }

    public FsmAgentState getState() {
        return state;
    }

    /**
     * 主决策入口：根据意图与是否命中法条定位，返回下一状态。
     *
     * @param intent           意图类型
     * @param hasLawLocator    是否命中“第X编/章/条/原文”等法条定位特征
     * @param factsSufficient  事实是否充分（仅在需事实核查的意图下使用）
     * @return 下一状态
     */
    public FsmAgentState next(LegalIntentType intent, boolean hasLawLocator, boolean factsSufficient) {
        // 状态机是单向的，同一输入不会回到已处理状态
        switch (state) {
            case INIT -> {
                state = FsmAgentState.INTENT_DETECTED;
                return next(intent, hasLawLocator, factsSufficient);
            }
            case INTENT_DETECTED -> {
                if (hasLawLocator || intent == LegalIntentType.LAW_TEXT_QUERY) {
                    state = FsmAgentState.DIRECT_LAW_QUERY;
                } else if (needsFactCheck(intent, factsSufficient)) {
                    state = FsmAgentState.FACT_CHECK;
                } else {
                    state = FsmAgentState.RAG_RETRIEVAL;
                }
                return state;
            }
            case DIRECT_LAW_QUERY -> {
                // 法条定位后直接结束，禁止进入事实核查
                state = FsmAgentState.FINISHED;
                return state;
            }
            case FACT_CHECK -> {
                // 事实核查后进入 LLM 输出（可包含补充提问）
                state = FsmAgentState.LLM_ANSWER;
                return state;
            }
            case RAG_RETRIEVAL -> {
                // 检索完进入 LLM 输出
                state = FsmAgentState.LLM_ANSWER;
                return state;
            }
            case LLM_ANSWER -> {
                state = FsmAgentState.FINISHED;
                return state;
            }
            case FINISHED -> {
                return state;
            }
            default -> {
                state = FsmAgentState.FINISHED;
                return state;
            }
        }
    }

    private boolean needsFactCheck(LegalIntentType intent, boolean factsSufficient) {
        if (intent == null) {
            return false;
        }
        // 仅责任/条件/是否合法类允许进入事实核查；若事实已充分则不再核查
        boolean factIntent = intent == LegalIntentType.LEGAL_LIABILITY
                || intent == LegalIntentType.LEGAL_CONDITION_CHECK
                || intent == LegalIntentType.APPLICABILITY;
        return factIntent && !factsSufficient;
    }
}
