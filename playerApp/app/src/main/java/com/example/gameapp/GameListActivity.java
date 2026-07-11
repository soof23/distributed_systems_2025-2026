package com.example.gameapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class GameListActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private GameAdapter adapter;
    private String playerId;
    private double balance = 0.0;
    private TextView textNoGames;
    private TextView textViewTopBalanceGames;

    private ImageButton buttonHomeGames;
    private ImageView imageViewCoinGames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_list);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        playerId = getIntent().getStringExtra("PLAYER_ID");
        balance = getIntent().getDoubleExtra("BALANCE", 0.0);

        if (playerId == null || playerId.trim().isEmpty()) {
            playerId = "Player";
        }

        recyclerView = findViewById(R.id.recyclerViewGames);
        textNoGames = findViewById(R.id.textNoGames);
        textViewTopBalanceGames = findViewById(R.id.textViewTopBalanceGames);
        buttonHomeGames = findViewById(R.id.buttonHomeGames);
        imageViewCoinGames = findViewById(R.id.imageViewCoinGames);
        UiHelp.rotateCoin(imageViewCoinGames);

        updateBalanceText();

        refreshBalance();

        buttonHomeGames.setOnClickListener(v -> goBackToMenu());

        List<Game> games = DataHolder.getInstance().getGamesList();

        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        if (games == null || games.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            textNoGames.setVisibility(View.VISIBLE);
        } else {
            textNoGames.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

            adapter = new GameAdapter(games, game -> {
                Intent intent = new Intent(GameListActivity.this, PlayGameActivity.class);
                intent.putExtra("SELECTED_GAME", game);
                intent.putExtra("PLAYER_ID", playerId);
                intent.putExtra("BALANCE", balance);
                startActivity(intent);
            });
            recyclerView.setAdapter(adapter);
        }
    }

   private void updateBalanceText() {
        textViewTopBalanceGames.setText(balance + " FUN");
   }

   private void refreshBalance() {
        new Thread(() -> {
            Object response = NetworkClient.getInstance().sendRequest("getBalance", playerId);

            runOnUiThread(() -> {
                if (response instanceof Double) {
                    balance = (Double) response;
                    updateBalanceText();
                }
            });
        }).start();
   }

   private void goBackToMenu() {
        Intent intent = new Intent(GameListActivity.this, MenuActivity.class);

        intent.putExtra("PLAYER_ID", playerId);
        intent.putExtra("BALANCE", balance);

        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
   }
}