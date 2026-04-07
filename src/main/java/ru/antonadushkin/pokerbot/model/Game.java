package ru.antonadushkin.pokerbot.model;

import java.util.ArrayList;
import java.util.List;

public class Game {

    private Long chatId;
    private List<Player> players = new ArrayList<>();

    public Game(Long chatId) {
        this.chatId = chatId;
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
