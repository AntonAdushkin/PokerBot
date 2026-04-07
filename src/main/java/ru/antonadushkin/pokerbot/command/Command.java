package ru.antonadushkin.pokerbot.command;

public enum Command {

    START_GAME("/startgame"),
    JOIN("/join");

    private final String value;

    Command(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
