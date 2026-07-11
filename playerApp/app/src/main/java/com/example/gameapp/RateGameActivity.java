package com.example.gameapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.EditText;
import android.widget.ImageButton;

public class RateGameActivity extends AppCompatActivity{
    private Game selectedGame;
    private String playerId;
    private double balance = 0.0;
    private ImageButton buttonHomeRate;
    private ImageView imageViewRateLogo;
    private ImageView imageViewCoinRate;
    private TextView textViewTopBalanceRate;
    private TextView textViewRateGameName;
    private TextView textViewRating;
    private TextView textViewNumberOfVotes;
    private LinearLayout layoutRateStars;
    private EditText editTextMyRating;
    private Button buttonSubmit;
    private ProgressBar progressBarRate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_rate_game);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        selectedGame = (Game) getIntent().getSerializableExtra("SELECTED_GAME");
        playerId = getIntent().getStringExtra("PLAYER_ID");
        balance = getIntent().getDoubleExtra("BALANCE", 0.0);

        if (playerId == null || playerId.trim().isEmpty()) {
            playerId = "Player";
        }

        buttonHomeRate = findViewById(R.id.buttonHomeRate);
        imageViewRateLogo = findViewById(R.id.imageViewRateLogo);
        imageViewCoinRate = findViewById(R.id.imageViewCoinRate);

        textViewTopBalanceRate = findViewById(R.id.textViewTopBalanceRate);
        textViewRateGameName = findViewById(R.id.textViewRateGameName);
        textViewRating = findViewById(R.id.textViewRating);
        textViewNumberOfVotes = findViewById(R.id.textViewNumberOfVotes);
        layoutRateStars = findViewById(R.id.layoutRateStars);

        editTextMyRating = findViewById(R.id.editTextMyRating);
        buttonSubmit = findViewById(R.id.buttonSubmitRate);
        progressBarRate = findViewById(R.id.progressBarRate);

        UiHelp.rotateCoin(imageViewCoinRate);

        showGameInfo();

        updateBalanceText();

        buttonHomeRate.setOnClickListener(v -> goBackToMenu());

        buttonSubmit.setOnClickListener(v -> handleRate());

    }

    private void showGameInfo() {
        if (selectedGame == null) {
            textViewRateGameName.setText("Game Name");
            textViewRating.setText("(0)");
            textViewNumberOfVotes.setText("Number of Votes: 0");
            layoutRateStars.removeAllViews();
            return;
        }

        textViewRateGameName.setText(selectedGame.getGameName());
        textViewRating.setText("(" + selectedGame.getStars() + ")");
        textViewNumberOfVotes.setText("Number of Votes: " + selectedGame.getNoOfVotes());

        showStars(selectedGame.getStars());

        String logo = selectedGame.getGameLogo();

        if (logo != null && !logo.isEmpty()) {
            int resId = getResources().getIdentifier(logo, "drawable", getPackageName());

            if (resId != 0) {
                imageViewRateLogo.setImageResource(resId);
            }
        }
    }

    private void showStars(int stars) {
        layoutRateStars.removeAllViews();

        int fullStars = (int) stars;

        for (int i = 0; i < fullStars; i++) {
            addFullStar();
        }
    }

    private void addFullStar() {
        ImageView star = new ImageView(this);

        star.setImageResource(R.drawable.star);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(58,58);
        params.setMargins(5,0,5,0);

        star.setLayoutParams(params);
        star.setContentDescription("Star");

        layoutRateStars.addView(star);
    }

    private void updateBalanceText() {
        textViewTopBalanceRate.setText(balance + " FUN");
    }

    private void handleRate() {
        if (selectedGame == null) {
            Toast.makeText(this, "No game selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String ratingS = editTextMyRating.getText().toString().trim();

        if (ratingS.isEmpty()) {
            Toast.makeText(this, "Plese enter rating.", Toast.LENGTH_SHORT).show();
            return;
        }

        int rating;

        try {
            rating = Integer.parseInt(ratingS);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
            return;
        }

        if (rating < 1 || rating > 5) {
            Toast.makeText(this, "Rating must be between 1-5 stars", Toast.LENGTH_SHORT).show();
            return;
        }

        sendRateRequest(rating);
    }

    private void sendRateRequest(int rating) {
        progressBarRate.setVisibility(View.VISIBLE);
        buttonSubmit.setEnabled(false);

        new Thread(() -> {
            RateRequest request = new RateRequest(playerId, selectedGame.getGameName(), rating);

            Object response = NetworkClient.getInstance().sendRequest("rate", request);

            runOnUiThread(() -> {
                progressBarRate.setVisibility(View.GONE);
                buttonSubmit.setEnabled(true);

                Toast.makeText(this, "Server response: " + response, Toast.LENGTH_LONG).show();

                Intent intent = new Intent(RateGameActivity.this, PlayGameActivity.class);
                intent.putExtra("SELECTED_GAME", selectedGame);
                intent.putExtra("PLAYER_ID", playerId);
                intent.putExtra("BALANCE", balance);

                startActivity(intent);
                finish();
            });
        }).start();
    }

    private void goBackToMenu() {
        Intent intent = new Intent(RateGameActivity.this, MenuActivity.class);

        intent.putExtra("PLAYER_ID", playerId);
        intent.putExtra("BALANCE", balance);

        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}