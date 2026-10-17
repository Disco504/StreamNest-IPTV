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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public class MainActivity extends Activity {

    private static final int NAVY = Color.rgb(15, 20, 35);
    private static final int PANEL = Color.rgb(27, 36, 54);
    private static final int BLUE = Color.rgb(25, 115, 190);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(175, 190, 210);

    private LinearLayout screen;
    private LinearLayout categoryPanel;
    private LinearLayout channelPanel;
    private ArrayList<Channel> channels = new ArrayList<>();
    private Set<String> favorites = new LinkedHashSet<>();
    private String selectedCategory = "All Channels";

    private static class Channel {
        String name;
        String url;
        String group;
        String tvgId;

        Channel(String name, String url, String group, String tvgId) {
            this.name = name;
            this.url = url;
            this.group = group;
            this.tvgId = tvgId;
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(WHITE);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        return view;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);

        favorites = new LinkedHashSet<>(getPreferences(MODE_PRIVATE)
                .getStringSet("favorite_urls", Collections.emptySet()));

        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(NAVY);
        screen.setPadding(dp(16), dp(8), dp(16), dp(12));

        TextView title = label("StreamNest IPTV", 28);
        title.setTextColor(Color.rgb(95, 190, 255));
        title.setTypeface(null, Typeface.BOLD);
        screen.addView(title);
        screen.addView(label("Your entertainment, organized.", 14));

        ScrollView homeScroll = new ScrollView(this);
        LinearLayout homeMenu = new LinearLayout(this);
        homeMenu.setOrientation(LinearLayout.VERTICAL);
        homeScroll.addView(homeMenu);

        addHomeSection(homeMenu, "LIVE TV",
                "Browse channel categories and watch live");
        addHomeSection(homeMenu, "TV GUIDE",
                "Browse programs and schedules");
        addHomeSection(homeMenu, "MOVIES",
                "Browse your video-on-demand library");
        addHomeSection(homeMenu, "TV SERIES",
                "Explore seasons and episodes");
        addHomeSection(homeMenu, "CATCH-UP",
                "Replay programs where supported");
        addHomeSection(homeMenu, "SETTINGS",
                "Configure your playlist and preferences");

        screen.addView(homeScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(screen);
        homeMenu.getChildAt(0).requestFocus();
    }

    private void addHomeSection(LinearLayout menu, String title,
                                String subtitle) {
        TextView item = label(title + "\n" + subtitle, 16);
        item.setBackgroundColor(PANEL);
        item.setFocusable(true);
        item.setClickable(true);
        item.setOnFocusChangeListener((v, focused) ->
                v.setBackgroundColor(focused ? BLUE : PANEL));

        item.setOnClickListener(v -> {
            if ("LIVE TV".equals(title)) {
                showPlaylistDialog();
            } else if ("SETTINGS".equals(title)) {
                showEpgDialog();
            } else if ("TV GUIDE".equals(title)) {
                String epgUrl = getPreferences(MODE_PRIVATE)
                        .getString("epg_url", "");
                String message = epgUrl.isEmpty()
                        ? "Open Settings to enter your XMLTV EPG URL."
                        : "EPG URL saved. Program guide is the next step.";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, title + " — coming next",
                        Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(7), 0, 0);
        menu.addView(item, params);
    }


    private void showEpgDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Enter XMLTV EPG URL");
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(getPreferences(MODE_PRIVATE)
                .getString("epg_url", ""));

        new AlertDialog.Builder(this)
                .setTitle("TV Guide (XMLTV)")
                .setMessage("Enter the separate EPG/XMLTV URL from your provider.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()
                            && !url.startsWith("http://")
                            && !url.startsWith("https://")) {
                        Toast.makeText(this, "Enter a valid URL",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    getPreferences(MODE_PRIVATE).edit()
                            .putString("epg_url", url)
                            .apply();
                    Toast.makeText(this, "EPG URL saved",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
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
            ArrayList<Channel> loaded = new ArrayList<>();
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
                    String pendingGroup = "Uncategorized";
                    String pendingTvgId = "";

                    while ((line = reader.readLine()) != null) {
                        line = line.trim();

                        if (line.startsWith("#EXTINF:")) {
                            int comma = line.lastIndexOf(',');
                            pendingName = comma >= 0
                                    ? line.substring(comma + 1).trim()
                                    : "Channel";
                            pendingGroup = readAttribute(line, "group-title");
                            pendingTvgId = readAttribute(line, "tvg-id");
                            if (pendingGroup.isEmpty()) {
                                pendingGroup = "Uncategorized";
                            }
                        } else if (!line.isEmpty()
                                && !line.startsWith("#")
                                && pendingName != null) {
                            loaded.add(new Channel(
                                    pendingName, line, pendingGroup, pendingTvgId));
                            pendingName = null;
                            pendingGroup = "Uncategorized";
                            pendingTvgId = "";
                        }
                    }
                }

                runOnUiThread(() -> {
                    if (loaded.isEmpty()) {
                        Toast.makeText(this,
                                "No channels found. Check the playlist URL.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        channels = loaded;
                        selectedCategory = "All Channels";
                        showLiveTv();
                        Toast.makeText(this,
                                "Loaded " + channels.size() + " channels",
                                Toast.LENGTH_SHORT).show();
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

    private String readAttribute(String line, String attribute) {
        String marker = attribute + "=\"";
        int start = line.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = line.indexOf('"', start);
        if (end < 0) {
            return "";
        }
        return line.substring(start, end).trim();
    }

    private void showLiveTv() {
        screen.removeAllViews();

        TextView title = label("StreamNest  /  LIVE TV", 24);
        title.setTextColor(Color.rgb(95, 190, 255));
        title.setTypeface(null, Typeface.BOLD);
        screen.addView(title);

        LinearLayout panels = new LinearLayout(this);
        panels.setOrientation(LinearLayout.HORIZONTAL);

        categoryPanel = new LinearLayout(this);
        categoryPanel.setOrientation(LinearLayout.VERTICAL);
        categoryPanel.setBackgroundColor(PANEL);

        ScrollView categoryScroll = new ScrollView(this);
        categoryScroll.addView(categoryPanel);
        panels.addView(categoryScroll, new LinearLayout.LayoutParams(
                dp(165), LinearLayout.LayoutParams.MATCH_PARENT));

        channelPanel = new LinearLayout(this);
        channelPanel.setOrientation(LinearLayout.VERTICAL);
        channelPanel.setPadding(dp(8), 0, 0, 0);

        ScrollView channelScroll = new ScrollView(this);
        channelScroll.addView(channelPanel);
        panels.addView(channelScroll, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        screen.addView(panels, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        renderCategories();
        renderChannels();

        TextView back = label("‹  Back to Home", 15);
        back.setFocusable(true);
        back.setClickable(true);
        back.setOnClickListener(v -> onCreateHomeAgain());
        screen.addView(back);
    }

    private void onCreateHomeAgain() {
        recreate();
    }

    private void renderCategories() {
        categoryPanel.removeAllViews();

        addCategory("All Channels", channels.size());

        addCategory("Favorites", favorites.size());

        TreeSet<String> groups = new TreeSet<>();
        for (Channel channel : channels) {
            groups.add(channel.group);
        }
        for (String group : groups) {
            int count = 0;
            for (Channel channel : channels) {
                if (channel.group.equals(group)) {
                    count++;
                }
            }
            addCategory(group, count);
        }
    }

    private void addCategory(String category, int count) {
        TextView item = label(category + "\n" + count + " channels", 14);
        item.setFocusable(true);
        item.setClickable(true);
        item.setBackgroundColor(category.equals(selectedCategory)
                ? BLUE : PANEL);

        item.setOnFocusChangeListener((v, focused) -> {
            if (category.equals(selectedCategory)) {
                v.setBackgroundColor(BLUE);
            } else {
                v.setBackgroundColor(focused
                        ? Color.rgb(48, 65, 90) : PANEL);
            }
        });

        item.setOnClickListener(v -> {
            selectedCategory = category;
            renderCategories();
            renderChannels();
        });

        categoryPanel.addView(item, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void renderChannels() {
        channelPanel.removeAllViews();

        TextView heading = label(selectedCategory, 19);
        heading.setTypeface(null, Typeface.BOLD);
        channelPanel.addView(heading);

        int shown = 0;

        for (Channel channel : channels) {
            boolean isFavorite = favorites.contains(channel.url);
            boolean matches;

            if ("All Channels".equals(selectedCategory)) {
                matches = true;
            } else if ("Favorites".equals(selectedCategory)) {
                matches = isFavorite;
            } else {
                matches = selectedCategory.equals(channel.group);
            }

            if (!matches) {
                continue;
            }

            shown++;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(PANEL);
            row.setFocusable(true);
            row.setClickable(true);

            TextView name = label(shown + ".  " + channel.name, 15);
            name.setSingleLine(true);
            name.setFocusable(false);
            row.addView(name, new LinearLayout.LayoutParams(
                    0, dp(54), 1f));

            TextView star = label(isFavorite ? "★" : "☆", 23);
            star.setTextColor(isFavorite
                    ? Color.rgb(255, 200, 65) : MUTED);
            star.setGravity(Gravity.CENTER);
            star.setFocusable(true);
            star.setContentDescription(isFavorite
                    ? "Remove favorite" : "Add favorite");
            row.addView(star, new LinearLayout.LayoutParams(
                    dp(48), dp(54)));

            row.setOnFocusChangeListener((v, focused) ->
                    v.setBackgroundColor(focused ? BLUE : PANEL));

            row.setOnClickListener(v -> playChannel(channel));

            star.setOnClickListener(v -> {
                if (favorites.contains(channel.url)) {
                    favorites.remove(channel.url);
                } else {
                    favorites.add(channel.url);
                }

                getPreferences(MODE_PRIVATE).edit()
                        .putStringSet("favorite_urls",
                                new LinkedHashSet<>(favorites))
                        .apply();

                renderCategories();
                renderChannels();
            });

            LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, dp(3), 0, dp(3));
            channelPanel.addView(row, rowParams);
        }

        if (shown == 0) {
            channelPanel.addView(label(
                    "No channels in this category.", 15));
        }
    }

    private void playChannel(Channel channel) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("channel_name", channel.name);
        intent.putExtra("stream_url", channel.url);
        startActivity(intent);
    }
}
