package ru.antonadushkin.pokerbot.handler;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.antonadushkin.pokerbot.service.GameService;

@Component
public class UpdateHandler {

    private final GameService gameService;

    public UpdateHandler(GameService gameService) {
        this.gameService = gameService;
    }

    public void handle(Update update, AbsSender sender) {

        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            gameService.handleMoneyInput(update, sender);

            if (text.equals("/startgame")) {
                gameService.startGame(chatId, sender);
                return;
            }

        }

        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();

            if (data.equals("JOIN_GAME")) {
                gameService.joinGameByButton(update, sender);
            }

        }

    }

}
