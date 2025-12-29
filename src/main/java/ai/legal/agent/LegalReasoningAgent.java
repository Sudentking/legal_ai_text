package ai.legal.agent;

import ai.legal.agent.intent.LegalIntent;
import ai.legal.agent.intent.LegalIntentResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 事实不足判断 + 追问的法律 Agent。
 */
public class LegalReasoningAgent {

    /**
     * 根据意图与置信度判断是否需要补充事实。
     *
     * @param userQuestion    用户原始问题
     * @param intentResult    意图分类结果
     * @return AgentDecision  决策结果
     */
    public AgentDecision decide(String userQuestion, LegalIntentResult intentResult) {
        if (intentResult == null) {
            return buildNeedFactsDecision(userQuestion, "缺少意图识别结果", defaultMissingFacts());
        }

        // 置信度不足或直接标记为事实不足时，请求补充事实
        if (intentResult.getIntent() == LegalIntent.FACT_INSUFFICIENT || intentResult.getConfidence() < 0.6) {
            return buildNeedFactsDecision(userQuestion, "信息不足：" + intentResult.getReason(), inferMissingFacts(intentResult));
        }

        // 特定意图下判断是否缺乏关键要素
        if (intentResult.getIntent() == LegalIntent.LEGAL_CONDITION_CHECK) {
            return buildNeedFactsDecision(userQuestion,
                    "条件判断需要明确行为和情形，当前信息不足",
                    Arrays.asList("相关行为或事件的具体描述", "涉及的主体及其身份", "发生时间与地点", "相关合同/约定/背景"));
        }

        if (intentResult.getIntent() == LegalIntent.LEGAL_LIABILITY) {
            return buildNeedFactsDecision(userQuestion,
                    "责任判断需要行为细节与因果，当前信息不足",
                    Arrays.asList("行为或过错的具体描述", "损害结果及证据", "因果关系说明", "当事人身份与角色"));
        }

        // 可进入 RAG
        return new AgentDecision(true, null, new ArrayList<>());
    }

    private AgentDecision buildNeedFactsDecision(String userQuestion, String reason, List<String> missing) {
        StringBuilder followUp = new StringBuilder();
        followUp.append("依据现有信息，无法直接作出法律意见。").append(reason == null ? "" : " ").append("请补充：");
        for (int i = 0; i < missing.size(); i++) {
            followUp.append(missing.get(i));
            if (i < missing.size() - 1) {
                followUp.append("；");
            }
        }
        if (followUp.charAt(followUp.length() - 1) != '。') {
            followUp.append("。");
        }
        return new AgentDecision(false, followUp.toString(), missing);
    }

    private List<String> inferMissingFacts(LegalIntentResult intentResult) {
        if (intentResult == null || intentResult.getIntent() == null) {
            return defaultMissingFacts();
        }
        switch (intentResult.getIntent()) {
            case LEGAL_CONDITION_CHECK:
                return Arrays.asList("具体情形描述", "当事人身份与关系", "相关合同或约定", "时间、地点等关键事实");
            case LEGAL_LIABILITY:
                return Arrays.asList("行为或过错的具体描述", "损害结果及证据", "因果关系说明", "当事人身份与角色");
            default:
                return defaultMissingFacts();
        }
    }

    private List<String> defaultMissingFacts() {
        return Arrays.asList("事件的时间、地点、当事人身份", "具体行为或合同内容", "已有证据或材料");
    }
}
