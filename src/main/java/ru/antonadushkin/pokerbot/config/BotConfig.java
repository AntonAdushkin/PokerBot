package ru.antonadushkin.pokerbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BotConfig {

    @Value("${telegram.bot.username}")
    private String username;

    @Value("${telegram.bot.token}")
    private String token;

    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }

}
