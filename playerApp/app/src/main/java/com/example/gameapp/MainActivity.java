package com.example.gameapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText editTextPlayerId;
    private Button buttonLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editTextPlayerId = findViewById(R.id.editTextPlayerId);
        buttonLogin = findViewById(R.id.buttonLogin);

        buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String playerId = editTextPlayerId.getText().toString().trim();
                if (playerId.isEmpty()) {
                    Toast.makeText(MainActivity.this, "give an id", Toast.LENGTH_SHORT).show();
                } else {
                    performLogin(playerId);
                }
            }
        });
    }

    private void performLogin(String playerId) {
        ProgressBar progressBar = findViewById(R.id.progressBarLogin);
        buttonLogin.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                Object response = NetworkClient.getInstance().sendRequest("registerPlayer", playerId);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    buttonLogin.setEnabled(true);
                    if ("REGISTER_OK".equals(response)) {
                        Intent intent = new Intent(MainActivity.this, MenuActivity.class);
                        intent.putExtra("PLAYER_ID", playerId);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(MainActivity.this, "Error: " + response, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    buttonLogin.setEnabled(true);
                });
            }
        }).start();
    }
}