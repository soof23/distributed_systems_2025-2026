package com.example.gameapp;


import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.EditText;
import android.widget.ImageButton;


public class AddBalanceActivity extends AppCompatActivity {
    private String playerId;
    private double balance = 0;
    private TextView textViewTopBalance;
    private TextView textViewCurrentBalance;
    private EditText editTextAmount;
    private Button buttonAddBalance;
    private ImageButton buttonHome;
    private ProgressBar progressBarAddBalance;
    private TextView textViewAddBalanceTitle;
    private ImageView imageViewCoin;
    private ImageView imageViewCoinCurrentBalance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_balance);

        playerId = getIntent().getStringExtra("PLAYER_ID");
        balance = getIntent().getDoubleExtra("BALANCE", 0);

        if (playerId == null || playerId.trim().isEmpty()) {
            playerId = "Player";
        }

        textViewTopBalance = findViewById(R.id.textViewTopBalance);
        textViewCurrentBalance = findViewById(R.id.textViewCurrentBalance);

        imageViewCoin = findViewById(R.id.imageViewCoin);
        imageViewCoinCurrentBalance = findViewById(R.id.imageViewCurrentBalanceCoin);

        textViewAddBalanceTitle = findViewById(R.id.textViewAddBalanceTitle);
        editTextAmount = findViewById(R.id.editTextAmount);
        buttonAddBalance = findViewById(R.id.buttonAddBalance);
        buttonHome = findViewById(R.id.buttonHome);
        progressBarAddBalance = findViewById(R.id.progressBarAddBalance);

        UiHelp.rotateCoin(imageViewCoin);
        UiHelp.rotateCoin(imageViewCoinCurrentBalance);

        updateBalanceText();

        refreshBalance();

        buttonHome.setOnClickListener(v -> goBackToMenu());

        buttonAddBalance.setOnClickListener(v -> {
            String amountText = editTextAmount.getText().toString().trim();

            if (amountText.isEmpty()) {
                Toast.makeText(this, "Please enter amount!", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount;

            try {
                amount = Double.parseDouble(amountText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid Amount.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (amount <=0) {
                Toast.makeText(this, "Amount must be bigger than 0.", Toast.LENGTH_SHORT).show();
                return;
            }

            sendAddBalanceRequest(amount);
        });
    }

    private void updateBalanceText() {
        textViewTopBalance.setText(balance + " FUN");
        textViewCurrentBalance.setText("Current Balance: \n" + balance + " FUN");
    }

    private void refreshBalance() {
        new Thread(() -> {
            Object response = NetworkClient.getInstance().sendRequest("getBalance", playerId);
            runOnUiThread(() -> {
                if (response instanceof Double) {
                    balance = (double) response;
                    updateBalanceText();
                }
            });
        }).start();
    }

    private void sendAddBalanceRequest(double amount) {
        progressBarAddBalance.setVisibility(View.VISIBLE);
        buttonAddBalance.setEnabled(false);

        new Thread(() -> {
            BalanceRequest request = new BalanceRequest(playerId, amount);
            Object response = NetworkClient.getInstance().sendRequest("addBalance", request);

            runOnUiThread(() -> {
                progressBarAddBalance.setVisibility(View.GONE);
                buttonAddBalance.setEnabled(true);

                if (response instanceof Double) {
                    balance = (double) response;

                    editTextAmount.setText("");

                    updateBalanceText();
                    Toast.makeText(this, "Balance added.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Error: ", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void goBackToMenu() {
        Intent intent = new Intent(AddBalanceActivity.this, MenuActivity.class);

        intent.putExtra("PLAYER_ID", playerId);
        intent.putExtra("BALANCE", balance);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
