package com.example.gameapp;

import java.io.Serializable;

/*
Request for AddBalance
στέλνει playerId και amount
το class είναι serializable για να μπορεί
να στέλνει μέσω ObjectOutputStream()
από τον Player στον Master
*/

public class BalanceRequest implements Serializable {
	private String playerId;
	private double amount;
	private static final long serialVersionUID = 1L;

	// constructor gia request addBalance
	public BalanceRequest(String playerId, double amount) {
		this.playerId = playerId;
		this.amount = amount;
	}

	public String getPlayerId() {
		return playerId;
	}

	public double getAmount() {
		return amount;
	}
}
