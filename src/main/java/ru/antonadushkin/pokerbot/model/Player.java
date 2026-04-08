package ru.antonadushkin.pokerbot.model;

public class Player {

    private Long id;
    private String username;
    private int money;
    private Integer finalMoney;

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

    public Integer getFinalMoney() {
        return finalMoney;
    }

    public void setFinalMoney(Integer finalMoney) {
        this.finalMoney = finalMoney;
    }

    public boolean hasFinalMoney() {
        return finalMoney != null;
    }

    public void addMoney(int amount) {
        this.money += amount;
    }

}
