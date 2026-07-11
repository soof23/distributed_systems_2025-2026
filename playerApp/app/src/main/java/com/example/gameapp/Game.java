package com.example.gameapp;

import java.io.Serializable;

public class Game implements Serializable{
    //Μεταβλητές που θα δέχεται απο json
    private String gameName;
    private String providerName;
    private int stars;
    private int noOfVotes;
    private String gameLogo;
    private double minBet;
    private double maxBet;
    private String riskLevel;
    private String hashKey;

    private double jackpot;
    private String betType;
    private Boolean isActive;
    private double[] riskTable;
    private static final long serialVersionUID = 1L;

    public Game(){
    }

    //Constructor για την δημιουργία του αντικειμένου
    public Game(String gameName, String providerName, int stars, int noOfVotes, String gameLogo, double minBet, double maxBet, String riskLevel, String hashKey) {
        this.gameName = gameName;
        this.providerName = providerName;
        this.stars = stars;
        this.noOfVotes = noOfVotes;
        this.gameLogo = gameLogo;
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.riskLevel = riskLevel;
        this.hashKey = hashKey;

        this.isActive = true;

        initializeGameParameters();
    }

    //Aυτόματος υπολογισμός κατηγορίας πονταρίσματος και Jackpot του παιχνιδιού
    private void initializeGameParameters(){
        //Καθορισμός κατηγορίας πονταρίσματος βάσει ελάχιστου πονταρίσματος 
        if (this.minBet >= 5.0) {
            this.betType = "$$$";
        } else if (this.minBet >= 1.0) {
            this.betType = "$$";
        } else {
            this.betType = "$";
        }

        // σε περίπτωση error
        if (this.riskLevel == null) {
            this.riskLevel = "high";
        }

        //Καθορισμός πινάκων πολλαπλασιαστών και Jackpot βάσει επιπέδου ρίσκου
        switch (this.riskLevel) {
            case "low":
                this.riskTable = new double[]{0.0, 0.0, 0.0, 0.1, 0.5, 1.0, 1.1, 1.3, 2.0, 2.5};
                this.jackpot = 10.0;
                break;
            case "medium":
                this.riskTable = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 1.0, 1.5, 2.5, 3.5};
                this.jackpot = 20.0;
                break;
            case "high":
                this.riskTable = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 6.5};
                this.jackpot = 40.0;
                break;
            default:
                this.riskLevel = "high";
                this.riskTable = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 6.5};
                this.jackpot = 40.0;
                break;
        }
    }

    //Μεθόδος για την μετατροπή απο json και αποθήκευση στο αντικείμενο
    public static Game toJson(String jsonTexString) {
        String TempGameName = "";
        String TempProviderName = "";
        int TempStars = 0;
        int TempNoOfVotes = 0;
        String TempLogo = "";
        double TempMinBet = 0.0;
        double TempMaxBet = 0.0;
        String TempRiskLevel = "";
        String TempHashKey = "";

        String cleanJson = jsonTexString.replace("{", "").replace("}", "").trim();
        String[] keyValuePairs = cleanJson.split(",");
        for (String pair : keyValuePairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length < 2) {
                continue;
            }
            String key = keyValue[0].trim().replaceAll("\"", "");
            String value = keyValue[1].trim().replaceAll("\"", "");

            switch (key) {
                case "GameName":
                    TempGameName = value;
                    break;
                case "ProviderName":
                    TempProviderName = value;
                    break;
                case "Stars":
                    TempStars = Integer.parseInt(value);
                    break;
                case "NoOfVotes":
                    TempNoOfVotes = Integer.parseInt(value);
                    break;
                case "GameLogo":
                    TempLogo = value;
                    break;
                case "MinBet":
                    TempMinBet = Double.parseDouble(value);
                    break;
                case "MaxBet":
                    TempMaxBet = Double.parseDouble(value);
                    break;
                case "RiskLevel":
                    TempRiskLevel = value;
                    break;
                case "HashKey":
                    TempHashKey = value;
                    break;
            }
        }
        return new Game(TempGameName, TempProviderName, TempStars, TempNoOfVotes, TempLogo, TempMinBet, TempMaxBet, TempRiskLevel, TempHashKey);
    }

    //getters and setters
    public String getGameName(){
        return gameName;
    }
	
    public String getProviderName(){
        return providerName;
    }
    
    public int getStars(){
        return stars;
    }
	
    public void setStars(int stars){
        this.stars = stars;
    }
	
    public int getNoOfVotes(){
        return noOfVotes;
    }
	
    public void setNoOfVotes(int noOfVotes){
        this.noOfVotes = noOfVotes;
    }
	
    public double getMinBet(){
        return minBet;
    }
	
    public void setMinBet(double minBet){
        this.minBet = minBet;
        initializeGameParameters();
    }
	
    public double getMaxBet(){
        return maxBet;
    }
	
    public String getRiskLevel(){
        return riskLevel;
    }
	
    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
        // Κάθε φορά που αλλάζει το riskLevel, ξαναυπολογίζουμε τον πίνακα riskTable και το jackpot
        initializeGameParameters(); 
    }
	
    public String getHashKey(){
        return hashKey;
    }
	
    public String getGameLogo(){
        return gameLogo;
    }
	
    public double getJackpot(){
        return jackpot;
    }
	
    public String getBetType(){
        return betType;
    }

    public void setGameLogo(String gameLogo) {
        this.gameLogo = gameLogo;
    }
	
    public Boolean getIsActive(){
        return isActive;
    }
	
    public void setIsActive(Boolean isActive){
        this.isActive = isActive;
    }
	
    public double[] getRiskTable() {
        return riskTable;
    }
}