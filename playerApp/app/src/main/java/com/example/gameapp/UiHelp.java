package com.example.gameapp;

import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

public class UiHelp {
    public static void rotateCoin(ImageView coin) {
        if (coin == null) {
            return;
        }

        ObjectAnimator animator = ObjectAnimator.ofFloat(coin, View.ROTATION, 0f, 360f);
        animator.setDuration(900);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.start();
    }
}
