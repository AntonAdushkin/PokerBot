package ru.antonadushkin.pokerbot.model;

import java.util.ArrayList;
import java.util.List;

public class Game {

    private Long chatId;
    private List<Player> players = new ArrayList<>();
    private Long ownerId;
    private boolean started;
    private boolean registrationOpen;

    public Game(Long chatId, Long ownerId) {
        this.chatId = chatId;
        this.ownerId = ownerId;
        this.started = false;
        this.registrationOpen = true;
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

    public boolean hasPlayer(Long userId) {
        for (Player p : players) {
            if (p.getId().equals(userId)) {
                return true;
            }
        }
        return false;
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
