package com.example.gameapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {
    private List<Game> gameList;
    private OnGameClickListener listener;

    public interface OnGameClickListener {
        void onGameClick(Game game);
    }

    public GameAdapter(List<Game> gameList, OnGameClickListener listener) {
        this.gameList = gameList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.game_item, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        Game game = gameList.get(position);
        holder.name.setText(game.getGameName());
        holder.minBet.setText("Min Bet: " + game.getMinBet());
        holder.maxBet.setText("Max Bet: " + game.getMaxBet());


        String logoName = game.getGameLogo();
        if (logoName != null && !logoName.isEmpty()) {
            int resId = holder.itemView.getContext().getResources().getIdentifier(
                    logoName,
                    "drawable",
                    holder.itemView.getContext().getPackageName()
            );

            if (resId != 0) {
                holder.logo.setImageResource(resId);
            } else {
                // default logo
                holder.logo.setImageResource(R.drawable.ic_launcher_background);
            }
        } else {
            holder.logo.setImageResource(R.drawable.ic_launcher_background);
        }

        holder.itemView.setOnClickListener(v -> listener.onGameClick(game));
    }

    @Override
    public int getItemCount() {
        return gameList.size();
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        ImageView logo;
        TextView name, minBet, maxBet;

        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            logo = itemView.findViewById(R.id.imageLogo);
            name = itemView.findViewById(R.id.textGameName);
            minBet = itemView.findViewById(R.id.textMinBet);
            maxBet = itemView.findViewById(R.id.textMaxBet);
        }
    }
}
