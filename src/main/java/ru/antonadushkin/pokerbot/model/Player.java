package ru.antonadushkin.pokerbot.model;

public class Player {

    private Long id;
    private String username;
    private int money;

    public Player(Long id, String username, int money) {
        this.id = id;
        this.username = username;
        this.money = money;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public int getMoney() {
        return money;
    }

}
