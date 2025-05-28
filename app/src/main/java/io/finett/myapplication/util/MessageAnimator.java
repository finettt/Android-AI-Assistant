package io.finett.myapplication.util;

import android.content.Context;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.recyclerview.widget.RecyclerView;

import io.finett.myapplication.R;

/**
 * Утилитарный класс для анимации сообщений в RecyclerView
 */
public class MessageAnimator {

    private static int lastPosition = -1;
    
    /**
     * Применяет анимацию к новым элементам RecyclerView
     * @param view представление элемента
     * @param position позиция элемента
     * @param context контекст
     */
    public static void animate(View view, int position, Context context) {
        // Анимируем только новые элементы (с позициями больше чем lastPosition)
        if (position > lastPosition) {
            Animation animation = AnimationUtils.loadAnimation(context, R.anim.message_appear);
            view.startAnimation(animation);
            lastPosition = position;
        }
    }
    
    /**
     * Сбрасывает счетчик позиций, чтобы анимация могла быть применена повторно
     * Должен вызываться при обновлении всего списка
     */
    public static void resetAnimationState() {
        lastPosition = -1;
    }
    
    /**
     * Очищает анимацию всех элементов в RecyclerView
     * @param recyclerView представление списка
     */
    public static void clearAnimations(RecyclerView recyclerView) {
        if (recyclerView != null) {
            int childCount = recyclerView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = recyclerView.getChildAt(i);
                if (child != null) {
                    child.clearAnimation();
                }
            }
        }
    }
} 