package com.example.gameapp;
import java.io.Serializable;

/*
Request for Search
*/

public class SearchRequest implements Serializable {
    private String playerId;
    private String searchId;
    private Integer stars;
    private String betType;
    private String riskLevel;
    private static final long serialVersionUID = 1L;

    public SearchRequest(String playerId, String searchId, Integer stars, String betType, String riskLevel){
        this.playerId = playerId;
        this.searchId = searchId;
        this.stars = stars;
        this.betType = betType;
        this.riskLevel = riskLevel;
    }

    public String getPlayerId(){
        return playerId;
    }
    
    public String getSearchId(){
        return searchId;
    }

    public Integer getStars(){
        return stars;
    }

    public String getBetType(){
        return betType;
    }

    public String getRiskLevel(){
        return riskLevel;
    }
}