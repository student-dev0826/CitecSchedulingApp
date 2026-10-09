package com.example.citecschedulingapp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

/** Builds one row of the chat list. Unread rows get a tinted card, bold blue name and a red count. */
public final class ChatContactCard {

    private ChatContactCard() {}

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static View build(Context ctx, String name, String snippet, String time,
                             int unread, View.OnClickListener onClick) {
        boolean hasUnread = unread > 0;
        int accent = ctx.getResources().getColor(R.color.accent);

        MaterialCardView card = new MaterialCardView(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(ctx, 10));
        card.setLayoutParams(lp);
        card.setRadius(dp(ctx, 16));
        card.setCardElevation(dp(ctx, hasUnread ? 3 : 1));
        card.setCardBackgroundColor(hasUnread ? Color.parseColor("#EFF6FF") : Color.WHITE);
        card.setStrokeColor(hasUnread ? accent : ctx.getResources().getColor(R.color.border_color));
        card.setStrokeWidth(hasUnread ? dp(ctx, 2) : 1);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(ctx);
        tvName.setText(name);
        tvName.setTextSize(16f);
        tvName.setSingleLine(true);
        tvName.setEllipsize(TextUtils.TruncateAt.END);
        tvName.setTypeface(null, hasUnread ? Typeface.BOLD : Typeface.NORMAL);
        tvName.setTextColor(hasUnread ? accent : ctx.getResources().getColor(R.color.primary));

        TextView tvSnippet = new TextView(ctx);
        tvSnippet.setText(snippet);
        tvSnippet.setTextSize(13f);
        tvSnippet.setSingleLine(true);
        tvSnippet.setEllipsize(TextUtils.TruncateAt.END);
        tvSnippet.setPadding(0, dp(ctx, 3), 0, 0);
        tvSnippet.setTypeface(null, hasUnread ? Typeface.BOLD : Typeface.NORMAL);
        tvSnippet.setTextColor(ctx.getResources().getColor(
                hasUnread ? R.color.text_primary : R.color.text_secondary));

        textCol.addView(tvName);
        textCol.addView(tvSnippet);
        row.addView(textCol);

        LinearLayout right = new LinearLayout(ctx);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        right.setPadding(dp(ctx, 10), 0, 0, 0);

        if (time != null && !time.isEmpty()) {
            TextView tvTime = new TextView(ctx);
            tvTime.setText(time);
            tvTime.setTextSize(11f);
            tvTime.setTextColor(hasUnread ? accent : ctx.getResources().getColor(R.color.text_secondary));
            right.addView(tvTime);
        }

        if (hasUnread) {
            TextView badge = new TextView(ctx);
            badge.setText(unread > 9 ? "9+" : String.valueOf(unread));
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(11f);
            badge.setTypeface(null, Typeface.BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackgroundResource(R.drawable.bg_unread_badge);
            badge.setMinWidth(dp(ctx, 24));
            badge.setMinHeight(dp(ctx, 24));
            badge.setPadding(dp(ctx, 7), 0, dp(ctx, 7), 0);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx, 24));
            bp.topMargin = dp(ctx, 6);
            badge.setLayoutParams(bp);
            right.addView(badge);
        }

        row.addView(right);
        card.addView(row);
        card.setOnClickListener(onClick);
        return card;
    }
}
