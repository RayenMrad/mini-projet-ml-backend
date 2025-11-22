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
    
    private static final int SMALL_DOCUMENT_THRESHOLD = 10000;

    @PostConstruct
    public void init() {
        log.info("🤖 Initialisation de l'AIAgent UNIVERSEL avec RAG Hybride");
        
        String fullText = RagConfig.getFullDocumentText();
        boolean isSmallDocument = fullText.length() < SMALL_DOCUMENT_THRESHOLD;
        
        if (isSmallDocument) {
            log.info("📄 Document petit ({} caractères) - Mode contexte complet activé", fullText.length());
            
            this.chatClient = builder
                .defaultSystem(buildIntelligentSystemPrompt(fullText))
                .defaultFunctions("getProductInfo", "getLowStockProducts")
                .build();
        } else {
            log.info("📚 Document volumineux ({} caractères) - Mode RAG hybride activé", fullText.length());
            
            this.chatClient = builder
                .defaultSystem(buildIntelligentSystemPrompt(null))
                .defaultFunctions("getProductInfo", "getLowStockProducts")
                .build();
        }
        
        log.info("✅ AIAgent UNIVERSEL initialisé avec succès");
    }

    /**
     * Construit un system prompt intelligent qui gère PDF + CSV + BDD
     */
    private String buildIntelligentSystemPrompt(String fullDocumentText) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("""
            Tu es un assistant logistique intelligent avec 3 SOURCES D'INFORMATION DISTINCTES :
            
            """);
        
        // Source 1 : Documents PDF
        if (fullDocumentText != null) {
            prompt.append("=== SOURCE 1 : DOCUMENT DE POLITIQUE (PDF) ===\n");
            prompt.append(fullDocumentText);
            prompt.append("\n\n=== FIN DU DOCUMENT ===\n\n");
        } else {
            prompt.append("=== SOURCE 1 : DOCUMENT DE POLITIQUE (PDF via RAG) ===\n");
            prompt.append("Les documents PDF seront fournis dans le contexte de la requête.\n\n");
        }
        
        // Source 2 : Base de données (Tools)
        prompt.append("""
            === SOURCE 2 : BASE DE DONNÉES EN TEMPS RÉEL (via Functions) ===
            Tu as accès à ces fonctions pour interroger la base de données :
            - getProductInfo(sku) : Récupère le stock, prix, statut d'un produit
            - getLowStockProducts() : Liste les produits en rupture de stock
            
            """);
        
        // Source 3 : Données CSV
        prompt.append("""
            === SOURCE 3 : DONNÉES CSV UPLOADÉES ===
            Lorsque l'utilisateur uploade un CSV, les données seront fournies dans le contexte.
            
            """);
        
        // Règles de routage CRITIQUES
        prompt.append("""
            ==========================================
            RÈGLES DE ROUTAGE INTELLIGENTES (IMPORTANT)
            ==========================================
            
            🔍 COMMENT IDENTIFIER LA BONNE SOURCE :
            
            1️⃣ Si la question contient "CONTEXTE : L'utilisateur a uploadé un fichier CSV"
               → C'est une question CSV
               → Analyse UNIQUEMENT les données CSV fournies
               → Ignore complètement le document PDF
               → Format : "D'après les données CSV : [analyse]"
            
            2️⃣ Si la question concerne le STOCK ACTUEL, PRIX, DISPONIBILITÉ
               Mots-clés : "stock actuel", "prix", "coûte", "disponible", "rupture", "quantité en stock"
               → APPELLE les FONCTIONS (getProductInfo ou getLowStockProducts)
               → N'utilise PAS le document PDF
               → N'utilise PAS les données CSV
               → Si produit introuvable : "Produit non trouvé dans la base de données"
            
            3️⃣ Si la question concerne les RÈGLES, POLITIQUES, PROCÉDURES
               Mots-clés : "procédure", "délai", "pénalité", "coefficient", "validation", "section"
               → Utilise le DOCUMENT PDF
               → Format : "D'après le document (Section X.X) : [détails]"
               → Si info absente : "Information non trouvée dans le document"
            
            ==========================================
            EXEMPLES CONCRETS
            ==========================================
            
            ❓ "CONTEXTE : L'utilisateur a uploadé un fichier CSV. [données]... QUESTION : Quelle est la quantité totale dans la région Nord ?"
            ✅ BONNE RÉPONSE : "D'après les données CSV, la quantité totale dans la région Nord est de 57 unités (Montre Rolex: 2 + Écouteurs Bluetooth: 50 + PC Portable Gaming: 5)."
            ❌ MAUVAISE RÉPONSE : "Information non trouvée dans le document"
            
            ❓ "Quel est le stock actuel de PlayStation 5 ?"
            ✅ BONNE RÉPONSE : [Appelle getProductInfo("PS5-SLIM")] → "Le stock actuel de PlayStation 5 est de 2 unités."
            ❌ MAUVAISE RÉPONSE : "Information non trouvée dans le document"
            
            ❓ "Quel est le prix de l'iPhone 15 Pro ?"
            ✅ BONNE RÉPONSE : [Appelle getProductInfo("IPHONE-15")] → "Le prix de l'iPhone 15 Pro est de 50€."
            ❌ MAUVAISE RÉPONSE : "Information non trouvée dans le document"
            
            ❓ "Liste les produits en stock critique"
            ✅ BONNE RÉPONSE : [Appelle getLowStockProducts()] → "Voici les produits en stock critique : PlayStation 5 (2 unités), Dell XPS 13 (0 unités)..."
            ❌ MAUVAISE RÉPONSE : "Information non trouvée dans le document"
            
            ❓ "Quelle est la pénalité pour un retour au 35ème jour ?"
            ✅ BONNE RÉPONSE : "D'après le document (Section 1.1), la pénalité est de 15% des frais de restockage pour un retour entre le 31ème et 45ème jour."
            
            ❓ "Quel est le coefficient de sécurité pour le seuil minimum ?"
            ✅ BONNE RÉPONSE : "D'après le document (Section 2.1), le coefficient de sécurité est de 1,2."
            
            ==========================================
            INTERDICTIONS ABSOLUES
            ==========================================
            
            ❌ Ne dis JAMAIS "Information non trouvée dans le document" pour des questions de STOCK ou CSV
            ❌ Ne cherche JAMAIS dans le PDF pour des questions de stock/prix/disponibilité
            ❌ Ne mentionne JAMAIS le PDF quand tu analyses des données CSV
            ❌ N'invente JAMAIS de données
            
            ==========================================
            RÉSUMÉ DES ACTIONS
            ==========================================
            
            CSV uploadé → Analyse les données CSV fournies
            Stock/Prix/Produit → Appelle les fonctions (Tools)
            Règles/Politiques → Cherche dans le document PDF
            
            Choisis intelligemment la bonne source pour chaque question !
            """);
        
        return prompt.toString();
    }

    public String chat(String userQuery) {
        log.info("💬 Question reçue : {}", userQuery);
        
        try {
            // Détecter si c'est une question CSV, Stock, ou Document
            String queryType = detectQueryType(userQuery);
            log.info("🎯 Type de question détecté : {}", queryType);
            
            String response;
            
            if ("CSV".equals(queryType)) {
                // Question CSV : le contexte est déjà dans userQuery
                log.info("📊 Question CSV détectée - Pas besoin d'ajouter le document PDF");
                response = chatClient.prompt()
                        .user(userQuery)
                        .call()
                        .content();
                        
            } else if ("STOCK".equals(queryType)) {
                // Question Stock : forcer l'appel des fonctions
                log.info("🗄️ Question STOCK détectée - Les fonctions doivent être appelées");
                response = chatClient.prompt()
                        .user(userQuery + "\n\n⚠️ RAPPEL : Cette question concerne la base de données. Utilise les fonctions disponibles.")
                        .call()
                        .content();
                        
            } else {
                // Question Document : ajouter le contexte si nécessaire
                log.info("📄 Question DOCUMENT détectée");
                
                if (RagConfig.getFullDocumentText().length() < SMALL_DOCUMENT_THRESHOLD) {
                    // Document déjà dans le system prompt
                    response = chatClient.prompt()
                            .user(userQuery)
                            .call()
                            .content();
                } else {
                    // Document volumineux, ajouter le contexte
                    String context = getRelevantContext(userQuery);
                    String enrichedQuery = """
                        CONTEXTE DU DOCUMENT :
                        """ + context + """
                        
                        QUESTION :
                        """ + userQuery;
                    
                    response = chatClient.prompt()
                            .user(enrichedQuery)
                            .call()
                            .content();
                }
            }
            
            log.info("✅ Réponse générée ({} caractères)", response.length());
            return response;
            
        } catch (Exception e) {
            log.error("❌ Erreur lors du traitement de la requête", e);
            return "Désolé, une erreur s'est produite : " + e.getMessage();
        }
    }
    
    /**
     * Détecte le type de question pour router correctement
     */
    private String detectQueryType(String query) {
        String lower = query.toLowerCase();
        
        // CSV : détection prioritaire (si le contexte contient "uploadé un fichier CSV")
        if (lower.contains("contexte : l'utilisateur a uploadé un fichier csv") ||
            lower.contains("données csv") ||
            (lower.contains("région") && (lower.contains("nord") || lower.contains("sud") || lower.contains("est") || lower.contains("ouest")))) {
            return "CSV";
        }
        
        // Stock : mots-clés de la base de données
        if (lower.contains("stock actuel") || 
            lower.contains("prix") || 
            lower.contains("coûte") ||
            lower.contains("disponible") ||
            lower.contains("rupture") ||
            lower.contains("quantité en stock") ||
            lower.contains("liste les produits") ||
            lower.contains("playstation") ||
            lower.contains("iphone") ||
            lower.contains("dell") ||
            lower.contains("produit")) {
            return "STOCK";
        }
        
        // Document : par défaut
        return "DOCUMENT";
    }
    
    private String getRelevantContext(String query) {
        log.info("🔍 Recherche de contexte pertinent pour : {}", query);
        
        List<Document> relevantDocs = new ArrayList<>();
        
        try {
            relevantDocs = vectorStore.similaritySearch(
                SearchRequest.query(query)
                    .withTopK(10)
                    .withSimilarityThreshold(0.2)
            );
            log.info("🎯 Recherche vectorielle : {} documents trouvés", relevantDocs.size());
        } catch (Exception e) {
            log.warn("⚠️ Recherche vectorielle échouée : {}", e.getMessage());
        }
        
        if (relevantDocs.isEmpty()) {
            log.info("🔄 Passage à la recherche par mots-clés...");
            relevantDocs = RagConfig.keywordSearch(query, 10);
            log.info("📝 Recherche par mots-clés : {} documents trouvés", relevantDocs.size());
        }
        
        if (relevantDocs.isEmpty()) {
            log.warn("⚠️ Aucun résultat - Utilisation du document complet");
            return RagConfig.getFullDocumentText();
        }
        
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < relevantDocs.size(); i++) {
            Document doc = relevantDocs.get(i);
            context.append("--- Extrait ").append(i + 1).append(" ---\n");
            context.append(doc.getContent()).append("\n\n");
        }
        
        return context.toString();
    }
    
    public String testRag(String query) {
        log.info("🔬 Test du système RAG pour : {}", query);
        
        StringBuilder report = new StringBuilder();
        report.append("=== DIAGNOSTIC RAG ===\n\n");
        
        String fullText = RagConfig.getFullDocumentText();
        report.append("📊 Taille du document : ").append(fullText.length()).append(" caractères\n");
        report.append("📦 Nombre total de chunks : ").append(RagConfig.getAllDocuments().size()).append("\n");
        report.append("🎯 Type de question : ").append(detectQueryType(query)).append("\n\n");
        
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
        
        List<Document> keywordResults = RagConfig.keywordSearch(query, 5);
        report.append("📝 Recherche par mots-clés : ").append(keywordResults.size()).append(" résultats\n");
        for (int i = 0; i < Math.min(3, keywordResults.size()); i++) {
            String preview = keywordResults.get(i).getContent()
                .substring(0, Math.min(100, keywordResults.get(i).getContent().length()));
            report.append("   - Résultat ").append(i + 1).append(" : ").append(preview).append("...\n");
        }
        report.append("\n");
        
        String context = getRelevantContext(query);
        report.append("📄 Contexte final : ").append(context.length()).append(" caractères\n");
        report.append("Aperçu : ").append(context.substring(0, Math.min(200, context.length()))).append("...\n");
        
        return report.toString();
    }
}