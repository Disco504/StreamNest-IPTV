package com.streamnest.iptv;

import android.util.Xml;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private boolean showingLiveTv = false;
    private boolean showingTvGuide = false;

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

        showHomeScreen();
        restoreSavedConnection();
    }

    private void restoreSavedConnection() {
        android.content.SharedPreferences prefs =
                getPreferences(MODE_PRIVATE);

        String type = prefs.getString("last_connection", "");

        if ("xtream".equals(type)) {
            String server = prefs.getString("xtream_server", "");
            String username = prefs.getString("xtream_username", "");
            String password = prefs.getString("xtream_password", "");

            if (!server.isEmpty() && !username.isEmpty()
                    && !password.isEmpty()) {
                loadXtreamChannels(server, username, password,
                        prefs.getBoolean("launch_live_tv_on_boot", true));
            } else {
                Toast.makeText(this,
                        "Please log in to Xtream Codes once more to save your password.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        String playlistUrl = prefs.getString("playlist_url", "");
        if ("m3u".equals(type)
                || (type.isEmpty() && !playlistUrl.isEmpty())) {
            if (!playlistUrl.isEmpty()) {
                loadPlaylist(playlistUrl,
                        prefs.getBoolean("launch_live_tv_on_boot", true));
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (showingLiveTv || showingTvGuide) {
            showHomeScreen();
        } else {
            super.onBackPressed();
        }
    }

    private void showHomeScreen() {
        showingLiveTv = false;
        showingTvGuide = false;
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
                showConnectionDialog();
            } else if ("SETTINGS".equals(title)) {
                showSettingsDialog();
            } else if ("TV GUIDE".equals(title)) {
                showTvGuide();
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


    private void showSettingsDialog() {
        android.content.SharedPreferences prefs =
                getPreferences(MODE_PRIVATE);
        String[] options = {
                "Launch Live TV on app boot",
                "Launch Live TV after playlist change"
        };
        boolean[] checked = {
                prefs.getBoolean("launch_live_tv_on_boot", true),
                prefs.getBoolean("launch_live_tv_on_change", true)
        };

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setMultiChoiceItems(options, checked,
                        (dialog, which, isChecked) -> {
                            String key = which == 0
                                    ? "launch_live_tv_on_boot"
                                    : "launch_live_tv_on_change";
                            prefs.edit().putBoolean(key, isChecked).apply();
                        })
                .setPositiveButton("XMLTV EPG URL",
                        (dialog, which) -> showEpgDialog())
                .setNegativeButton("Done", null)
                .show();
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


    private void showConnectionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Connect Playlist")
                .setItems(new String[]{
                        "M3U Playlist",
                        "Xtream Codes Login"
                }, (dialog, which) -> {
                    if (which == 0) {
                        showPlaylistDialog();
                    } else {
                        showXtreamDialog();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showXtreamDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(8);
        form.setPadding(pad, pad, pad, pad);

        EditText server = new EditText(this);
        server.setSingleLine(true);
        server.setHint("Server URL (https://host:port)");
        server.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);
        server.setText(getPreferences(MODE_PRIVATE)
                .getString("xtream_server", ""));
        form.addView(server);

        EditText username = new EditText(this);
        username.setSingleLine(true);
        username.setHint("Username");
        username.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        username.setText(getPreferences(MODE_PRIVATE)
                .getString("xtream_username", ""));
        form.addView(username);

        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setHint("Password");
        password.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(password);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);

        AlertDialog loginDialog = new AlertDialog.Builder(this)
                .setTitle("Xtream Codes Login")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Login", (dialog, which) -> {
                    String base = server.getText().toString().trim();
                    String user = username.getText().toString().trim();
                    String pass = password.getText().toString();

                    while (base.endsWith("/")) {
                        base = base.substring(0, base.length() - 1);
                    }
                    if (base.endsWith("/player_api.php")) {
                        base = base.substring(0,
                                base.length() - "/player_api.php".length());
                    }

                    if ((!base.startsWith("https://")
                            && !base.startsWith("http://"))
                            || user.isEmpty() || pass.isEmpty()) {
                        Toast.makeText(this,
                                "Enter a valid server URL, username and password.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    getPreferences(MODE_PRIVATE).edit()
                            .putString("xtream_server", base)
                            .putString("xtream_username", user)
                            .putString("xtream_password", pass)
                            .putString("last_connection", "xtream")
                            .apply();

                    loadXtreamChannels(base, user, pass);
                })
                .create();
        loginDialog.setOnShowListener(v -> {
            if (loginDialog.getWindow() != null) {
                loginDialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                );
            }
        });
        loginDialog.show();
    }

    private String apiEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private JSONArray getXtreamArray(String urlText) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)
                    new URL(urlText).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(25000);
            connection.setRequestProperty(
                    "User-Agent", "StreamNestIPTV/1.0");

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new java.io.IOException(
                        "Provider returned HTTP " + status);
            }

            try (InputStream input = connection.getInputStream();
                 java.io.ByteArrayOutputStream output =
                         new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                return new JSONArray(
                        new String(output.toByteArray(),
                                StandardCharsets.UTF_8));
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void loadXtreamChannels(
            String base, String username, String password) {
        boolean launchAfterLoad = getPreferences(MODE_PRIVATE)
                .getBoolean("launch_live_tv_on_change", true);
        loadXtreamChannels(base, username, password, launchAfterLoad);
    }

    private void loadXtreamChannels(
            String base, String username, String password,
            boolean launchAfterLoad) {
        Toast.makeText(this, "Connecting to provider...",
                Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            ArrayList<Channel> loaded = new ArrayList<>();
            String failure = null;

            try {
                String api = base + "/player_api.php?username="
                        + apiEncode(username) + "&password="
                        + apiEncode(password);

                JSONArray categories = getXtreamArray(
                        api + "&action=get_live_categories");

                Map<String, String> groupNames = new HashMap<>();
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject item = categories.getJSONObject(i);
                    String id = item.optString("category_id", "");
                    String name = item.optString(
                            "category_name", "Uncategorized");
                    if (!id.isEmpty()) {
                        groupNames.put(id, name);
                    }
                }

                JSONArray streams = getXtreamArray(
                        api + "&action=get_live_streams");

                for (int i = 0; i < streams.length(); i++) {
                    JSONObject item = streams.getJSONObject(i);
                    String id = item.optString("stream_id", "");
                    if (id.isEmpty()) {
                        continue;
                    }

                    String name = item.optString("name", "Channel");
                    String categoryId =
                            item.optString("category_id", "");
                    String group = groupNames.get(categoryId);
                    if (group == null || group.isEmpty()) {
                        group = "Uncategorized";
                    }

                    String extension = item.optString(
                            "container_extension", "ts");
                    if (extension.isEmpty()) {
                        extension = "ts";
                    }

                    String streamUrl = base + "/live/"
                            + Uri.encode(username) + "/"
                            + Uri.encode(password) + "/"
                            + Uri.encode(id) + "."
                            + Uri.encode(extension);

                    String tvgId = item.optString(
                            "epg_channel_id", "");

                    loaded.add(new Channel(
                            name, streamUrl, group, tvgId));
                }
            } catch (Exception e) {
                android.util.Log.e("StreamNest",
                        "Xtream connection failed", e);
                failure = e.getMessage();
            }

            final String error = failure;
            runOnUiThread(() -> {
                if (error != null) {
                    Toast.makeText(this,
                            "Xtream login failed. Check the server, credentials and connection.",
                            Toast.LENGTH_LONG).show();
                } else if (loaded.isEmpty()) {
                    Toast.makeText(this,
                            "Login returned no live channels.",
                            Toast.LENGTH_LONG).show();
                } else {
                    channels = loaded;
                    selectedCategory = "All Channels";
                    if (launchAfterLoad) {
                        showLiveTv();
                    }
                    Toast.makeText(this,
                            "Loaded " + channels.size()
                                    + " live channels",
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
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
                            .putString("playlist_url", url)
                            .putString("last_connection", "m3u")
                            .apply();

                    Toast.makeText(this, "Loading playlist...",
                            Toast.LENGTH_SHORT).show();
                    loadPlaylist(url);
                })
                .show();
    }

    private void loadPlaylist(String playlistUrl) {
        boolean launchAfterLoad = getPreferences(MODE_PRIVATE)
                .getBoolean("launch_live_tv_on_change", true);
        loadPlaylist(playlistUrl, launchAfterLoad);
    }

    private void loadPlaylist(String playlistUrl, boolean launchAfterLoad) {
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
                        if (launchAfterLoad) {
                        showLiveTv();
                    }
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
        showingLiveTv = true;
        showingTvGuide = false;
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
        showHomeScreen();
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


    private static class EpgProgram {
        String channelId;
        String title;
        long startMillis;
        long stopMillis;

        EpgProgram(String channelId, String title, long startMillis, long stopMillis) {
            this.channelId = channelId;
            this.title = title;
            this.startMillis = startMillis;
            this.stopMillis = stopMillis;
        }
    }

    private void showTvGuide() {
        showingTvGuide = true;
        String epgUrl = getPreferences(MODE_PRIVATE).getString("epg_url", "");
        if (epgUrl.trim().isEmpty()) {
            Toast.makeText(this, "Open Settings to enter your XMLTV EPG URL.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (channels.isEmpty()) {
            Toast.makeText(this, "Load your M3U playlist first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        final Map<String, Channel> channelMap = new HashMap<>();
        for (Channel channel : channels) {
            if (channel.tvgId != null && !channel.tvgId.trim().isEmpty()) {
                channelMap.put(channel.tvgId.trim(), channel);
            }
        }

        screen.removeAllViews();
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.addView(label("TV GUIDE", 25));

        TextView status = label("Downloading programme listings...", 15);
        screen.addView(status);

        TextView back = label("← BACK TO HOME", 16);
        back.setFocusable(true);
        back.setOnClickListener(v -> showHomeScreen());
        screen.addView(back);

        ScrollView scroll = new ScrollView(this);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        Thread worker = new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(epgUrl.trim());
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "StreamNestIPTV/1.0");

                ArrayList<EpgProgram> programs = new ArrayList<>();
                try (InputStream input = connection.getInputStream()) {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setInput(input, "UTF-8");

                    String currentChannel = null;
                    String currentTitle = null;
                    long currentStart = 0;
                    long currentStop = 0;
                    int event = parser.getEventType();

                    while (event != XmlPullParser.END_DOCUMENT
                            && programs.size() < 20000) {
                        if (event == XmlPullParser.START_TAG
                                && "programme".equals(parser.getName())) {
                            currentChannel = parser.getAttributeValue(null, "channel");
                            currentTitle = null;
                            currentStart = parseXmltvTime(
                                    parser.getAttributeValue(null, "start"));
                            currentStop = parseXmltvTime(
                                    parser.getAttributeValue(null, "stop"));
                        } else if (event == XmlPullParser.START_TAG
                                && "title".equals(parser.getName())
                                && currentChannel != null) {
                            currentTitle = parser.nextText();
                        } else if (event == XmlPullParser.END_TAG
                                && "programme".equals(parser.getName())) {
                            if (currentChannel != null && currentTitle != null
                                    && currentStart > 0 && currentStop > currentStart) {
                                programs.add(new EpgProgram(currentChannel,
                                        currentTitle.trim(), currentStart, currentStop));
                            }
                            currentChannel = null;
                            currentTitle = null;
                        }
                        event = parser.next();
                    }
                }

                final ArrayList<EpgProgram> loaded = programs;
                runOnUiThread(() ->
                        renderGuidePrograms(loaded, channelMap, status, results));
            } catch (Exception e) {
                final String message = e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> status.setText(
                        "Could not load the EPG: " + message
                        + "\nCheck the saved URL and internet connection."));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
        worker.start();
    }

    private long parseXmltvTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        String[] patterns = {"yyyyMMddHHmmss Z", "yyyyMMddHHmmss"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                Date date = format.parse(value.trim());
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        return 0;
    }

    private void renderGuidePrograms(List<EpgProgram> programs,
            Map<String, Channel> channelMap, TextView status, LinearLayout results) {
        results.removeAllViews();
        long now = System.currentTimeMillis();
        long horizon = now + 24L * 60L * 60L * 1000L;
        int matched = 0;
        ArrayList<EpgProgram> visible = new ArrayList<>();

        for (EpgProgram program : programs) {
            if (channelMap.containsKey(program.channelId)) {
                matched++;
                if (program.stopMillis > now && program.startMillis < horizon) {
                    visible.add(program);
                }
            }
        }

        Collections.sort(visible, Comparator.comparingLong(p -> p.startMillis));

        if (matched == 0) {
            status.setText("Downloaded " + programs.size()
                    + " programmes, but none matched your M3U tvg-id values."
                    + "\nThe playlist channel IDs must match the XMLTV channel IDs.");
            return;
        }

        if (visible.isEmpty()) {
            status.setText("Matched channels, but no current or upcoming programmes "
                    + "were found in the next 24 hours.");
            return;
        }

        status.setText("Guide loaded: " + visible.size()
                + " current/upcoming listings. Showing up to 100.");

        SimpleDateFormat clock = new SimpleDateFormat("h:mm a", Locale.getDefault());
        int shown = 0;
        for (EpgProgram program : visible) {
            if (shown >= 100) {
                break;
            }
            Channel channel = channelMap.get(program.channelId);
            if (channel == null) {
                continue;
            }

            String times = clock.format(new Date(program.startMillis)) + " – "
                    + clock.format(new Date(program.stopMillis));
            String text = channel.name + "\n" + times + "\n"
                    + (program.title.isEmpty() ? "(Untitled programme)" : program.title);

            TextView item = label(text, 15);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            item.setBackgroundColor(PANEL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, dp(3), 0, dp(3));
            results.addView(item, params);
            shown++;
        }
    }

    private void playChannel(Channel channel) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("channel_name", channel.name);
        intent.putExtra("stream_url", channel.url);
        startActivity(intent);
    }
}
