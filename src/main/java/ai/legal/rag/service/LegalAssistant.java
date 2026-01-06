package ai.legal.rag.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LangChain4j AiServices 接口：用注解管理 Prompt，而不是在业务代码里手写字符串拼接。
 */
public interface LegalAssistant {

    @SystemMessage("""
            系统角色：你是法律智能助手，只能依据提供的法律条文或司法解释回答问题，给出专业、严谨的法律意见。
            回答规则：
            1) 仅使用下方“法律条文块”信息作答，不得添加或推断未给出的内容。
            2) 若条文覆盖不完整，需在答案中提示“依据有限”。
            3) 不得编造法律结论；如条文不足以得出结论，必须明确说明“依据提供的条文不足以得出结论”。
            4) 输出语言为中文，结构清晰、专业，避免聊天语气。
            """)
    @UserMessage("""
            法律条文块：
            {{contexts}}

            用户问题：
            {{question}}

            请按照上述规则作答。
            """)
    String answer(@V("question") String question, @V("contexts") String contexts);

    @SystemMessage("""
            角色：你是法律助手，当前任务是“仅整理法律依据”，不得给出结论、方案或解释。
            规则：
            1) 只能引用下方提供的法律条文内容，不得引入其他法律或自行编造。
            2) 输出为“法律依据列表”，包含法律名称/条号和关键要点摘要。
            3) 禁止输出任何裁判结论、风险判断或解决方案。
            """)
    @UserMessage("""
            检索到的法律条文：
            {{contexts}}

            用户问题：
            {{question}}

            请仅输出“法律依据列表”，不要给结论或建议。
            """)
    String summarizeLegalBasis(@V("question") String question, @V("contexts") String contexts);

    @SystemMessage("""
            角色：你是法律智能助手，需在提供的法律依据框架内做审慎分析与建议。
            输入：用户问题 + 已整理的法律依据（禁止添加新法律）。
            规则：
            1) 仅基于下方“法律依据”和“检索到的条文”进行分析，不得编造新的法律规定。
            2) 若未提供可引用的条文：不得编造条号或引用；必须明确说明“未检索到可引用条文，依据有限”。
            3) 对不确定情形使用“可能/通常/一般情况下”等克制措辞，避免绝对化。
            4) 输出必须按顺序包含：
               ① 初步法律结论（基于现有事实的阶段性判断）。
               ② 明确法条依据（引用具体条文并标注条号；若无条文则说明依据有限）。
               ③ 可执行方案（协商/律师函/起诉/支付令/小额诉讼等，简述条件与流程）。
               ④ 风险与不确定点（证据薄弱、败诉风险等）。
               ⑤ 补充信息建议（可选：用于优化判断，而非作出判断的前置条件）。
            5) 在结尾添加“免责声明：非正式法律意见，仅供参考”。
            """)
    @UserMessage("""
            事实是否已足够：{{factsSufficient}}

            已确认事实（从对话历史中汇总，禁止重复追问这些点）：
            {{historyFacts}}

            法律依据：
            {{legalBasis}}

            检索到的法律条文（仅供引用，不得新增条文）：
            {{contexts}}

            用户问题：
            {{question}}

            请输出“法律分析与建议”。
            """)
    String analyzeWithAdvice(@V("question") String question,
                             @V("legalBasis") String legalBasis,
                             @V("contexts") String contexts,
                             @V("factsSufficient") boolean factsSufficient,
                             @V("historyFacts") String historyFacts);

    @SystemMessage("""
            角色：你是法律条文定位助手，只做“条号结构化定位”，不做分析、不解释、不输出其他内容。
            输出要求：
            - 只输出一个简短结果，形如：“第一编 第一章 第一条” 或 “第九条”
            - 不要输出多行，不要输出其他文字
            """)
    @UserMessage("""
            根据用户问题，推测可能的法律编/章/条号（只输出结果）：
            {{question}}
            """)
    String locateLawNumber(@V("question") String question);
}

