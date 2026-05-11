package com.dreampediatrics.app;

import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;


public class AnimationUtil {

    public static void scaleDown(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.95f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0.95f);
        scaleX.setDuration(100);
        scaleY.setDuration(100);
        scaleX.setInterpolator(new DecelerateInterpolator());
        scaleY.setInterpolator(new DecelerateInterpolator());
        scaleX.start();
        scaleY.start();
    }

    public static void scaleUp(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f);
        scaleX.setDuration(100);
        scaleY.setDuration(100);
        scaleX.setInterpolator(new DecelerateInterpolator());
        scaleY.setInterpolator(new DecelerateInterpolator());
        scaleX.start();
        scaleY.start();
    }

    public static void slideRight(View view, float distance) {
        ObjectAnimator translateX = ObjectAnimator.ofFloat(view, "translationX", distance);
        translateX.setDuration(100);
        translateX.setInterpolator(new DecelerateInterpolator());
        translateX.start();
    }

    public static void slideBack(View view) {
        ObjectAnimator translateX = ObjectAnimator.ofFloat(view, "translationX", 0f);
        translateX.setDuration(100);
        translateX.setInterpolator(new DecelerateInterpolator());
        translateX.start();
    }
}
