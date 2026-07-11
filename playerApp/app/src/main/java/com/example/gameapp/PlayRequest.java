package com.example.gameapp;
import java.io.Serializable;

/*
Request for Play
*/

public class PlayRequest implements Serializable {
    private String playerId;
    private String gameName;
    private double bet;
    private static final long serialVersionUID = 1L;

    public PlayRequest(String playerId, String gameName, double bet){
        this.playerId = playerId;
        this.gameName = gameName;
        this.bet = bet;
    }

    public String getPlayerId(){
        return playerId;
    }

    public String getGameName(){
        return gameName;
    }

    public double getBet(){
        return bet;
    }
}