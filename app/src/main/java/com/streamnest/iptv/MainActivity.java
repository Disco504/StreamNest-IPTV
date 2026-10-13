package com.streamnest.iptv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
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
        screen.addView(makeLabel("Your entertainment, organized.", 16));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(menu);

        addSection(menu, "LIVE TV", "Watch channels from your playlist");
        addSection(menu, "TV GUIDE", "Browse programs and schedules");
        addSection(menu, "MOVIES", "Browse your video-on-demand library");
        addSection(menu, "TV SERIES", "Explore seasons and episodes");
        addSection(menu, "CATCH-UP", "Replay programs where supported");
        addSection(menu, "SETTINGS", "Configure playlists and preferences");

        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(screen);

        if (menu.getChildCount() > 0) {
            menu.getChildAt(0).requestFocus();
        }
    }

    private void addSection(LinearLayout menu, String title, String subtitle) {
        TextView item = makeLabel(title + "\n" + subtitle, 17);
        item.setBackgroundColor(Color.rgb(35, 45, 65));
        item.setFocusable(true);
        item.setClickable(true);

        item.setOnFocusChangeListener((view, focused) ->
                view.setBackgroundColor(focused
                        ? Color.rgb(25, 115, 175)
                        : Color.rgb(35, 45, 65)));

        item.setOnClickListener(view -> {
            if (title.equals("LIVE TV")) {
                showPlaylistDialog();
            } else {
                Toast.makeText(this, title + " — coming next",
                        Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        menu.addView(item, params);
    }

    private void showPlaylistDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Enter M3U playlist URL");
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(getPreferences(MODE_PRIVATE)
                .getString("playlist_url", ""));

        new AlertDialog.Builder(this)
                .setTitle("M3U Playlist")
                .setMessage("Enter your IPTV playlist URL")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Load", (dialog, which) -> {
                    String url = input.getText().toString().trim();

                    if (!url.startsWith("http://")
                            && !url.startsWith("https://")) {
                        Toast.makeText(this, "Enter a valid URL",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    getPreferences(MODE_PRIVATE).edit()
                            .putString("playlist_url", url).apply();

                    Toast.makeText(this, "Loading playlist...",
                            Toast.LENGTH_SHORT).show();
                    loadPlaylist(url);
                })
                .show();
    }

    private void loadPlaylist(String playlistUrl) {
        new Thread(() -> {
            ArrayList<String> names = new ArrayList<>();
            ArrayList<String> streams = new ArrayList<>();
            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection)
                        new URL(playlistUrl).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty(
                        "User-Agent", "StreamNestIPTV/1.0");

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    String pendingName = null;

                    while ((line = reader.readLine()) != null) {
                        line = line.trim();

                        if (line.startsWith("#EXTINF:")) {
                            int comma = line.lastIndexOf(',');
                            pendingName = comma >= 0
                                    ? line.substring(comma + 1).trim()
                                    : "Channel";
                        } else if (!line.isEmpty()
                                && !line.startsWith("#")
                                && pendingName != null) {
                            names.add(pendingName);
                            streams.add(line);
                            pendingName = null;
                        }
                    }
                }

                runOnUiThread(() -> {
                    if (names.isEmpty()) {
                        Toast.makeText(this,
                                "No channels found. Check the playlist URL.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        showChannels(names, streams);
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Could not load playlist. Check the URL and connection.",
                        Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void showChannels(ArrayList<String> names,
                              ArrayList<String> streams) {
        new AlertDialog.Builder(this)
                .setTitle("Live TV (" + names.size() + " channels)")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    Intent intent = new Intent(this, PlayerActivity.class);
                    intent.putExtra("channel_name", names.get(which));
                    intent.putExtra("stream_url", streams.get(which));
                    startActivity(intent);
                })
                .setNegativeButton("Close", null)
                .show();
    }
}
