package com.streamnest.iptv;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private int dp(float value) {
        return (int) (value *
            getResources().getDisplayMetrics().density);
    }

    private TextView makeLabel(String text, int size) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(18), dp(14), dp(18), dp(14));
        return label;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(15, 20, 35));
        getWindow().setNavigationBarColor(Color.rgb(15, 20, 35));

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.rgb(15, 20, 35));
        screen.setPadding(dp(18), dp(12), dp(18), dp(12));

        TextView title = makeLabel("StreamNest IPTV", 30);
        title.setTextColor(Color.rgb(95, 190, 255));
        title.setTypeface(null, Typeface.BOLD);
        screen.addView(title);

        screen.addView(makeLabel(
            "Your entertainment, organized.", 16));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(menu);

        addSection(menu, "LIVE TV",
            "Watch channels from your playlist");
        addSection(menu, "TV GUIDE",
            "Browse programs and schedules");
        addSection(menu, "MOVIES",
            "Browse your video-on-demand library");
        addSection(menu, "TV SERIES",
            "Explore seasons and episodes");
        addSection(menu, "CATCH-UP",
            "Replay programs where supported");
        addSection(menu, "SETTINGS",
            "Configure playlists and preferences");

        screen.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(screen);

        if (menu.getChildCount() > 0) {
            menu.getChildAt(0).requestFocus();
        }
    }

    private void addSection(LinearLayout menu,
                            String title, String subtitle) {
        TextView item = makeLabel(title + "\n" + subtitle, 17);
        item.setTextColor(Color.WHITE);
        item.setBackgroundColor(Color.rgb(35, 45, 65));
        item.setFocusable(true);
        item.setClickable(true);
        item.setOnFocusChangeListener((view, focused) -> {
            view.setBackgroundColor(focused
                ? Color.rgb(25, 115, 175)
                : Color.rgb(35, 45, 65));
        });

        item.setOnClickListener(view ->
            Toast.makeText(this,
                title + " — feature coming next",
                Toast.LENGTH_SHORT).show());

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        menu.addView(item, params);
    }
}
