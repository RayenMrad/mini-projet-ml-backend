package com.rayen.miniprojet.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import com.rayen.miniprojet.tools.StockTools;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AIAgent {

    private final ChatClient.Builder builder;
    private final StockTools stockTools;
    private ChatClient chatClient;

    @PostConstruct
    public void init() {
        this.chatClient = builder
                .defaultSystem("""
                    Tu es un assistant logistique expert.
                    1. Utilise tes outils pour vérifier le stock.
                    2. Si le statut est "ALERTE RUPTURE", conseille de commander.
                    3. Sois concis et précis.
                    """)
                .defaultFunctions("getProductInfo", "getLowStockProducts")
                .build();
    }

    public String chat(String userQuery) {
        return chatClient.prompt()
                .user(userQuery)
                .call()
                .content();
    }
}