package com.example.gameapp;

import java.util.List;

public class DataHolder {
    private static DataHolder instance;
    private List<Game> gamesList;

    private DataHolder() {}

    public static synchronized DataHolder getInstance() {
        if (instance == null) {
            instance = new DataHolder();
        }
        return instance;
    }

    public List<Game> getGamesList() {
        return gamesList;
    }

    public void setGamesList(List<Game> gamesList) {
        this.gamesList = gamesList;
    }
}
