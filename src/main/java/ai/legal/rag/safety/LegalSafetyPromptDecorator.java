package ai.legal.rag.safety;

/**
 * 根据风险等级修饰 Prompt。
 */
public class LegalSafetyPromptDecorator {

    /**
     * 风险等级达到条件性或更高时，追加安全约束。
     *
     * @param originalPrompt 原 Prompt
     * @param riskLevel      风险等级
     * @return 修饰后的 Prompt
     */
    public String decorate(String originalPrompt, LegalAnswerRiskLevel riskLevel) {
        if (riskLevel == null || originalPrompt == null) {
            return originalPrompt;
        }
        if (riskLevel == LegalAnswerRiskLevel.SAFE_EXPLANATION) {
            return originalPrompt;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(originalPrompt);
        sb.append("\n\n安全提示：");
        sb.append("需结合具体事实判断，避免使用“必然”“一定”“当然构成”等绝对措辞。");
        sb.append(" 如提供的事实不足，请明确说明“依据提供的条文不足以得出结论”。");
        return sb.toString();
    }
}
