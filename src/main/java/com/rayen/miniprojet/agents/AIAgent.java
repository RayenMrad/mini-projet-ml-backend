package com.rayen.miniprojet.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import com.rayen.miniprojet.config.RagConfig;
import com.rayen.miniprojet.tools.StockTools;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIAgent {
    private final ChatClient.Builder builder;
    private final StockTools stockTools;
    private final VectorStore vectorStore;
    private ChatClient chatClient;
    
    // Seuil de tokens pour décider si on envoie tout le document
    private static final int SMALL_DOCUMENT_THRESHOLD = 10000; // ~3 pages

    @PostConstruct
    public void init() {
        log.info("🤖 Initialisation de l'AIAgent UNIVERSEL avec RAG Hybride");
        
        // Vérifier si le document est assez petit pour être envoyé en entier
        String fullText = RagConfig.getFullDocumentText();
        boolean isSmallDocument = fullText.length() < SMALL_DOCUMENT_THRESHOLD;
        
        if (isSmallDocument) {
            log.info("📄 Document petit ({} caractères) - Mode contexte complet activé", fullText.length());
            
            // MODE 1 : Document complet dans le system prompt
            this.chatClient = builder
                .defaultSystem("""
                    Tu es un assistant expert qui répond UNIQUEMENT à partir du document suivant.
                    
                    === DOCUMENT COMPLET ===
                    """ + fullText + """
                    
                    === FIN DU DOCUMENT ===
                    
                    INSTRUCTIONS :
                    1. Lis ATTENTIVEMENT tout le document ci-dessus
                    2. Réponds UNIQUEMENT avec les informations du document
                    3. Cite toujours la section et les valeurs exactes
                    4. Format : "D'après le document (Section X.X) : [détails]"
                    5. Si l'info n'est pas dans le document : dis "Information non trouvée dans le document"
                    
                    Ne donne JAMAIS d'informations générales ou inventées.
                    """)
                .defaultFunctions("getProductInfo", "getLowStockProducts")
                .build();
        } else {
            log.info("📚 Document volumineux ({} caractères) - Mode RAG hybride activé", fullText.length());
            
            // MODE 2 : RAG classique pour documents volumineux
            this.chatClient = builder
                .defaultSystem("""
                    Tu es un assistant expert qui répond à partir des extraits de documents fournis.
                    
                    INSTRUCTIONS :
                    1. Le contexte ci-dessous contient les passages pertinents du document
                    2. Lis ATTENTIVEMENT tous les extraits fournis
                    3. Réponds en citant les sections et valeurs exactes
                    4. Format : "D'après le document (Section X.X) : [détails]"
                    5. Si l'info n'est pas dans le contexte : dis "Information non trouvée dans les extraits fournis"
                    
                    Ne donne JAMAIS d'informations générales ou inventées.
                    """)
                .defaultFunctions("getProductInfo", "getLowStockProducts")
                .build();
        }
        
        log.info("✅ AIAgent UNIVERSEL initialisé avec succès");
    }

    public String chat(String userQuery) {
        log.info("💬 Question reçue : {}", userQuery);
        
        try {
            // Récupérer le contexte pertinent
            String context = getRelevantContext(userQuery);
            
            // Si document petit, le contexte est déjà dans le system prompt
            if (RagConfig.getFullDocumentText().length() < SMALL_DOCUMENT_THRESHOLD) {
                log.info("📄 Utilisation du contexte complet du system prompt");
                String response = chatClient.prompt()
                        .user(userQuery)
                        .call()
                        .content();
                
                log.info("✅ Réponse générée ({} caractères)", response.length());
                return response;
            } else {
                // Pour documents volumineux, ajouter le contexte à la requête
                String enrichedQuery = """
                    CONTEXTE DU DOCUMENT :
                    """ + context + """
                    
                    QUESTION :
                    """ + userQuery;
                
                log.info("📚 Contexte ajouté ({} caractères)", context.length());
                
                String response = chatClient.prompt()
                        .user(enrichedQuery)
                        .call()
                        .content();
                
                log.info("✅ Réponse générée ({} caractères)", response.length());
                return response;
            }
            
        } catch (Exception e) {
            log.error("❌ Erreur lors du traitement de la requête", e);
            return "Désolé, une erreur s'est produite : " + e.getMessage();
        }
    }
    
    /**
     * Récupère le contexte pertinent avec stratégie hybride :
     * 1. Essayer recherche vectorielle
     * 2. Si échec, utiliser recherche par mots-clés
     * 3. Si échec, retourner tout le document
     */
    private String getRelevantContext(String query) {
        log.info("🔍 Recherche de contexte pertinent pour : {}", query);
        
        List<Document> relevantDocs = new ArrayList<>();
        
        // STRATÉGIE 1 : Recherche vectorielle
        try {
            relevantDocs = vectorStore.similaritySearch(
                SearchRequest.query(query)
                    .withTopK(10)
                    .withSimilarityThreshold(0.2)  // Seuil très bas
            );
            log.info("🎯 Recherche vectorielle : {} documents trouvés", relevantDocs.size());
        } catch (Exception e) {
            log.warn("⚠️ Recherche vectorielle échouée : {}", e.getMessage());
        }
        
        // STRATÉGIE 2 : Si vectorielle échoue, recherche par mots-clés
        if (relevantDocs.isEmpty()) {
            log.info("🔄 Passage à la recherche par mots-clés...");
            relevantDocs = RagConfig.keywordSearch(query, 10);
            log.info("📝 Recherche par mots-clés : {} documents trouvés", relevantDocs.size());
        }
        
        // STRATÉGIE 3 : Si tout échoue, retourner tout le document
        if (relevantDocs.isEmpty()) {
            log.warn("⚠️ Aucun résultat - Utilisation du document complet");
            return RagConfig.getFullDocumentText();
        }
        
        // Construire le contexte à partir des documents trouvés
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < relevantDocs.size(); i++) {
            Document doc = relevantDocs.get(i);
            context.append("--- Extrait ").append(i + 1).append(" ---\n");
            context.append(doc.getContent()).append("\n\n");
            
            // Log pour debug
            log.info("📄 Extrait {} (150 premiers chars) : {}", 
                     i + 1, 
                     doc.getContent().substring(0, Math.min(150, doc.getContent().length())));
        }
        
        return context.toString();
    }
    
    /**
     * Méthode de test pour diagnostiquer le RAG
     */
    public String testRag(String query) {
        log.info("🔬 Test du système RAG pour : {}", query);
        
        StringBuilder report = new StringBuilder();
        report.append("=== DIAGNOSTIC RAG ===\n\n");
        
        // Test 1 : Taille du document
        String fullText = RagConfig.getFullDocumentText();
        report.append("📊 Taille du document : ").append(fullText.length()).append(" caractères\n");
        report.append("📦 Nombre total de chunks : ").append(RagConfig.getAllDocuments().size()).append("\n\n");
        
        // Test 2 : Recherche vectorielle
        try {
            List<Document> vectorResults = vectorStore.similaritySearch(
                SearchRequest.query(query).withTopK(5).withSimilarityThreshold(0.2)
            );
            report.append("🎯 Recherche vectorielle : ").append(vectorResults.size()).append(" résultats\n");
            for (int i = 0; i < Math.min(3, vectorResults.size()); i++) {
                String preview = vectorResults.get(i).getContent()
                    .substring(0, Math.min(100, vectorResults.get(i).getContent().length()));
                report.append("   - Résultat ").append(i + 1).append(" : ").append(preview).append("...\n");
            }
        } catch (Exception e) {
            report.append("❌ Recherche vectorielle échouée : ").append(e.getMessage()).append("\n");
        }
        report.append("\n");
        
        // Test 3 : Recherche par mots-clés
        List<Document> keywordResults = RagConfig.keywordSearch(query, 5);
        report.append("📝 Recherche par mots-clés : ").append(keywordResults.size()).append(" résultats\n");
        for (int i = 0; i < Math.min(3, keywordResults.size()); i++) {
            String preview = keywordResults.get(i).getContent()
                .substring(0, Math.min(100, keywordResults.get(i).getContent().length()));
            report.append("   - Résultat ").append(i + 1).append(" : ").append(preview).append("...\n");
        }
        report.append("\n");
        
        // Test 4 : Contexte final
        String context = getRelevantContext(query);
        report.append("📄 Contexte final : ").append(context.length()).append(" caractères\n");
        report.append("Aperçu : ").append(context.substring(0, Math.min(200, context.length()))).append("...\n");
        
        return report.toString();
    }
}