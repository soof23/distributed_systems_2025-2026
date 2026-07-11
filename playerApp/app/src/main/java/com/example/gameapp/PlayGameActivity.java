package com.example.gameapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class PlayGameActivity extends AppCompatActivity {
    private Game selectedGame;
    private String playerId;
    private double balance = 0.0;
    private ImageButton buttonHomePlay;
    private ImageView playLogo;
    private ImageView imageViewCoinPlay;
    private TextView viewTopBalancePlay;
    private TextView playGameName;
    private TextView textViewProvider;
    private LinearLayout layoutStars;
    private TextView textViewVotes;
    private TextView textViewMinBet;
    private TextView textViewMaxBet;
    private TextView textViewRisk;
    private TextView textViewBetType;
    private TextView textViewJackpot;
    private TextView textPlayResult;
    private TextView textCurrentBalancePlay;
    private EditText editBetAmount;

    private Button buttonPlay;
    private Button buttonPlayAgain;
    private Button buttonRate;

    private LinearLayout layoutGameInfo;
    private LinearLayout layoutBetSection;
    private LinearLayout layoutResultSection;
    private LinearLayout layoutBetInput;
    private TextView textViewSelectBet;

    private ProgressBar progressBarPlay;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_game);

        // Ενεργοποίηση κουμπιού Back στην πάνω μπάρα
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        selectedGame = (Game) getIntent().getSerializableExtra("SELECTED_GAME");
        playerId = getIntent().getStringExtra("PLAYER_ID");
        balance = getIntent().getDoubleExtra("BALANCE", 0.0);

        if (playerId == null || playerId.trim().isEmpty()) {
            playerId = "Player";
        }

        buttonHomePlay = findViewById(R.id.buttonHomePlay);
        playLogo = findViewById(R.id.playGameLogo);
        imageViewCoinPlay = findViewById(R.id.imageViewCoinPlay);

        viewTopBalancePlay = findViewById(R.id.textViewTopBalancePlay);
        playGameName = findViewById(R.id.playGameName);

        textViewProvider = findViewById(R.id.textViewProvider);
        layoutStars = findViewById(R.id.layoutStars);
        textViewVotes = findViewById(R.id.textViewVotes);
        textViewMinBet = findViewById(R.id.textViewMinBet);
        textViewMaxBet = findViewById(R.id.textViewMaxBet);
        textViewRisk = findViewById(R.id.textViewRisk);
        textViewBetType = findViewById(R.id.textViewBetType);
        textViewJackpot = findViewById(R.id.textViewJackpot);

        textPlayResult = findViewById(R.id.textPlayResult);
        textCurrentBalancePlay = findViewById(R.id.textCurrentBalancePlay);
        editBetAmount = findViewById(R.id.editBetAmount);
        buttonPlay = findViewById(R.id.buttonPlay);
        buttonPlayAgain = findViewById(R.id.buttonPlayAgain);
        buttonRate = findViewById(R.id.buttonRate);

        layoutGameInfo = findViewById(R.id.layoutGameInfo);
        layoutBetSection = findViewById(R.id.layoutBetSection);
        layoutResultSection = findViewById(R.id.layoutResultSection);
        layoutBetInput = findViewById(R.id.layoutBetInput);
        textViewSelectBet = findViewById(R.id.textViewSelectBetAmount);

        progressBarPlay = findViewById(R.id.progressBarPlay);

        showGameBeforePlay();

        showBet();

        updateBalanceTexts();

        updateBalanceServer();

        buttonHomePlay.setOnClickListener(v -> goBackToMenu());

        buttonPlay.setOnClickListener(v -> handlePlay());

        buttonPlayAgain.setOnClickListener(v -> showBet());

        buttonRate.setOnClickListener(v -> {
            Intent intent = new Intent(PlayGameActivity.this, RateGameActivity.class);
            intent.putExtra("SELECTED_GAME", selectedGame);
            intent.putExtra("PLAYER_ID", playerId);
            intent.putExtra("BALANCE", balance);
            startActivity(intent);
        });
    }

    private void showGameBeforePlay() {
        if (selectedGame == null) {
            playGameName.setText("Game Name");
            textViewProvider.setText("Provider1");
            textViewVotes.setText("Votes: 0");
            textViewMinBet.setText("Min Bet: 0 FUN");
            textViewMaxBet.setText("Max Bet: 0 FUN");
            textViewRisk.setText("Risk: high");
            textViewBetType.setText("Bet Type: $$$");
            textViewJackpot.setText("Jackpot: 0 FUN");

            layoutStars.removeAllViews();
            addFullStars();
            return;
        }

        playGameName.setText(selectedGame.getGameName());

        textViewProvider.setText("Provider: " + selectedGame.getProviderName());

        showStars(selectedGame.getStars());

        textViewVotes.setText("Votes: " + selectedGame.getNoOfVotes());
        textViewMinBet.setText("Min Bet: " + selectedGame.getMinBet());
        textViewMaxBet.setText("Max Bet: " + selectedGame.getMaxBet());
        textViewRisk.setText("Risk: " + selectedGame.getRiskLevel());
        textViewBetType.setText("Bet Type: " + selectedGame.getBetType());
        textViewJackpot.setText("Jackpot: " + selectedGame.getJackpot());

        String logo = selectedGame.getGameLogo();

        if (logo != null && !logo.isEmpty()) {
            int resId = getResources().getIdentifier(logo, "drawable", getPackageName());

            if (resId != 0) {
                playLogo.setImageResource(resId);
            }
        }
    }

    private void showStars(int stars) {
        layoutStars.removeAllViews();

        for (int i = 0; i < stars; i++) {
            addFullStars();
        }
    }

    private void addFullStars() {
        ImageView star = new ImageView(this);
        star.setImageResource(R.drawable.star);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(42, 42);

        params.setMargins(4, 0, 4, 0);
        star.setLayoutParams(params);
        star.setContentDescription("Full Star");
        layoutStars.addView(star);
    }

    private void updateBalanceTexts() {
        viewTopBalancePlay.setText(balance + " FUN");
        textCurrentBalancePlay.setText("Current Balance: \n" + balance + " FUN");
    }

    private void updateBalanceServer() {
        new Thread(() -> {
            Object balanceResponse = NetworkClient.getInstance().sendRequest("getBalance", playerId);

            runOnUiThread(() -> {
                if (balanceResponse instanceof Double) {
                    balance = (Double) balanceResponse;
                    updateBalanceTexts();
                }
            });
        }).start();
    }

    private void handlePlay() {
        String betS = editBetAmount.getText().toString().trim();

        if (betS.isEmpty()) {
            Toast.makeText(this, "Plase enter bet amount", Toast.LENGTH_SHORT).show();
            return;
        }

        double bet;

        try {
            bet = Double.parseDouble(betS);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid Bet Amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedGame == null) {
            Toast.makeText(this, "No game selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bet < selectedGame.getMinBet() || bet > selectedGame.getMaxBet()) {
            Toast.makeText(this, "Bet must be between Min bet and Max bet.", Toast.LENGTH_LONG).show();
            return;
        }

        sendPlayRequest(bet);
    }

    private void sendPlayRequest(double bet) {
        progressBarPlay.setVisibility(View.VISIBLE);
        buttonPlay.setEnabled(false);

        new Thread(() -> {
            PlayRequest request = new PlayRequest(playerId, selectedGame.getGameName(), bet);
            Object response = NetworkClient.getInstance().sendRequest("play", request);

            runOnUiThread(() -> {
                progressBarPlay.setVisibility(View.GONE);
                buttonPlay.setEnabled(true);

                if (response instanceof String) {
                    Toast.makeText(this, (String) response, Toast.LENGTH_LONG).show();
                } else if (response instanceof PlayResult) {
                    PlayResult result = (PlayResult) response;

                    showPlayResult(result);
                    updateBalanceServer();
                } else {
                    Toast.makeText(this, "Response: ", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void showPlayResult(PlayResult result) {
        String resultText;
        if (result.isJackpotWin()) {
            resultText =
                    "JACKPOT!\n" +
                            "Return: " + result.getReturnAmount() + " FUN\n" +
                            "Net: " + result.getNetResult() + " FUN";
        } else if (result.getNetResult() > 0) {
            resultText =
                    "You won!\n" +
                            "Return: " + result.getReturnAmount() + " FUN\n" +
                            "Net: " + result.getNetResult() + " FUN";
        } else {
            resultText =
                    "You Lost \n" +
                            "Return: " + result.getReturnAmount() + " FUN\n" +
                            "Net: " + result.getNetResult() + " FUN";
        }

        textPlayResult.setText(resultText);
        showResult();
    }

    private void showBet() {
        editBetAmount.setText("");

        layoutGameInfo.setVisibility(View.VISIBLE);
        layoutBetSection.setVisibility(View.VISIBLE);
        layoutResultSection.setVisibility(View.GONE);

        textViewSelectBet.setVisibility(View.VISIBLE);
        layoutBetInput.setVisibility(View.VISIBLE);
        buttonPlay.setVisibility(View.VISIBLE);
    }

    private void showResult() {
        layoutGameInfo.setVisibility(View.GONE);
        layoutBetSection.setVisibility(View.VISIBLE);
        textViewSelectBet.setVisibility(View.GONE);
        layoutBetInput.setVisibility(View.GONE);
        buttonPlay.setVisibility(View.GONE);
        layoutResultSection.setVisibility(View.VISIBLE);
    }

    private void goBackToMenu() {
        Intent intent = new Intent(PlayGameActivity.this, MenuActivity.class);
        intent.putExtra("PLAYER_ID", playerId);
        intent.putExtra("BALANCE", balance);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}