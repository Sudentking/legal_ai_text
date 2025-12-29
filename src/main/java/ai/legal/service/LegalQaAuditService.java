package ai.legal.service;

import ai.legal.dao.LegalQaAuditLogDao;
import ai.legal.model.LegalQaAuditLog;

import java.time.Instant;
import java.util.List;
import java.util.StringJoiner;

/**
 * 审计日志服务，负责记录问答链路。
 */
public class LegalQaAuditService {

    private final LegalQaAuditLogDao dao;

    public LegalQaAuditService(LegalQaAuditLogDao dao) {
        this.dao = dao;
    }

    public void record(String requestId,
                       String userId,
                       String userQuestion,
                       String intentType,
                       List<Long> lawIds,
                       List<String> articleNos,
                       String answerText,
                       boolean factInsufficient) {
        LegalQaAuditLog log = new LegalQaAuditLog();
        log.setRequestId(requestId);
        log.setUserId(userId);
        log.setUserQuestion(userQuestion);
        log.setIntentType(intentType);
        log.setRetrievedLawIds(joinLongs(lawIds));
        log.setRetrievedArticleNos(joinStrings(articleNos));
        log.setAnswerText(answerText);
        log.setFactInsufficient(factInsufficient);
        log.setCreatedAt(Instant.now());
        dao.insert(log);
    }

    private String joinLongs(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Long v : values) {
            if (v != null) {
                joiner.add(v.toString());
            }
        }
        return joiner.toString();
    }

    private String joinStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(",");
        for (String v : values) {
            if (v != null) {
                joiner.add(v);
            }
        }
        return joiner.toString();
    }
}
