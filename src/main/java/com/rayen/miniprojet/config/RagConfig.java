package com.rayen.miniprojet.config;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.List;

@Configuration
public class RagConfig {

    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);

    @Value("classpath:docs/politique.pdf") // Le chemin de ton PDF
    private Resource pdfResource;

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // 1. Création du store en mémoire (léger pour 8Go RAM)
        SimpleVectorStore simpleVectorStore = new SimpleVectorStore(embeddingModel);

        // 2. Lecture du PDF
        if (pdfResource.exists()) {
            log.info("📖 Chargement du PDF : " + pdfResource.getFilename());
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
            List<Document> documents = pdfReader.get();

            // 3. Découpage en petits morceaux (Chunks) pour l'IA
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> splitDocuments = splitter.apply(documents);

            // 4. Stockage dans la mémoire vectorielle
            simpleVectorStore.add(splitDocuments);
            log.info("✅ PDF ingéré dans le VectorStore !");
        } else {
            log.warn("⚠️ Fichier politique.pdf introuvable dans resources/docs/");
        }

        return simpleVectorStore;
    }
}