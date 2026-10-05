package com.example.citecschedulingapp;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/** Small helpers for building the cards that the list screens create in code. */
public final class UiUtil {

    private UiUtil() {}

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static MaterialCardView card(Context c) {
        MaterialCardView card = new MaterialCardView(c);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(c, 12);
        card.setLayoutParams(p);
        card.setRadius(dp(c, 12));
        card.setCardElevation(dp(c, 2));
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeColor(ContextCompat.getColor(c, R.color.border_color));
        card.setStrokeWidth(dp(c, 1));
        return card;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        return l;
    }

    public static TextView text(Context c, String s, float sp, int colorRes, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(ContextCompat.getColor(c, colorRes));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, 4);
        t.setLayoutParams(p);
        return t;
    }

    public static TextView badge(Context c, String s, String bgHex, String fgHex) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(10f);
        t.setTextColor(Color.parseColor(fgHex));
        t.setBackgroundColor(Color.parseColor(bgHex));
        t.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    /** Title on the left, status badge on the right. */
    public static LinearLayout headerRow(Context c, String title, TextView badge) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(16f);
        t.setTextColor(ContextCompat.getColor(c, R.color.primary));
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        if (badge != null) row.addView(badge);
        return row;
    }

    public static MaterialButton outlinedButton(Context c, String label, int colorInt) {
        MaterialButton b = new MaterialButton(c, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        b.setTextSize(12f);
        b.setTextColor(colorInt);
        b.setStrokeColor(android.content.res.ColorStateList.valueOf(colorInt));
        b.setCornerRadius(dp(c, 8));
        return b;
    }

    public static LinearLayout buttonRow(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, 12);
        row.setLayoutParams(p);
        return row;
    }

    /** Adds a button to a buttonRow, sharing the width equally. */
    public static void addToButtonRow(Context c, LinearLayout row, MaterialButton b, boolean first) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (!first) p.setMarginStart(dp(c, 8));
        b.setLayoutParams(p);
        row.addView(b);
    }
}
