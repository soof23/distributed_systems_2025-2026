package com.example.gameapp;
import java.io.Serializable;

/*
Request for Rate
*/

public class RateRequest implements Serializable {
    private String playerId;
    private String gameName;
    private int rating;
    private static final long serialVersionUID = 1L;

    public RateRequest(String playerId, String gameName, int rating){
        this.playerId = playerId;
        this.gameName = gameName;
        this.rating = rating;
    }

    public String getPlayerId(){
        return playerId;
    }

    public String getGameName(){
        return gameName;
    }

    public int getRating(){
        return rating;
    }  
}