package ai.legal.rag.intent;

/**
 * 法律意图类型。
 */
public enum LegalIntentType {
    // 解释、定义类问题
    EXPLANATION,
    // 适用性判定/是否可以/是否合法
    APPLICABILITY,
    // 程序/流程/怎么做
    PROCEDURE,
    // 需要补充事实或信息不足
    NEEDS_FACTS
}
