package ai.legal.rag.intent;

/**
 * 法律意图类型。
 */
public enum LegalIntentType {
    // 法条原文/章节内容查询
    LAW_TEXT_QUERY,
    // 解释、定义类问题
    EXPLANATION,
    // 适用性判定/是否可以/是否合法
    APPLICABILITY,
    // 是否构成/是否满足条件/定性判断
    LEGAL_CONDITION_CHECK,
    // 责任/后果/赔偿等判断
    LEGAL_LIABILITY,
    // 程序/流程/怎么做
    PROCEDURE,
    // 需要补充事实或信息不足
    NEEDS_FACTS
}
