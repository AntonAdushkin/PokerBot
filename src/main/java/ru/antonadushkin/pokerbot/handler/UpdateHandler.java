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

        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();

            switch (data) {
                case "JOIN_GAME":
                    gameService.joinGameByButton(update, sender);
                    break;

                case "START_GAME":
                    gameService.startGameByButton(update, sender);
                    break;

                case "OPEN_REG":
                    gameService.openRegistration(update, sender);
                    break;

                case "CLOSE_REG":
                    gameService.closeRegistration(update, sender);
                    break;

                case "REBUY":
                    gameService.rebuyByButton(update, sender);
                    break;

                case "END_GAME":
                    gameService.endGameByButton(update, sender);
                    break;

                case "ENTER_FINAL_MONEY":
                    gameService.enterFinalMoneyByButton(update, sender);
                    break;

                case "NEW_GAME":
                    gameService.newGame(update, sender);
                    break;

            }

            return;
        }

        if (update.hasMessage() && update.getMessage().hasText()) {

            Long chatId = update.getMessage().getChatId();
            Long userId = update.getMessage().getFrom().getId();
            String text = update.getMessage().getText();

            gameService.handleMoneyInput(update, sender);

            if ("/startgame".equals(text)) {
                gameService.startGame(chatId, userId, sender);
                return;
            }
        }

    }

}
