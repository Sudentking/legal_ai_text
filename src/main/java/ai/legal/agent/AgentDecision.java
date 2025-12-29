package ai.legal.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent 对是否进入 RAG 的决策结果。
 */
public class AgentDecision {
    private final boolean allowRag;
    private final String followUpQuestion;
    private final List<String> missingFacts;

    public AgentDecision(boolean allowRag, String followUpQuestion, List<String> missingFacts) {
        this.allowRag = allowRag;
        this.followUpQuestion = followUpQuestion;
        if (missingFacts == null) {
            this.missingFacts = Collections.emptyList();
        } else {
            this.missingFacts = new ArrayList<>(missingFacts);
        }
    }

    public boolean isAllowRag() {
        return allowRag;
    }

    public String getFollowUpQuestion() {
        return followUpQuestion;
    }

    public List<String> getMissingFacts() {
        return Collections.unmodifiableList(missingFacts);
    }
}
