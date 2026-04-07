package ru.antonadushkin.pokerbot.bot;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.antonadushkin.pokerbot.config.BotConfig;
import ru.antonadushkin.pokerbot.handler.UpdateHandler;

@Component
public class PokerBot extends TelegramLongPollingBot {

    private final BotConfig config;
    private final UpdateHandler updateHandler;

    public PokerBot(BotConfig config, UpdateHandler updateHandler) {
        this.config = config;
        this.updateHandler = updateHandler;
    }

    @Override
    public String getBotUsername() {
        return config.getUsername();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        updateHandler.handle(update, this);
    }

}
