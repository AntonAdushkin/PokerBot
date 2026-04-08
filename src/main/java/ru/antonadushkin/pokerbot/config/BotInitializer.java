package ru.antonadushkin.pokerbot.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.antonadushkin.pokerbot.bot.PokerBot;

@Component
public class BotInitializer {

    private final PokerBot pokerBot;

    public BotInitializer(PokerBot pokerBot) {
        this.pokerBot = pokerBot;
    }

    @PostConstruct
    public void init() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(pokerBot);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}