package com.ktguru.chat.service;

import com.ktguru.chat.model.ChatMessage;
import com.ktguru.chat.model.ChatSession;
import com.ktguru.chat.model.UnansweredQuestion;
import com.ktguru.chat.repository.ChatMessageRepository;
import com.ktguru.chat.repository.ChatSessionRepository;
import com.ktguru.chat.repository.UnansweredQuestionRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String SYSTEM_KT_ANSWER = """
            You are KT Guru, a senior engineer helping with knowledge transfer.
            You must produce ONE consolidated answer (not a list of raw search hits).
            
            Use ONLY the information in CONTEXT when stating facts about this organization's systems, tickets, or code.
            If CONTEXT is empty or insufficient, say what is missing and give safe general guidance without inventing details.
            
            Structure your answer with these sections (use these headings):
            1) Problem understanding
            2) Step-by-step solution
            3) Alternative approaches (if any; otherwise say "None identified from available knowledge")
            4) Edge cases / risks
            5) Who usually handles this (infer from roles in CONTEXT if present; otherwise "Not specified in knowledge base")
            6) Approx resolution time (use ticket timing from CONTEXT when available; otherwise give a reasonable range and label as estimate)
            
            Be concise but mature. Do not hallucinate ticket numbers, file paths, or config values that are not in CONTEXT.
            """;

    private static final String SYSTEM_CLARIFY = """
            You are KT Guru. The user's question is too vague to search the knowledge base effectively.
            Reply with ONLY 2–4 short follow-up questions (bullet list) to narrow scope (e.g. internal vs external user, environment, subsystem, error text).
            Do not answer the technical question yet.
            """;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatLanguageModel chatLanguageModel;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final UnansweredQuestionRepository unansweredQuestionRepository;

    @Value("${kt-guru.ai.clarify.max-chars}")
    private int clarifyMaxChars;

    @Value("${kt-guru.ai.clarify.min-score}")
    private double clarifyMinScore;

    @Value("${kt-guru.ai.chat.history-pairs}")
    private int historyPairs;

    @Transactional
    public ChatResult chat(Long sessionId, String userText) {
        String message = userText == null ? "" : userText.trim();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("Message is required.");
        }

        ChatSession session = sessionId == null
                ? chatSessionRepository.save(new ChatSession())
                : chatSessionRepository.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("Unknown session: " + sessionId));

        List<ChatMessage> prior = chatMessageRepository.findBySession_IdOrderByCreatedAtAsc(session.getId());
        KnowledgeRetrievalService.RetrievalBundle bundle = knowledgeRetrievalService.retrieve(message);
        String context = bundle.contextBlock();
        boolean noKnowledge = context.isBlank();
        double bestVec = bundle.bestVectorScore();
        boolean shortQuery = message.length() <= clarifyMaxChars;
        boolean weakVector = bestVec < clarifyMinScore;

        boolean clarify = noKnowledge && shortQuery && weakVector;

        List<dev.langchain4j.data.message.ChatMessage> lc = new ArrayList<>();
        if (clarify) {
            lc.add(SystemMessage.from(SYSTEM_CLARIFY));
            appendHistory(lc, tail(prior, historyPairs * 2));
            lc.add(UserMessage.from("User: " + message));
        } else {
            lc.add(SystemMessage.from(SYSTEM_KT_ANSWER));
            appendHistory(lc, tail(prior, historyPairs * 2));
            String userBlock = context.isEmpty()
                    ? "CONTEXT:\n(none — no matching documents, code, or issue records were retrieved.)\n\nUSER QUESTION:\n" + message
                    : "CONTEXT:\n" + context + "\n\nUSER QUESTION:\n" + message;
            lc.add(UserMessage.from(userBlock));
        }

        Response<AiMessage> response = chatLanguageModel.generate(lc);
        String answer = response.content().text();

        ChatMessage u = new ChatMessage();
        u.setSession(session);
        u.setRole(ChatMessage.Role.USER);
        u.setContent(message);
        chatMessageRepository.save(u);

        ChatMessage a = new ChatMessage();
        a.setSession(session);
        a.setRole(ChatMessage.Role.ASSISTANT);
        a.setContent(answer);
        chatMessageRepository.save(a);

        if (noKnowledge && !clarify) {
            UnansweredQuestion uq = new UnansweredQuestion();
            uq.setQuestion(message);
            unansweredQuestionRepository.save(uq);
        }

        return new ChatResult(session.getId(), clarify ? ChatResult.Type.CLARIFICATION_NEEDED : ChatResult.Type.ANSWER, answer);
    }

    private static void appendHistory(List<dev.langchain4j.data.message.ChatMessage> lc, List<ChatMessage> slice) {
        for (ChatMessage m : slice) {
            if (m.getRole() == ChatMessage.Role.USER) {
                lc.add(UserMessage.from(m.getContent()));
            } else if (m.getRole() == ChatMessage.Role.ASSISTANT) {
                lc.add(AiMessage.from(m.getContent()));
            }
        }
    }

    private static List<ChatMessage> tail(List<ChatMessage> all, int max) {
        if (all.size() <= max) {
            return new ArrayList<>(all);
        }
        return new ArrayList<>(all.subList(all.size() - max, all.size()));
    }

    public record ChatResult(Long sessionId, Type type, String content) {
        public enum Type {
            ANSWER,
            CLARIFICATION_NEEDED
        }
    }
}
