package ru.antonadushkin.pokerbot.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage;
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.UnpinChatMessage;
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

    // новая игра
    public void newGame(Update update, AbsSender sender) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        String username = getUsername(update.getCallbackQuery().getFrom());

        Game oldGame = games.get(chatId);

        if (oldGame == null) {
            sendMessage(sender, chatId, "❌ Предыдущая игра не найдена.");
            return;
        }

        if (!oldGame.isCompleted()) {
            sendMessage(sender, chatId, "⛔ Нельзя начать новую игру, пока предыдущая не завершена.");
            return;
        }

        Game newGame = new Game(chatId, userId);
        games.put(chatId, newGame);

        sendMessage(sender, chatId, "🎮 " + username + " начал новую игру!");
        sendAndPinNewGameMessage(sender, newGame);

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
            if (amount < 0) {
                throw new NumberFormatException();
            }
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
            if (amount <= 0) {
                sendMessage(sender, chatId, "❌ " + username + ", сумма входа должна быть больше 0.");
                return;
            }

            Player player = new Player(userId, username, amount);
            game.addPlayer(player);

            sendMessage(sender, chatId,
                    "✅ " + username + " вошёл в игру и дэпнул " + amount + "₽.");

            pendingMoneyActions.remove(userId);
            updateGameMessage(sender, game);
            return;
        }

        if (pendingMoneyAction.type() == MoneyActionType.REBUY) {
            if (amount <= 0) {
                sendMessage(sender, chatId, "❌ " + username + ", сумма додэпа должна быть больше 0.");
                return;
            }

            Player player = game.findPlayer(userId);

            if (player == null) {
                pendingMoneyActions.remove(userId);
                sendMessage(sender, chatId, "❌ Игрок не найден.");
                return;
            }

            if (player.isLeftGame()) {
                pendingMoneyActions.remove(userId);
                sendMessage(sender, chatId, "⛔ " + username + ", ты уже вышел из игры и не можешь додэпать.");
                return;
            }

            player.addMoney(amount);

            sendMessage(sender, chatId,
                    "✅ " + username + " додэпнул " + amount + "₽.");

            pendingMoneyActions.remove(userId);
            updateGameMessage(sender, game);
            return;
        }

        if (pendingMoneyAction.type() == MoneyActionType.LEAVE_FINAL_MONEY) {
            Player player = game.findPlayer(userId);

            if (player == null) {
                pendingMoneyActions.remove(userId);
                sendMessage(sender, chatId, "❌ Игрок не найден.");
                return;
            }

            player.setFinalMoney(amount);
            player.leaveGame();

            sendMessage(sender, chatId,
                    "🚪 " + username + " вышел из игры с остатком " + amount + "₽.");

            pendingMoneyActions.remove(userId);
            updateGameMessage(sender, game);
            return;
        }

        if (pendingMoneyAction.type() == MoneyActionType.FINAL_MONEY) {
            Player player = game.findPlayer(userId);

            if (player == null) {
                pendingMoneyActions.remove(userId);
                sendMessage(sender, chatId, "❌ Игрок не найден.");
                return;
            }

            player.setFinalMoney(amount);

            sendMessage(sender, chatId,
                    "✅ " + username + " указал остаток: " + amount + "₽.");

            pendingMoneyActions.remove(userId);

            if (game.allPlayersSubmittedFinalMoney()) {
                SettlementResult settlementResult = buildSettlementResult(game);

                if (settlementResult.success()) {
                    game.complete();

                    sendMessage(sender, chatId, settlementResult.message());
                    updateGameMessage(sender, game);
                    unpinGameMessage(sender, chatId);
                } else {

                    game.resetFinishing();

                    sendMessage(sender, chatId, settlementResult.message());
                    sendMessage(sender, chatId,
                            "🔁 Остатки введены некорректно. Пожалуйста, все активные участники снова нажмите «💰 Ввести остаток» и введите суммы заново.");

                    updateGameMessage(sender, game);
                }
            } else {
                sendMessage(sender, chatId, buildWaitingForResultsMessage(game));
                updateGameMessage(sender, game);
            }

            return;

        }
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
            sendMessage(sender, chatId, "⛔ Только создатель игры может начать игру.");
            return;
        }

        if (game.isStarted()) {
            sendMessage(sender, chatId, "⚠️ Игра уже началась.");
            return;
        }

        if (game.getPlayers().size() < 2) {
            sendMessage(sender, chatId, "⛔ Для начала игры нужно минимум 2 игрока.");
            return;
        }

        game.start();
        game.closeRegistration();

        sendMessage(sender, chatId, "🚀 Игра началась! Регистрация автоматически закрыта.");

        updateGameMessage(sender, game);

    }

    // новый набор кнопок
    private InlineKeyboardMarkup buildKeyboard(Game game) {

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        if (game.isCompleted()) {

            keyboard.add(List.of(
                    createButton("🎮 Начать новую игру", "NEW_GAME")
            ));

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(keyboard);

            return markup;
        }

        if (game.isRegistrationOpen()) {
            keyboard.add(List.of(
                    createButton("✅ Присоединиться", "JOIN_GAME")
            ));
        }

        if (!game.isStarted()) {
            if (game.getPlayers().size() >= 2) {
                keyboard.add(List.of(
                        createButton("🚀 Начать игру", "START_GAME")
                ));
            }

        } else if (!game.isFinishing()) {

            if (!game.isRegistrationOpen()) {
                keyboard.add(List.of(
                        createButton("💸 Додэп", "REBUY")
                ));

                keyboard.add(List.of(
                        createButton("🚪 Выйти из игры", "LEAVE_GAME")
                ));

                keyboard.add(List.of(
                        createButton("🏁 Закончить игру", "END_GAME")
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

        } else {
            keyboard.add(List.of(
                    createButton("💰 Ввести остаток", "ENTER_FINAL_MONEY")
            ));
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

        if (game == null) {
            sendMessage(sender, chatId, "❌ Игра не найдена.");
            return;
        }

        if (!game.isStarted()) {
            sendMessage(sender, chatId, "⛔ Регистрацией можно управлять только после начала игры.");
            return;
        }

        if (game.isFinishing()) {
            sendMessage(sender, chatId, "⛔ Нельзя менять регистрацию во время завершения игры.");
            return;
        }

        if (game.isCompleted()) {
            sendMessage(sender, chatId, "⛔ Игра уже завершена.");
            return;
        }

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

        if (game == null) {
            sendMessage(sender, chatId, "❌ Игра не найдена.");
            return;
        }

        if (!game.isStarted()) {
            sendMessage(sender, chatId, "⛔ Регистрацией можно управлять только после начала игры.");
            return;
        }

        if (game.isFinishing()) {
            sendMessage(sender, chatId, "⛔ Нельзя менять регистрацию во время завершения игры.");
            return;
        }

        if (game.isCompleted()) {
            sendMessage(sender, chatId, "⛔ Игра уже завершена.");
            return;
        }

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

    // конец игры
    public void endGameByButton(Update update, AbsSender sender) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();

        Game game = games.get(chatId);

        if (game == null) {
            sendMessage(sender, chatId, "❌ Игра не найдена.");
            return;
        }

        if (!game.getOwnerId().equals(userId)) {
            sendMessage(sender, chatId, "⛔ Только создатель игры может завершить игру.");
            return;
        }

        if (!game.isStarted()) {
            sendMessage(sender, chatId, "⛔ Игра ещё не началась.");
            return;
        }

        if (game.isRegistrationOpen()) {
            sendMessage(sender, chatId, "⛔ Сначала закрой регистрацию.");
            return;
        }

        if (game.isFinishing()) {
            sendMessage(sender, chatId, "⚠️ Игра уже находится в режиме завершения.");
            return;
        }

        game.startFinishing();

        sendMessage(sender, chatId,
                "🏁 Игра завершена!\n\n" +
                        "Теперь каждый участник должен нажать кнопку «💰 Ввести остаток» " +
                        "и отправить сумму, которая у него осталась в конце игры.");

        updateGameMessage(sender, game);

    }

    public void enterFinalMoneyByButton(Update update, AbsSender sender) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        String username = getUsername(update.getCallbackQuery().getFrom());

        Game game = games.get(chatId);

        if (game == null) {
            sendMessage(sender, chatId, "❌ Игра не найдена.");
            return;
        }

        if (!game.isFinishing()) {
            sendMessage(sender, chatId, "⛔ Сейчас игра не находится в режиме завершения.");
            return;
        }

        Player player = game.findPlayer(userId);

        if (player == null) {
            sendMessage(sender, chatId, "⛔ " + username + ", только участник игры может ввести остаток.");
            return;
        }

        if (player.isLeftGame()) {
            sendMessage(sender, chatId,
                    "⚠️ " + username + ", ты уже вышел из игры ранее. Твой остаток уже сохранён.");
            return;
        }

        pendingMoneyActions.put(userId, new PendingMoneyAction(chatId, MoneyActionType.FINAL_MONEY));

        sendMessage(sender, chatId,
                "💰 " + username + ", введи сумму, которая у тебя осталась в конце игры:");

    }

    public void leaveGameByButton(Update update, AbsSender sender) {

        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        String username = getUsername(update.getCallbackQuery().getFrom());

        Game game = games.get(chatId);

        if (game == null) {
            sendMessage(sender, chatId, "❌ Игра не найдена.");
            return;
        }

        if (!game.isStarted()) {
            sendMessage(sender, chatId, "⛔ Выйти из игры можно только после её начала.");
            return;
        }

        if (game.isFinishing()) {
            sendMessage(sender, chatId, "⛔ Сейчас уже идёт завершение игры, используй ввод итогового остатка.");
            return;
        }

        if (game.isCompleted()) {
            sendMessage(sender, chatId, "⛔ Игра уже завершена.");
            return;
        }

        Player player = game.findPlayer(userId);

        if (player == null) {
            sendMessage(sender, chatId, "⛔ " + username + ", ты не участвуешь в этой игре.");
            return;
        }

        if (player.isLeftGame()) {
            sendMessage(sender, chatId, "⚠️ " + username + ", ты уже вышел из игры.");
            return;
        }

        pendingMoneyActions.put(userId, new PendingMoneyAction(chatId, MoneyActionType.LEAVE_FINAL_MONEY));

        sendMessage(sender, chatId,
                "🚪 " + username + ", введи сумму, которая у тебя осталась на момент выхода из игры:");
    }

    private SettlementResult buildSettlementResult(Game game) {

        int totalInvested = 0;
        int totalFinal = 0;

        for (Player player : game.getPlayers()) {
            totalInvested += player.getMoney();
            totalFinal += player.getFinalMoney();
        }

        if (totalInvested != totalFinal) {
            return new SettlementResult(
                    false,
                    "❌ Невозможно посчитать переводы.\n\n" +
                            "Сумма внесённых денег: " + totalInvested + "₽\n" +
                            "Сумма итоговых остатков: " + totalFinal + "₽\n\n" +
                            "Проверьте введённые остатки и введите их заново."
            );
        }

        List<SettlementSide> creditors = new ArrayList<>();
        List<SettlementSide> debtors = new ArrayList<>();

        for (Player player : game.getPlayers()) {
            int net = player.getFinalMoney() - player.getMoney();

            if (net > 0) {
                creditors.add(new SettlementSide(player.getUsername(), net));
            } else if (net < 0) {
                debtors.add(new SettlementSide(player.getUsername(), -net));
            }
        }

        if (creditors.isEmpty() && debtors.isEmpty()) {
            return new SettlementResult(
                    true,
                    "🏁 Игра завершена!\n\n" +
                            "Никто никому ничего не должен.\n\n" +
                            "✅ Игра окончена. Можно начинать новую."
            );
        }

        List<String> transfers = new ArrayList<>();

        int debtorIndex = 0;
        int creditorIndex = 0;

        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
            SettlementSide debtor = debtors.get(debtorIndex);
            SettlementSide creditor = creditors.get(creditorIndex);

            int transferAmount = Math.min(debtor.amount(), creditor.amount());

            transfers.add("💸 " + debtor.username() + " → " + creditor.username() + ": " + transferAmount + "₽");

            debtor = debtor.decrease(transferAmount);
            creditor = creditor.decrease(transferAmount);

            if (debtor.amount() == 0) {
                debtorIndex++;
            } else {
                debtors.set(debtorIndex, debtor);
            }

            if (creditor.amount() == 0) {
                creditorIndex++;
            } else {
                creditors.set(creditorIndex, creditor);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🏁 Игра завершена!\n\n");
        sb.append("Итоговые переводы:\n\n");

        for (String transfer : transfers) {
            sb.append(transfer).append("\n");
        }

        sb.append("\nПереведите деньги согласно списку выше.");
        sb.append("\n✅ После этого игра считается завершённой.");

        return new SettlementResult(true, sb.toString());

    }

    private String buildWaitingForResultsMessage(Game game) {

        List<Player> playersWithoutFinalMoney = game.getPlayersWithoutFinalMoney();

        if (playersWithoutFinalMoney.isEmpty()) {
            return "✅ Все участники уже ввели остатки.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("⏳ Ожидаем остатки от:\n");

        for (Player player : playersWithoutFinalMoney) {
            sb.append("• ").append(player.getUsername()).append("\n");
        }

        return sb.toString();

    }

    // новая кнопка
    private InlineKeyboardButton createButton(String text, String data) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(data);
        return button;
    }

    private void sendAndPinNewGameMessage(AbsSender sender, Game game) {

        Long chatId = game.getChatId();

        try {

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId.toString());
            sendMessage.setText(buildGameMessage(game));
            sendMessage.setReplyMarkup(buildKeyboard(game));

            Message message = sender.execute(sendMessage);

            Integer newMessageId = message.getMessageId();

            gameMessages.put(chatId, newMessageId);

            PinChatMessage pinChatMessage = new PinChatMessage();
            pinChatMessage.setChatId(chatId.toString());
            pinChatMessage.setMessageId(newMessageId);
            pinChatMessage.setDisableNotification(true);

            sender.execute(pinChatMessage);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // текст сообщения
    private String buildGameMessage(Game game) {
        StringBuilder sb = new StringBuilder();
        sb.append("♠️♥️ ПОКЕР ♣️♦️\n\n");

        if (game.isCompleted()) {
            sb.append("🏁 Игра окончена!\n\n");
        } else if (game.isFinishing()) {
            sb.append("⏳ Идёт ввод итоговых остатков.\n\n");
        } else if (game.isStarted()) {
            sb.append("🃏️ Игра идёт.\n\n");
        } else {
            sb.append("📝 Идёт регистрация.\n\n");
        }

        sb.append("Игроки:\n");

        if (game.getPlayers().isEmpty()) {
            sb.append("— пока никто не присоединился.\n");
        } else {
            for (Player player : game.getPlayers()) {
                sb.append("• ")
                        .append(player.getUsername())
                        .append(" — внесено: ")
                        .append(player.getMoney())
                        .append("₽");

                if (player.isLeftGame()) {
                    sb.append(" (вышел из игры)");
                }

                if (game.isFinishing() || game.isCompleted() || player.isLeftGame()) {
                    sb.append(", остаток: ");
                    if (player.getFinalMoney() != null) {
                        sb.append(player.getFinalMoney()).append("₽");
                    } else {
                        sb.append("—");
                    }
                }

                sb.append("\n");
            }
        }

        sb.append("\nОбщий банк: ").append(game.getTotalBank()).append("₽.");

        if (game.isCompleted()) {
            sb.append("\n\n✅ Игра завершена. Главное сообщение больше не активно.");
        } else if (game.isFinishing()) {
            sb.append("\n\nℹ️ Игроки, которые вышли из игры досрочно, уже имеют сохранённый остаток.");
        }

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

    private void unpinGameMessage(AbsSender sender, Long chatId) {
        Integer messageId = gameMessages.get(chatId);

        if (messageId == null) {
            return;
        }

        try {
            UnpinChatMessage unpinChatMessage = new UnpinChatMessage();
            unpinChatMessage.setChatId(chatId.toString());
            unpinChatMessage.setMessageId(messageId);

            sender.execute(unpinChatMessage);
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
        REBUY,
        FINAL_MONEY,
        LEAVE_FINAL_MONEY
    }

    private record PendingMoneyAction(Long chatId, MoneyActionType type) {
    }

    private record SettlementResult(boolean success, String message) {
    }

    private record SettlementSide(String username, int amount) {
        private SettlementSide decrease(int value) {
            return new SettlementSide(username, amount - value);
        }
    }

}
