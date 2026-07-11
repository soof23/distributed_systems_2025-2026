package com.example.gameapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class SearchActivity extends AppCompatActivity {
    private Spinner spinnerStars, spinnerBetType, spinnerRisk;
    private String playerId;
    private double balance = 0.0;
    private TextView textViewTopBalanceSearch;
    private ImageButton buttonHomeSearch;
    private ImageView imageViewCoinSearch;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        playerId = getIntent().getStringExtra("PLAYER_ID");
        balance = getIntent().getDoubleExtra("BALANCE", 0.0);

        if (playerId == null || playerId.trim().isEmpty()) {
            playerId = "Player";
        }

        spinnerStars = findViewById(R.id.spinnerStars);
        spinnerBetType = findViewById(R.id.spinnerBetType);
        spinnerRisk = findViewById(R.id.spinnerRisk);
        textViewTopBalanceSearch = findViewById(R.id.textViewTopBalanceSearch);
        Button buttonSearch = findViewById(R.id.buttonSearchGames);
        buttonHomeSearch = findViewById(R.id.buttonHomeSearch);
        imageViewCoinSearch = findViewById(R.id.imageViewCoinSearch);

        UiHelp.rotateCoin(imageViewCoinSearch);

        updateBalanceText();
        refreshBalance();

        setupSpinners();
        buttonHomeSearch.setOnClickListener(v -> goBackToMenu());
        buttonSearch.setOnClickListener(v -> search());
    }

    private void setupSpinners() {
        String[] starOptions = {"Skip", "1", "2", "3", "4", "5"};
        spinnerStars.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, starOptions));

        String[] betOptions = {"Skip", "$", "$$", "$$$"};
        spinnerBetType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, betOptions));

        String[] riskOptions = {"Skip", "low", "medium", "high"};
        spinnerRisk.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, riskOptions));
    }

    private void updateBalanceText() {
        textViewTopBalanceSearch.setText(balance + " FUN");
    }

    private void refreshBalance() {
        new Thread (() -> {
        Object response = NetworkClient.getInstance().sendRequest("getBalance", playerId);

        runOnUiThread(() -> {
                if (response instanceof Double) {
                    balance = (Double) response;
                    updateBalanceText();
                }
            });
        }).start();
    }


    private void search() {
        Integer stars = spinnerStars.getSelectedItemPosition() == 0 ? null : spinnerStars.getSelectedItemPosition();
        String betType = spinnerBetType.getSelectedItem().toString().equals("Skip") ? null : spinnerBetType.getSelectedItem().toString();
        String risk = spinnerRisk.getSelectedItem().toString().equals("Skip") ? null : spinnerRisk.getSelectedItem().toString();

        String searchId = playerId + "_" + System.currentTimeMillis();

        ProgressBar progressBar = findViewById(R.id.progressBarSearch);
        Button buttonSearch = findViewById(R.id.buttonSearchGames);

        progressBar.setVisibility(View.VISIBLE);
        buttonSearch.setEnabled(false);

        new Thread(() -> {
            SearchRequest request = new SearchRequest(playerId, searchId, stars, betType, risk);

            Object response = NetworkClient.getInstance().sendRequest("search", request);

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                buttonSearch.setEnabled(true);
                if (response instanceof List) {
                    DataHolder.getInstance().setGamesList((List<Game>) response);
                    Intent intent = new Intent(this, GameListActivity.class);
                    intent.putExtra("PLAYER_ID", playerId);
                    intent.putExtra("BALANCE", balance);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "No games found", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void goBackToMenu() {
        Intent intent = new Intent(SearchActivity.this, MenuActivity.class);
        intent.putExtra("PLAYER_ID", playerId);
        intent.putExtra("BALANCE", balance);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
