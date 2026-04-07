package ru.antonadushkin.pokerbot.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.antonadushkin.pokerbot.model.Game;
import ru.antonadushkin.pokerbot.model.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GameService {

    private final Map<Long, Game> games = new HashMap<>();
    private final Map<Long, Long> pendingPlayers = new HashMap<>();
    private final Map<Long, Integer> gameMessages = new HashMap<>();

    public void startGame(Long chatId, AbsSender sender) {

        Game game = new Game(chatId);
        games.put(chatId, game);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(buildGameMessage(game));

        InlineKeyboardButton joinButton = new InlineKeyboardButton();
        joinButton.setText("✅ Присоединиться");
        joinButton.setCallbackData("JOIN_GAME");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(joinButton)));

        message.setReplyMarkup(markup);

        try {
            Message sentMessage = sender.execute(message);
            gameMessages.put(chatId, sentMessage.getMessageId());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void joinGameByButton(Update update, AbsSender sender) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();

        Game game = games.get(chatId);

        if (game == null) {
            sendMessage(sender, chatId, "❌ Игра не найдена");
            return;
        }

        if (game.hasPlayer(userId)) {
            sendMessage(sender, chatId, "⚠️ Ты уже в игре!");
            return;
        }

        pendingPlayers.put(userId, chatId);

        String username = update.getCallbackQuery().getFrom().getUserName();
        if (username == null) {
            username = update.getCallbackQuery().getFrom().getFirstName();
        }

        sendMessage(sender, chatId,
                "💰 " + username + ", введи сумму входа (в рублях):");

    }

    public void handleMoneyInput(Update update, AbsSender sender) {

        Long userId = update.getMessage().getFrom().getId();

        if (!pendingPlayers.containsKey(userId)) {
            return;
        }

        Long chatId = pendingPlayers.get(userId);
        String text = update.getMessage().getText();

        int amount;

        try {
            amount = Integer.parseInt(text);
            if (amount <= 0) throw new NumberFormatException();
        } catch (Exception e) {
            sendMessage(sender, chatId, "❌ Введи корректное число");
            return;
        }

        Game game = games.get(chatId);
        String username = update.getMessage().getFrom().getUserName();

        Player player = new Player(userId, username, amount);
        game.addPlayer(player);

        pendingPlayers.remove(userId);

        updateGameMessage(sender, game);

    }

    private String buildGameMessage(Game game) {

        StringBuilder sb = new StringBuilder();
        sb.append("🎮 Игра в покер\n\n");

        if (game.getPlayers().isEmpty()) {
            sb.append("Пока нет игроков\n");
        } else {
            int i = 1;
            for (Player p : game.getPlayers()) {
                sb.append(i++)
                        .append(". ")
                        .append(p.getUsername())
                        .append(" — ")
                        .append(p.getMoney())
                        .append("₽\n");
            }
        }

        sb.append("\n💰 Общий банк: ")
                .append(game.getTotalBank())
                .append("₽");

        return sb.toString();

    }

    private void updateGameMessage(AbsSender sender, Game game) {

        Long chatId = game.getChatId();
        Integer messageId = gameMessages.get(chatId);

        EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId.toString());
        edit.setMessageId(messageId);
        edit.setText(buildGameMessage(game));

        try {
            sender.execute(edit);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void sendMessage(AbsSender sender, Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        try {
            sender.execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
