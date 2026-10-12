package com.streamnest.iptv;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private int dp(float value) {
        return (int) (value * getResources()
            .getDisplayMetrics().density);
    }

    private TextView makeLabel(String text, int size) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(16), dp(12), dp(16), dp(12));
        return label;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.rgb(15, 20, 35));
        screen.setPadding(dp(12), dp(16), dp(12), dp(12));

        TextView title = makeLabel("StreamNest IPTV", 28);
        title.setTextColor(Color.rgb(95, 190, 255));
        screen.addView(title);

        screen.addView(makeLabel(
            "Your entertainment, organized.", 16));

        addSection(screen, "LIVE TV",
            "Watch channels from your playlist");
        addSection(screen, "TV GUIDE",
            "Browse programs and schedules");
        addSection(screen, "MOVIES",
            "Browse your video-on-demand library");
        addSection(screen, "TV SERIES",
            "Explore seasons and episodes");
        addSection(screen, "CATCH-UP",
            "Replay programs where supported");
        addSection(screen, "SETTINGS",
            "Configure playlists and preferences");

        setContentView(screen);
    }

    private void addSection(LinearLayout screen,
                           String title, String subtitle) {
        TextView item = makeLabel(
            title + "\n" + subtitle, 16);
        item.setBackgroundColor(Color.rgb(35, 45, 65));

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);

        screen.addView(item, params);
    }
}
