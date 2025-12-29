package ai.legal.agent.intent;

/**
 * 法律意图枚举。
 */
public enum LegalIntent {
    LEGAL_EXPLANATION,      // 法条解释
    LEGAL_SCOPE,            // 适用范围
    LEGAL_CONDITION_CHECK,  // 条件判断
    LEGAL_LIABILITY,        // 法律责任
    LEGAL_PROCEDURE,        // 程序性问题
    FACT_INSUFFICIENT,      // 事实不足
    UNKNOWN                 // 无法识别
}
