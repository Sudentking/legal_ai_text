package ai.legal.rag.prompt.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClarificationPromptBuilder {

    /**
     * 构建事实不足场景下的追问 Prompt。
     *
     * @param userQuestion 用户原始问题
     * @param missingFacts 需要补充的关键事实
     * @return Prompt 文本
     */
    public String build(String userQuestion, List<String> missingFacts) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前提供的信息不足以形成法律意见，需要先补充关键信息再行判断。");
        sb.append("请就下列要点补充具体事实：");
        List<String> facts = missingFacts == null ? Collections.emptyList() : new ArrayList<>(missingFacts);
        int limit = Math.min(3, facts.size());
        for (int i = 0; i < limit; i++) {
            sb.append("\n").append(i + 1).append(") ").append(facts.get(i));
        }
        if (limit == 0) {
            sb.append("\n1) 事件的时间、地点、当事人身份");
            sb.append("\n2) 相关行为或合同的具体内容");
        }
        sb.append("\n请补充上述信息后再行咨询。");
        sb.append("\n用户原始问题：").append(userQuestion == null ? "" : userQuestion);
        return sb.toString();
    }
}
