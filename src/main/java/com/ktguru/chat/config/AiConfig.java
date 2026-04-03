package com.ktguru.chat.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.time.Duration;

@Configuration
public class AiConfig {

    @Value("${kt-guru.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${kt-guru.ai.ollama.chat-model}")
    private String chatModelName;

    @Value("${kt-guru.ai.ollama.embedding-model}")
    private String embeddingModelName;

    @Value("${kt-guru.ai.vector-store.file-path}")
    private String vectorStorePath;

    /**
     * Bean for text generation (Answering questions, summarization)
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(chatModelName)
                .temperature(0.2) // Low temperature = highly factual, less hallucination
                .timeout(Duration.ofMinutes(2)) // Local models take time
                .build();
    }

    /**
     * Bean for creating vectors from text chunks
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(embeddingModelName)
                .build();
    }

    /**
     * Vector store backed by a JSON file. Use {@link InMemoryEmbeddingStore#serializeToFile}
     * after ingesting so embeddings survive restarts.
     */
    @Bean
    public InMemoryEmbeddingStore<TextSegment> embeddingStore() {
        try {
            return InMemoryEmbeddingStore.fromFile(Paths.get(vectorStorePath));
        } catch (Exception e) {
            return new InMemoryEmbeddingStore<>();
        }
    }
}