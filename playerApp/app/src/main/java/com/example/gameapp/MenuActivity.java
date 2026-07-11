package com.example.gameapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class MenuActivity extends AppCompatActivity {
    private String playerId;
    private double balance = 0.0;
    private TextView textViewBalance;
    private TextView textViewUsername;
    private ImageView imageViewCoin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        // get ID from MainActivity
        playerId = getIntent().getStringExtra("PLAYER_ID");
        balance = getIntent().getDoubleExtra("BALANCE", 0);

        if (playerId == null || playerId.trim().isEmpty()) {
            playerId = "Player";
        }

        // connection with XML
        textViewUsername = findViewById(R.id.textViewUsername);
        textViewBalance = findViewById(R.id.textViewBalance);
        Button buttonAddBalance = findViewById(R.id.buttonAddBalance);
        Button buttonShowGames = findViewById(R.id.buttonShowGames);
        Button buttonLogout = findViewById(R.id.buttonLogout);

        imageViewCoin = findViewById(R.id.imageViewCoin);

        textViewUsername.setText(playerId);

        UiHelp.rotateCoin(imageViewCoin);

        refreshBalance();

        buttonAddBalance.setOnClickListener(v -> {
            Intent intent = new Intent(MenuActivity.this, AddBalanceActivity.class);
            intent.putExtra("PLAYER_ID", playerId);
            intent.putExtra("BALANCE", balance);
            startActivity(intent);
        });

        buttonShowGames.setOnClickListener(v -> {
            ProgressBar progressBar = findViewById(R.id.progressBarMenu);
            buttonShowGames.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);
            new Thread(() -> {
                Object response = NetworkClient.getInstance().sendRequest("getAvailableGames", null);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    buttonShowGames.setEnabled(true);

                    if (response instanceof List) {
                        Intent intent = new Intent(this, GameListActivity.class);
                        DataHolder.getInstance().setGamesList((List<Game>) response);
                        intent.putExtra("PLAYER_ID", playerId);
                        intent.putExtra("BALANCE", balance);
                        startActivity(intent);
                    } else {
                        Toast.makeText(this, "No available games found", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });

        findViewById(R.id.buttonSearchGames).setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            intent.putExtra("PLAYER_ID", playerId);
            intent.putExtra("BALANCE", balance);
            startActivity(intent);
        });

        buttonLogout.setOnClickListener(v -> {
            new Thread(() -> {
                NetworkClient.getInstance().disconnect();

                runOnUiThread(() -> {
                    Intent intent = new Intent(MenuActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }).start();
        });
    }

    private void refreshBalance() {
        new Thread(() -> {
            Object response = NetworkClient.getInstance().sendRequest("getBalance", playerId);

            runOnUiThread(() -> {
                if (response instanceof Double) {
                    balance = (Double) response;
                    textViewBalance.setText(balance + " FUN");
                }
            });
        }).start();
    }

    private void showAddBalanceRequest() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Balance");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String amountStr = input.getText().toString();
            if (!amountStr.isEmpty()) {
                double amount = Double.parseDouble(amountStr);
                sendAddBalanceRequest(amount);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, id) -> dialog.cancel());
        builder.show();
    }

    private void sendAddBalanceRequest(double amount) {
        new Thread(() -> {
            BalanceRequest request = new BalanceRequest(playerId, amount);
            Object response = NetworkClient.getInstance().sendRequest("addBalance", request);

            runOnUiThread(() -> {
                if (response instanceof Double) {
                    balance = (Double) response;
                    textViewBalance.setText("Balance: " + balance + " FUN");
                    Toast.makeText(this, "success", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "error: " + response, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}