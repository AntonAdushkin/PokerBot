package ru.antonadushkin.pokerbot.model;

import java.util.ArrayList;
import java.util.List;

public class Game {

    private Long chatId;
    private List<Player> players = new ArrayList<>();
    private Long ownerId;
    private boolean started;
    private boolean registrationOpen;
    private boolean finishing;
    private boolean completed;

    public Game(Long chatId, Long ownerId) {
        this.chatId = chatId;
        this.ownerId = ownerId;
        this.started = false;
        this.registrationOpen = true;
        this.finishing = false;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isRegistrationOpen() {
        return registrationOpen;
    }

    public void openRegistration() {
        this.registrationOpen = true;
    }

    public void closeRegistration() {
        this.registrationOpen = false;
    }

    public void start() {
        this.started = true;
    }

    public boolean isFinishing() {
        return finishing;
    }

    public void startFinishing() {
        this.finishing = true;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void complete() {
        this.completed = true;
        this.finishing = false;
    }

    public boolean hasPlayer(Long userId) {
        return findPlayer(userId) != null;
    }

    public Player findPlayer(Long userId) {
        for (Player p : players) {
            if (p.getId().equals(userId)) {
                return p;
            }
        }
        return null;
    }

    public boolean allPlayersSubmittedFinalMoney() {
        if (players.isEmpty()) {
            return false;
        }

        for (Player player : players) {
            if (!player.hasFinalMoney()) {
                return false;
            }
        }

        return true;
    }

    public List<Player> getPlayersWithoutFinalMoney() {
        List<Player> result = new ArrayList<>();

        for (Player player : players) {
            if (!player.hasFinalMoney()) {
                result.add(player);
            }
        }

        return result;
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public int getTotalBank() {
        int sum = 0;
        for (Player p : players) {
            sum += p.getMoney();
        }
        return sum;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public Long getChatId() {
        return chatId;
    }

}
