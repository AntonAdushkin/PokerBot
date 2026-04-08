package ru.antonadushkin.pokerbot.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ru.antonadushkin.pokerbot.model.Game;
import ru.antonadushkin.pokerbot.model.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GameService {

    private final Map<Long, Game> games = new HashMap<>();
    private final Map<Long, PendingMoneyAction> pendingMoneyActions = new HashMap<>();
    private final Map<Long, Integer> gameMessages = new HashMap<>();

    // старт игры
    public void startGame(Long chatId, Long ownerId, AbsSender sender) {

        if (games.containsKey(chatId)) {
            sendMessage(sender, chatId, "⚠️ Игра уже запущена.");
            return;
        }

        Game game = new Game(chatId, ownerId);
        games.put(chatId, game);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(buildGameMessage(game));
        message.setReplyMarkup(buildKeyboard(game));

        try {

            Message sentMessage = sender.execute(message);
            gameMessages.put(chatId, sentMessage.getMessageId());

            PinChatMessage pin = new PinChatMessage();
            pin.setChatId(chatId.toString());
            pin.setMessageId(sentMessage.getMessageId());
            pin.setDisableNotification(true); // без уведомления

            sender.execute(pin);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // присоединиться к игре по кнопке
    public void joinGameByButton(Update update, AbsSender sender) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();

        Game game = games.get(chatId);

        String username = getUsername(update.getCallbackQuery().getFrom());

        if (game == null) {
            sendMessage(sender, chatId, "❌ Игра не найдена.");
            return;
        }

        if (!game.isRegistrationOpen()) {
            sendMessage(sender, chatId, "⛔ Регистрация сейчас закрыта.");
            return;
        }

        if (game.hasPlayer(userId)) {
            sendMessage(sender, chatId, "⚠️ " + username + ", ты уже в игре!");
            return;
        }

        pendingMoneyActions.put(userId, new PendingMoneyAction(chatId, MoneyActionType.JOIN));

        sendMessage(sender, chatId,
                "💰 " + username + ", введи сумму входа (в рублях):");

    }

    // ввод суммы
    public void handleMoneyInput(Update update, AbsSender sender) {

        Long userId = update.getMessage().getFrom().getId();

        if (!pendingMoneyActions.containsKey(userId)) {
            return;
        }

        PendingMoneyAction pendingMoneyAction = pendingMoneyActions.get(userId);
        Long chatId = pendingMoneyAction.chatId();
        String text = update.getMessage().getText();

        int amount;

        try {
            amount = Integer.parseInt(text);
            if (amount <= 0) throw new NumberFormatException();
        } catch (Exception e) {
            String username = getUsername(update.getMessage().getFrom());
            sendMessage(sender, chatId, "❌ " + username + ", введи корректное число!");
            return;
        }

        Game game = games.get(chatId);
        String username = getUsername(update.getMessage().getFrom());

        if (game == null) {
            pendingMoneyActions.remove(userId);
            sendMessage(sender, chatId, "❌ Игра не найдена.");
            return;
        }

        if (pendingMoneyAction.type() == MoneyActionType.JOIN) {
            Player player = new Player(userId, username, amount);
            game.addPlayer(player);
        } else {
            Player player = game.findPlayer(userId);

            if (player == null) {
                pendingMoneyActions.remove(userId);
                sendMessage(sender, chatId, "❌ Игрок не найден.");
                return;
            }

            // >>> ДОБАВЛЕНО: увеличение стека игрока
            player.addMoney(amount);

            sendMessage(sender, chatId,
                    "✅ " + username + " додэпнул " + amount + "₽.");
        }

        pendingMoneyActions.remove(userId);

        updateGameMessage(sender, game);

    }

    // кнопка начала игры и конца регистрации
    public void startGameByButton(Update update, AbsSender sender) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();

        Game game = games.get(chatId);

        if (game == null) {
            sendMessage(sender, chatId, "❌ Игра не найдена.");
            return;
        }

        if (!game.getOwnerId().equals(userId)) {
            sendMessage(sender, chatId, "⛔ Только создатель может начать игру.");
            return;
        }

        game.start();
        game.closeRegistration();

        updateGameMessage(sender, game);

    }

    // новый набор кнопок
    private InlineKeyboardMarkup buildKeyboard(Game game) {

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        if (game.isRegistrationOpen()) {
            keyboard.add(List.of(
                    createButton("✅ Присоединиться", "JOIN_GAME")
            ));
        }

        if (!game.isStarted()) {
            keyboard.add(List.of(
                    createButton("🚀 Начать игру", "START_GAME")
            ));
        } else {
            // >>> ДОБАВЛЕНО: кнопка докупки после старта игры и при закрытой регистрации
            if (!game.isRegistrationOpen()) {
                keyboard.add(List.of(
                        createButton("💸 Додэп", "REBUY")
                ));
            }

            if (game.isRegistrationOpen()) {
                keyboard.add(List.of(
                        createButton("🔒 Закрыть регистрацию", "CLOSE_REG")
                ));
            } else {
                keyboard.add(List.of(
                        createButton("🔓 Открыть регистрацию", "OPEN_REG")
                ));
            }
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);

        return markup;
    }

    // открыть регистрацию
    public void openRegistration(Update update, AbsSender sender) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();

        Game game = games.get(chatId);

        if (!game.getOwnerId().equals(userId)) {
            sendMessage(sender, chatId, "⛔ Только владелец может открыть регистрацию");
            return;
        }

        game.openRegistration();

        updateGameMessage(sender, game);

    }

    // закрыть регистрацию
    public void closeRegistration(Update update, AbsSender sender) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();

        Game game = games.get(chatId);

        if (!game.getOwnerId().equals(userId)) {
            sendMessage(sender, chatId, "⛔ Только владелец может закрыть регистрацию.");
            return;
        }

        game.closeRegistration();

        updateGameMessage(sender, game);
    }

    // додэп
    public void rebuyByButton(Update update, AbsSender sender) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        String username = getUsername(update.getCallbackQuery().getFrom());

        Game game = games.get(chatId);

        if (game == null) {
            sendMessage(sender, chatId, "❌ Игра не найдена.");
            return;
        }

        if (!game.isStarted()) {
            sendMessage(sender, chatId, "⛔ Додэп возможен только после начала игры.");
            return;
        }

        if (game.isRegistrationOpen()) {
            sendMessage(sender, chatId, "⛔ Додэп возможен только когда регистрация закрыта.");
            return;
        }

        if (!game.hasPlayer(userId)) {
            sendMessage(sender, chatId, "⛔ " + username + ", додэпнуть может только участник игры.");
            return;
        }

        pendingMoneyActions.put(userId, new PendingMoneyAction(chatId, MoneyActionType.REBUY));

        sendMessage(sender, chatId,
                "💸 " + username + ", введи сумму додэпа (в рублях):");

    }

    // новая кнопка
    private InlineKeyboardButton createButton(String text, String data) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(data);
        return button;
    }

    // текст сообщения
    private String buildGameMessage(Game game) {

        StringBuilder sb = new StringBuilder();
        sb.append("♠️♥️ ПОКЕР ♣️♦️\n\n");

        if (game.isStarted()) {
            sb.append("🟢 Игра началась!\n");

            sb.append(game.isRegistrationOpen()
                    ? "🔓 Регистрация открыта.\n\n"
                    : "🔒 Регистрация закрыта.\n\n");

        } else {
            sb.append("🟡 Ожидание начала игры.\n\n");
        }

        if (game.getPlayers().isEmpty()) {
            sb.append("Пока нет игроков.\n");
        } else {
            sb.append("Список участников:\n");
            int i = 1;
            for (Player p : game.getPlayers()) {
                sb.append(i++)
                        .append(". ")
                        .append(p.getUsername())
                        .append(" — ")
                        .append(p.getMoney())
                        .append("₽.\n");
            }
        }

        sb.append("\n💰 Общий банк: ")
                .append(game.getTotalBank())
                .append("₽.");

        return sb.toString();

    }

    private void updateGameMessage(AbsSender sender, Game game) {

        Long chatId = game.getChatId();
        Integer messageId = gameMessages.get(chatId);

        EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId.toString());
        edit.setMessageId(messageId);
        edit.setText(buildGameMessage(game));
        edit.setReplyMarkup(buildKeyboard(game));

        try {
            sender.execute(edit);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private String getUsername(User user) {
        if (user.getUserName() != null) {
            return user.getUserName();
        }
        return user.getFirstName();
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

    private enum MoneyActionType {
        JOIN,
        REBUY
    }

    private record PendingMoneyAction(Long chatId, MoneyActionType type) {
    }

}
