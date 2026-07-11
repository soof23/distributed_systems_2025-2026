package com.example.gameapp;
import java.io.Serializable;

/*
Αποτέλεσμα που επιστρέφει ο Worker στον Master
*/

public class PlayResult implements Serializable {
    private String playerId;
    private String gameName;
    private double betAmount;
    private int randomNumber;
    private double returnAmount;
    private double netResult;
    private boolean jackpotWin;
    private String message;
    private static final long serialVersionUID = 1L;
    
    public PlayResult(String playerId, String gameName, double betAmount, int randomNumber, double returnAmount, double netResult, boolean jackpotWin, String message){
        this.playerId = playerId;
        this.gameName = gameName;
        this.betAmount = betAmount;
        this.randomNumber = randomNumber;
        this.returnAmount = returnAmount;
        this.netResult = netResult;
        this.jackpotWin = jackpotWin;
        this.message = message;
    }

    public String getPlayerId(){
        return playerId;
    }

    public String getGameName(){
        return gameName;
    }

    public double getBetAmount(){
        return betAmount;
    }

    public int getRandomNumber(){
        return randomNumber;
    }

    public double getReturnAmount(){
        return returnAmount;
    }

    public double getNetResult(){
        return netResult;
    }

    public boolean isJackpotWin(){
        return jackpotWin;
    }

    public String getMessage(){
        return message;
    }
}