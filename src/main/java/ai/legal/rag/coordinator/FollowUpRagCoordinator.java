package ai.legal.rag.coordinator;

import ai.legal.rag.service.LegalRagQaService;

/**
 * 处理用户补充事实后的二次 RAG 闭环。
 */
public class FollowUpRagCoordinator {

    private final LegalRagQaService ragQaService;

    public FollowUpRagCoordinator(LegalRagQaService ragQaService) {
        this.ragQaService = ragQaService;
    }

    /**
     * 将原问题与补充事实合并后触发 RAG 问答。
     *
     * @param originalQuestion 原始问题
     * @param userSupplement   用户补充的事实
     * @return RAG 回答
     */
    public String answerWithSupplement(String originalQuestion, String userSupplement) {
        StringBuilder enhanced = new StringBuilder();
        enhanced.append(originalQuestion == null ? "" : originalQuestion);
        if (userSupplement != null && !userSupplement.trim().isEmpty()) {
            enhanced.append("\n已补充事实：").append(userSupplement.trim());
        }
        return ragQaService.answer(enhanced.toString());
    }
}
