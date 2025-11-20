package com.rayen.miniprojet.controllers;

import com.rayen.miniprojet.agents.AIAgent;
import com.rayen.miniprojet.services.CsvService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class AIAgentController {

    private final AIAgent agent;
    private final CsvService csvService; // Injection du nouveau service

    // Ton ancien chat (Database)
    @GetMapping("/chat")
    public String chat(@RequestParam String query) {
        return agent.chat(query);
    }

    // 🆕 Nouveau endpoint pour CSV
    @PostMapping("/chat/csv")
    public String analyzeCsv(@RequestParam("file") MultipartFile file, 
                             @RequestParam("query") String query) {
        
        // 1. Convertir le fichier en texte
        String csvData = csvService.analyzeCsvContent(file);

        // 2. Construire un prompt complet pour l'IA
        String fullPrompt = """
                CONTEXTE : L'utilisateur a uploadé un fichier CSV.
                %s
                
                QUESTION UTILISATEUR : %s
                
                Réponds en analysant les données ci-dessus.
                """.formatted(csvData, query);

        // 3. Envoyer à l'agent
        return agent.chat(fullPrompt);
    }
}