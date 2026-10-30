package com.streamnest.iptv;

import android.util.Xml;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

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
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public class MainActivity extends Activity {
    private boolean isTelevisionDevice() {
        int uiMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_TYPE_MASK;
        return getPackageManager().hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_LEANBACK)
                || uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private boolean isPhoneLayout() {
        return !isTelevisionDevice()
                && getResources().getConfiguration().smallestScreenWidthDp < 600;
    }

    private final ExecutorService logoExecutor = Executors.newFixedThreadPool(4);

    private static final int NAVY = Color.rgb(15, 20, 35);
    private static final int PANEL = Color.rgb(27, 36, 54);
    private static final int BLUE = Color.rgb(25, 115, 190);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(175, 190, 210);

    private LinearLayout screen;
    private LinearLayout categoryPanel;
    private TextView phoneCategoryButton;
    private LinearLayout channelPanel;
    private ArrayList<Channel> channels = new ArrayList<>();
    private Set<String> favorites = new LinkedHashSet<>();
    private String selectedCategory = "All Channels";
    private boolean showingLiveTv = false;
    private boolean showingTvGuide = false;
    private ScrollView guideCategoryScrollView;

    private ExoPlayer previewPlayer;
    private PlayerView previewPlayerView;
    private String previewChannelUrl;
    private Channel guidePreviewChannel;

    private static class Channel {
        String name;
        String url;
        String group;
        String tvgId;
        String logoUrl;

        Channel(String name, String url, String group, String tvgId) {
            this(name, url, group, tvgId, "");
        }

        Channel(String name, String url, String group, String tvgId, String logoUrl) {
            this.name = name;
            this.url = url;
            this.group = group;
            this.tvgId = tvgId;
            this.logoUrl = logoUrl;
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
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (showingTvGuide
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && guideCategoryScrollView != null) {
            if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    && guideCategoryScrollView.getVisibility() != android.view.View.VISIBLE) {
                guideCategoryScrollView.setVisibility(android.view.View.VISIBLE);
                return true;
            }
            if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                    && guideCategoryScrollView.getVisibility() == android.view.View.VISIBLE) {
                guideCategoryScrollView.setVisibility(android.view.View.GONE);
                return true;
            }
        }
        if (showingTvGuide
                && event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.isLongPress()
                && guidePreviewChannel != null) {
            playChannel(guidePreviewChannel);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (showingLiveTv || showingTvGuide) {
            showHomeScreen();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (showingTvGuide && previewPlayerView != null
                && guidePreviewChannel != null) {
            playGuidePreview(guidePreviewChannel);
        }
    }


    private void showMovies() {
        String base = getPreferences(MODE_PRIVATE).getString("xtream_server", "");
        String user = getPreferences(MODE_PRIVATE).getString("xtream_username", "");
        String pass = getPreferences(MODE_PRIVATE).getString("xtream_password", "");

        if (base.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Movies")
                    .setMessage("Connect your Xtream Codes account in LIVE TV first.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        releaseGuidePreview();
        showingLiveTv = false;
        showingTvGuide = false;

        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(NAVY);
        screen.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView heading = label("MOVIE CATEGORIES", 24);
        heading.setTextColor(Color.rgb(95, 190, 255));
        heading.setTypeface(null, Typeface.BOLD);
        screen.addView(heading);

        TextView status = label("Loading categories...", 16);
        screen.addView(status);

        ScrollView scroll = new ScrollView(this);
        LinearLayout categoryList = new LinearLayout(this);
        categoryList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(categoryList);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView back = label("BACK TO HOME", 16);
        back.setFocusable(true);
        back.setClickable(true);
        back.setPadding(dp(8), dp(12), dp(8), dp(12));
        back.setOnClickListener(v -> showHomeScreen());
        screen.addView(back);
        setContentView(screen);

        new Thread(() -> {
            ArrayList<String[]> categories = new ArrayList<>();
            String failure = null;

            try {
                String api = base + "/player_api.php?username="
                        + apiEncode(user) + "&password=" + apiEncode(pass);

                JSONArray result = getXtreamArray(
                        api + "&action=get_vod_categories");

                for (int i = 0; i < result.length(); i++) {
                    JSONObject item = result.getJSONObject(i);
                    String id = item.optString("category_id", "");
                    String name = item.optString("category_name", "Uncategorized");

                    if (!id.isEmpty()) {
                        categories.add(new String[]{id, name});
                    }
                }

                java.util.Collections.sort(categories,
                        (a, b) -> a[1].compareToIgnoreCase(b[1]));

            } catch (Exception e) {
                failure = e.getMessage() == null
                        ? "Could not load movie categories." : e.getMessage();
            }

            final String error = failure;
            runOnUiThread(() -> {
                categoryList.removeAllViews();

                if (error != null) {
                    status.setText("Unable to load categories: " + error);
                    return;
                }

                status.setText(categories.size() + " categories");

                if (categories.isEmpty()) {
                    categoryList.addView(label(
                            "No movie categories were returned by your provider.", 16));
                    return;
                }

                for (String[] category : categories) {
                    TextView item = label(category[1] + "  \u25B6", 18);
                    item.setTextColor(Color.WHITE);
                    item.setBackgroundColor(PANEL);
                    item.setFocusable(true);
                    item.setClickable(true);
                    item.setPadding(dp(12), dp(14), dp(12), dp(14));

                    LinearLayout.LayoutParams params =
                            new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(0, dp(3), 0, dp(3));

                    item.setOnFocusChangeListener((v, focused) ->
                            v.setBackgroundColor(focused ? BLUE : PANEL));
                    item.setOnClickListener(v ->
                            showMoviesInCategory(
                                    category[0], category[1], base, user, pass));

                    categoryList.addView(item, params);
                }

                if (categoryList.getChildCount() > 0) {
                    categoryList.getChildAt(0).requestFocus();
                }
            });
        }).start();
    }

    private void showMoviesInCategory(
            String categoryId, String categoryName,
            String base, String user, String pass) {

        releaseGuidePreview();
        showingLiveTv = false;
        showingTvGuide = false;

        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(NAVY);
        screen.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView heading = label(categoryName, 22);
        heading.setTextColor(Color.rgb(95, 190, 255));
        heading.setTypeface(null, Typeface.BOLD);
        screen.addView(heading);

        TextView status = label("Loading movies...", 16);
        screen.addView(status);

        ScrollView listScroll = new ScrollView(this);
        LinearLayout movieList = new LinearLayout(this);
        movieList.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(movieList);
        screen.addView(listScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView back = label("BACK TO CATEGORIES", 16);
        back.setFocusable(true);
        back.setClickable(true);
        back.setPadding(dp(8), dp(12), dp(8), dp(12));
        back.setOnClickListener(v -> showMovies());
        screen.addView(back);
        setContentView(screen);

        new Thread(() -> {
            ArrayList<Channel> loaded = new ArrayList<>();
            String failure = null;

            try {
                String api = base + "/player_api.php?username="
                        + apiEncode(user) + "&password=" + apiEncode(pass);

                JSONArray streams = getXtreamArray(
                        api + "&action=get_vod_streams&category_id="
                                + apiEncode(categoryId));

                for (int i = 0; i < streams.length(); i++) {
                    JSONObject item = streams.getJSONObject(i);
                    String id = item.optString("stream_id", "");
                    if (id.isEmpty()) continue;

                    String extension = item.optString("container_extension", "mp4");

                    String url = base + "/movie/" + Uri.encode(user) + "/"
                            + Uri.encode(pass) + "/" + Uri.encode(id) + "."
                            + Uri.encode(extension);

                    loaded.add(new Channel(
                            item.optString("name", "Untitled movie"),
                            url,
                            categoryName,
                            "",
                            item.optString("stream_icon", "")));
                }

                java.util.Collections.sort(loaded,
                        (a, b) -> a.name.compareToIgnoreCase(b.name));

            } catch (Exception e) {
                failure = e.getMessage() == null
                        ? "Could not load movies." : e.getMessage();
            }

            final String error = failure;
            runOnUiThread(() -> {
                status.setText(error == null
                        ? loaded.size() + " movies" : "Unable to load movies: " + error);
                movieList.removeAllViews();

                if (error != null) {
                    movieList.addView(label(
                            "Check your Xtream login and connection.", 14));
                    return;
                }

                if (loaded.isEmpty()) {
                    movieList.addView(label(
                            "No movies were returned in this category.", 16));
                    return;
                }

                for (Channel movie : loaded) {
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setFocusable(true);
                    row.setClickable(true);
                    row.setBackgroundColor(PANEL);
                    row.setPadding(dp(8), dp(6), dp(8), dp(6));

                    android.widget.ImageView poster =
                            new android.widget.ImageView(this);
                    poster.setLayoutParams(
                            new LinearLayout.LayoutParams(dp(64), dp(88)));
                    poster.setScaleType(
                            android.widget.ImageView.ScaleType.CENTER_CROP);

                    if (!movie.logoUrl.isEmpty()) {
                        loadChannelLogo(poster, movie.logoUrl);
                    }
                    row.addView(poster);

                    TextView name = label(movie.name, 16);
                    name.setPadding(dp(12), dp(8), dp(8), dp(8));
                    row.addView(name, new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                    row.setOnFocusChangeListener((v, focused) ->
                            v.setBackgroundColor(focused ? BLUE : PANEL));
                    row.setOnClickListener(v -> playVodItem(movie));

                    LinearLayout.LayoutParams rowParams =
                            new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT);
                    rowParams.setMargins(0, dp(3), 0, dp(3));
                    movieList.addView(row, rowParams);
                }

                if (movieList.getChildCount() > 0) {
                    movieList.getChildAt(0).requestFocus();
                }
            });
        }).start();
    }

    private void playVodItem(Channel item) {
        if (item == null || item.url == null || item.url.trim().isEmpty()) return;
        releaseGuidePreviewPlayer();
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("channel_name", item.name);
        intent.putExtra("stream_url", item.url);
        startActivity(intent);
    }


    private void showSeries() {
        String base = getPreferences(MODE_PRIVATE).getString("xtream_server", "");
        String user = getPreferences(MODE_PRIVATE).getString("xtream_username", "");
        String pass = getPreferences(MODE_PRIVATE).getString("xtream_password", "");

        if (base.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("TV Series")
                    .setMessage("Connect your Xtream Codes account in LIVE TV first.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        releaseGuidePreview();
        showingLiveTv = false;
        showingTvGuide = false;
        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(NAVY);
        screen.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView heading = label("TV SERIES", 24);
        heading.setTextColor(Color.rgb(95, 190, 255));
        heading.setTypeface(null, Typeface.BOLD);
        screen.addView(heading);

        TextView status = label("Loading categories...", 16);
        screen.addView(status);
        ScrollView scroll = new ScrollView(this);
        LinearLayout categoryList = new LinearLayout(this);
        categoryList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(categoryList);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView back = label("BACK TO HOME", 16);
        back.setFocusable(true);
        back.setClickable(true);
        back.setPadding(dp(8), dp(12), dp(8), dp(12));
        back.setOnClickListener(v -> showHomeScreen());
        screen.addView(back);
        setContentView(screen);

        new Thread(() -> {
            ArrayList<String[]> categories = new ArrayList<>();
            String failure = null;
            try {
                String api = base + "/player_api.php?username="
                        + apiEncode(user) + "&password=" + apiEncode(pass);
                JSONArray result = getXtreamArray(
                        api + "&action=get_series_categories");
                for (int i = 0; i < result.length(); i++) {
                    JSONObject item = result.getJSONObject(i);
                    String id = item.optString("category_id", "");
                    String name = item.optString("category_name", "Uncategorized");
                    if (!id.isEmpty()) categories.add(new String[]{id, name});
                }
                java.util.Collections.sort(categories,
                        (a, b) -> a[1].compareToIgnoreCase(b[1]));
            } catch (Exception e) {
                failure = e.getMessage() == null
                        ? "Could not load series categories." : e.getMessage();
            }
            final String error = failure;
            runOnUiThread(() -> {
                categoryList.removeAllViews();
                if (error != null) {
                    status.setText("Unable to load categories: " + error);
                    return;
                }
                status.setText(categories.size() + " categories");
                if (categories.isEmpty()) {
                    categoryList.addView(label(
                            "No TV series categories were returned.", 16));
                    return;
                }
                for (String[] category : categories) {
                    TextView item = label(category[1] + "  \u25B6", 18);
                    item.setTextColor(Color.WHITE);
                    item.setBackgroundColor(PANEL);
                    item.setFocusable(true);
                    item.setClickable(true);
                    item.setPadding(dp(12), dp(14), dp(12), dp(14));
                    item.setOnFocusChangeListener((v, focused) ->
                            v.setBackgroundColor(focused ? BLUE : PANEL));
                    item.setOnClickListener(v -> showSeriesInCategory(
                            category[0], category[1], base, user, pass));
                    LinearLayout.LayoutParams params =
                            new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(0, dp(3), 0, dp(3));
                    categoryList.addView(item, params);
                }
                if (categoryList.getChildCount() > 0) {
                    categoryList.getChildAt(0).requestFocus();
                }
            });
        }).start();
    }

    private void showSeriesInCategory(
            String categoryId, String categoryName,
            String base, String user, String pass) {
        releaseGuidePreview();
        showingLiveTv = false;
        showingTvGuide = false;
        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(NAVY);
        screen.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView heading = label(categoryName, 22);
        heading.setTextColor(Color.rgb(95, 190, 255));
        heading.setTypeface(null, Typeface.BOLD);
        screen.addView(heading);
        TextView status = label("Loading shows...", 16);
        screen.addView(status);
        ScrollView listScroll = new ScrollView(this);
        LinearLayout seriesList = new LinearLayout(this);
        seriesList.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(seriesList);
        screen.addView(listScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView back = label("BACK TO CATEGORIES", 16);
        back.setFocusable(true);
        back.setClickable(true);
        back.setPadding(dp(8), dp(12), dp(8), dp(12));
        back.setOnClickListener(v -> showSeries());
        screen.addView(back);
        setContentView(screen);

        new Thread(() -> {
            ArrayList<Channel> loaded = new ArrayList<>();
            String failure = null;
            try {
                String api = base + "/player_api.php?username="
                        + apiEncode(user) + "&password=" + apiEncode(pass);
                JSONArray streams = getXtreamArray(
                        api + "&action=get_series&category_id="
                                + apiEncode(categoryId));
                for (int i = 0; i < streams.length(); i++) {
                    JSONObject item = streams.getJSONObject(i);
                    String id = item.optString("series_id", "");
                    if (id.isEmpty()) continue;
                    loaded.add(new Channel(
                            item.optString("name", "Untitled series"),
                            id,
                            categoryName,
                            "",
                            item.optString("cover", "")));
                }
                java.util.Collections.sort(loaded,
                        (a, b) -> a.name.compareToIgnoreCase(b.name));
            } catch (Exception e) {
                failure = e.getMessage() == null
                        ? "Could not load shows." : e.getMessage();
            }
            final String error = failure;
            runOnUiThread(() -> {
                status.setText(error == null
                        ? loaded.size() + " shows"
                        : "Unable to load shows: " + error);
                seriesList.removeAllViews();
                if (error != null) {
                    seriesList.addView(label(
                            "Check your Xtream login and connection.", 14));
                    return;
                }
                if (loaded.isEmpty()) {
                    seriesList.addView(label(
                            "No shows were returned in this category.", 16));
                    return;
                }
                for (Channel series : loaded) {
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setFocusable(true);
                    row.setClickable(true);
                    row.setBackgroundColor(PANEL);
                    row.setPadding(dp(8), dp(6), dp(8), dp(6));

                    android.widget.ImageView poster =
                            new android.widget.ImageView(this);
                    poster.setLayoutParams(
                            new LinearLayout.LayoutParams(dp(64), dp(88)));
                    poster.setScaleType(
                            android.widget.ImageView.ScaleType.CENTER_CROP);
                    if (!series.logoUrl.isEmpty()) {
                        loadChannelLogo(poster, series.logoUrl);
                    }
                    row.addView(poster);
                    TextView name = label(series.name, 16);
                    name.setPadding(dp(12), dp(8), dp(8), dp(8));
                    row.addView(name, new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                    row.setOnFocusChangeListener((v, focused) ->
                            v.setBackgroundColor(focused ? BLUE : PANEL));
                    row.setOnClickListener(v ->
                            showSeriesEpisodes(series, base, user, pass));

                    LinearLayout.LayoutParams rowParams =
                            new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT);
                    rowParams.setMargins(0, dp(3), 0, dp(3));
                    seriesList.addView(row, rowParams);
                }
                if (seriesList.getChildCount() > 0) {
                    seriesList.getChildAt(0).requestFocus();
                }
            });
        }).start();
    }

    private void showSeriesEpisodes(Channel series, String base, String user, String pass) {
        releaseGuidePreview();
        showingLiveTv = false;
        showingTvGuide = false;

        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(NAVY);
        screen.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView heading = label(series.name, 22);
        heading.setTextColor(Color.rgb(95, 190, 255));
        heading.setTypeface(null, Typeface.BOLD);
        screen.addView(heading);

        TextView status = label("Loading seasons and episodes...", 15);
        screen.addView(status);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView back = label("BACK TO SERIES", 16);
        back.setFocusable(true);
        back.setClickable(true);
        back.setPadding(dp(8), dp(12), dp(8), dp(12));
        back.setOnClickListener(v -> showSeries());
        screen.addView(back);
        setContentView(screen);

        new Thread(() -> {
            String failure = null;
            ArrayList<String> seasonLabels = new ArrayList<>();
            ArrayList<Channel> episodes = new ArrayList<>();

            try {
                String api = base + "/player_api.php?username="
                        + apiEncode(user) + "&password=" + apiEncode(pass)
                        + "&action=get_series_info&series_id=" + apiEncode(series.url);
                JSONObject info = new JSONObject(getSeriesInfoText(api));

                JSONArray episodeGroups = info.optJSONArray("episodes");
                JSONObject episodeObject = info.optJSONObject("episodes");

                if (episodeGroups != null) {
                    for (int i = 0; i < episodeGroups.length(); i++) {
                        Object value = episodeGroups.opt(i);
                        if (value instanceof JSONObject) {
                            JSONObject ep = (JSONObject) value;
                            addSeriesEpisode(ep, series, episodes);
                        }
                    }
                } else if (episodeObject != null) {
                    java.util.Iterator<String> keys = episodeObject.keys();
                    while (keys.hasNext()) {
                        JSONArray group = episodeObject.optJSONArray(keys.next());
                        if (group == null) continue;
                        for (int i = 0; i < group.length(); i++) {
                            JSONObject ep = group.optJSONObject(i);
                            if (ep != null) addSeriesEpisode(ep, series, episodes);
                        }
                    }
                }

                JSONArray seasons = info.optJSONArray("seasons");
                if (seasons != null) {
                    for (int i = 0; i < seasons.length(); i++) {
                        JSONObject season = seasons.optJSONObject(i);
                        if (season != null) {
                            seasonLabels.add(season.optString("name",
                                    "Season " + season.optString("season_number", String.valueOf(i + 1))));
                        }
                    }
                }
            } catch (Exception e) {
                failure = e.getMessage() == null ? "Could not load episodes." : e.getMessage();
            }

            final String error = failure;
            runOnUiThread(() -> {
                list.removeAllViews();
                if (error != null) {
                    status.setText("Unable to load episodes: " + error);
                    list.addView(label("Check your provider connection and try again.", 14));
                    return;
                }

                status.setText(episodes.size() + " episodes");
                if (episodes.isEmpty()) {
                    list.addView(label("No episodes were returned for this series.", 16));
                    return;
                }

                String currentSeason = "";
                for (Channel episode : episodes) {
                    String season = episode.group == null ? "" : episode.group;
                    if (!season.equals(currentSeason)) {
                        currentSeason = season;
                        TextView seasonTitle = label(
                                season.isEmpty() ? "Episodes" : season, 18);
                        seasonTitle.setTextColor(Color.rgb(95, 190, 255));
                        seasonTitle.setTypeface(null, Typeface.BOLD);
                        seasonTitle.setPadding(dp(4), dp(12), dp(4), dp(6));
                        list.addView(seasonTitle);
                    }

                    TextView row = label(episode.name, 16);
                    row.setFocusable(true);
                    row.setClickable(true);
                    row.setPadding(dp(12), dp(12), dp(8), dp(12));
                    row.setBackgroundColor(PANEL);
                    row.setOnFocusChangeListener((v, focused) ->
                            v.setBackgroundColor(focused ? BLUE : PANEL));
                    row.setOnClickListener(v -> playVodItem(episode));

                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    params.setMargins(0, dp(3), 0, dp(3));
                    list.addView(row, params);
                }
                if (list.getChildCount() > 0) list.getChildAt(1).requestFocus();
            });
        }).start();
    }

    private String getSeriesInfoText(String urlText) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(25000);
            connection.setRequestProperty("User-Agent", "StreamNestIPTV/1.0");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new java.io.IOException("Provider returned HTTP " + status);
            }
            try (InputStream input = connection.getInputStream();
                 java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void addSeriesEpisode(JSONObject ep, Channel series, ArrayList<Channel> episodes) {
        String id = ep.optString("id", "");
        if (id.isEmpty()) id = ep.optString("episode_id", "");
        if (id.isEmpty()) return;

        String extension = ep.optString("container_extension", "mp4");
        String season = ep.optString("season", "");
        String episodeNumber = ep.optString("episode_num", "");
        String title = ep.optString("title", "Episode " + (episodes.size() + 1));
        String name = episodeNumber.isEmpty() ? title : "Episode " + episodeNumber + " — " + title;

        String url = getPreferences(MODE_PRIVATE).getString("xtream_server", "")
                + "/series/" + Uri.encode(getPreferences(MODE_PRIVATE)
                .getString("xtream_username", "")) + "/"
                + Uri.encode(getPreferences(MODE_PRIVATE).getString("xtream_password", ""))
                + "/" + Uri.encode(id) + "." + Uri.encode(extension);

        episodes.add(new Channel(name, url,
                season.isEmpty() ? "Episodes" : "Season " + season, "", ""));
    }

    private void showHomeScreen() {
        releaseGuidePreview();
        if (isPhoneLayout()) {
            setRequestedOrientation(
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        showingLiveTv = false;
        showingTvGuide = false;
        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        TextView status = label("Downloading programme listings...", 15);
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

addHomeSection(homeMenu, "SEARCH",
                "Find live TV channels");
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

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Enter a channel name");

        new AlertDialog.Builder(this)
                .setTitle("Search Channels")
                .setView(input)
                .setPositiveButton("Search", (dialog, which) -> {
                    String query = input.getText().toString().trim()
                            .toLowerCase(java.util.Locale.ROOT);
                    if (query.isEmpty()) {
                        Toast.makeText(this, "Enter a search term.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    ArrayList<Channel> matches = new ArrayList<>();
                    for (Channel channel : channels) {
                        if (channel.name != null && channel.name
                                .toLowerCase(java.util.Locale.ROOT)
                                .contains(query)) {
                            matches.add(channel);
                        }
                    }

                    if (matches.isEmpty()) {
                        new AlertDialog.Builder(this)
                                .setTitle("Search Results")
                                .setMessage("No matching channels found.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }

                    String[] names = new String[matches.size()];
                    for (int i = 0; i < matches.size(); i++) {
                        names[i] = matches.get(i).name;
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Search Results")
                            .setItems(names, (resultDialog, index) -> {
                                showLiveTv();
                                playChannel(matches.get(index));
                            })
                            .setNegativeButton("Close", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
            if ("SETTINGS".equals(title)) {
                showSettingsDialog();
            } else if ("SEARCH".equals(title)) {
                showSearchDialog();
            } else if ("TV GUIDE".equals(title)) {
                showTvGuide();
            } else if ("MOVIES".equals(title)) {
                showMovies();
            } else if ("TV SERIES".equals(title)) {
                showSeries();
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
                .setNeutralButton("Add / Change Playlist",
                        (dialog, which) -> showConnectionDialog())
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

                    String logoUrl = item.optString("stream_icon", "");
                    loaded.add(new Channel(
                            name, streamUrl, group, tvgId, logoUrl));
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
                        showTvGuide();
                        launchLastPlayedChannel();
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
                    String pendingLogoUrl = "";

                    while ((line = reader.readLine()) != null) {
                        line = line.trim();

                        if (line.startsWith("#EXTINF:")) {
                            int comma = line.lastIndexOf(',');
                            pendingName = comma >= 0
                                    ? line.substring(comma + 1).trim()
                                    : "Channel";
                            pendingGroup = readAttribute(line, "group-title");
                            pendingTvgId = readAttribute(line, "tvg-id");
                            pendingLogoUrl = readAttribute(line, "tvg-logo");
                            if (pendingGroup.isEmpty()) {
                                pendingGroup = "Uncategorized";
                            }
                        } else if (!line.isEmpty()
                                && !line.startsWith("#")
                                && pendingName != null) {
                            loaded.add(new Channel(
                                    pendingName, line, pendingGroup, pendingTvgId, pendingLogoUrl));
                            pendingName = null;
                            pendingGroup = "Uncategorized";
                            pendingTvgId = "";
                            pendingLogoUrl = "";
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
                            showTvGuide();
                            launchLastPlayedChannel();
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

        boolean phoneLayout = isPhoneLayout();

        phoneCategoryButton = null;
        if (phoneLayout) {
            phoneCategoryButton = label(
                    "▼  CATEGORIES: " + selectedCategory, 17);
            phoneCategoryButton.setTextColor(WHITE);
            phoneCategoryButton.setBackgroundColor(PANEL);
            phoneCategoryButton.setPadding(dp(12), dp(12), dp(12), dp(12));
            phoneCategoryButton.setFocusable(true);
            phoneCategoryButton.setClickable(true);
            phoneCategoryButton.setOnClickListener(v -> showPhoneCategoryPicker());
            LinearLayout.LayoutParams categoryButtonParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            categoryButtonParams.gravity = android.view.Gravity.START;
            screen.addView(phoneCategoryButton, categoryButtonParams);
        }

        LinearLayout panels = new LinearLayout(this);
        panels.setOrientation(LinearLayout.HORIZONTAL);

        categoryPanel = new LinearLayout(this);
        categoryPanel.setOrientation(LinearLayout.VERTICAL);
        categoryPanel.setBackgroundColor(PANEL);

        ScrollView categoryScroll = new ScrollView(this);
        categoryScroll.addView(categoryPanel);
        panels.addView(categoryScroll, new LinearLayout.LayoutParams(
                phoneLayout ? 0 : dp(165),
                LinearLayout.LayoutParams.MATCH_PARENT));

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

    private void showPhoneCategoryPicker() {
        ArrayList<String> names = new ArrayList<>();
        names.add("All Channels");
        names.add("Favorites");

        TreeSet<String> groups = new TreeSet<>();
        for (Channel channel : channels) {
            groups.add(channel.group);
        }
        names.addAll(groups);

        String[] options = names.toArray(new String[0]);
        int selected = names.indexOf(selectedCategory);

        new AlertDialog.Builder(this)
                .setTitle("Choose Category")
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    selectedCategory = options[which];
                    if (phoneCategoryButton != null) {
                        phoneCategoryButton.setText(
                                "▼  CATEGORIES: " + selectedCategory);
                    }
                    renderCategories();
                    renderChannels();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
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

            row.setOnLongClickListener(v -> {
                boolean wasFavorite = favorites.contains(channel.url);

                if (wasFavorite) {
                    favorites.remove(channel.url);
                } else {
                    favorites.add(channel.url);
                }

                getPreferences(MODE_PRIVATE).edit()
                        .putStringSet("favorite_urls",
                                new LinkedHashSet<>(favorites))
                        .apply();

                Toast.makeText(this,
                        wasFavorite
                                ? "Removed from Favorites"
                                : "Added to Favorites",
                        Toast.LENGTH_SHORT).show();

                renderCategories();
                renderChannels();
                return true;
            });

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


    private TextView guidePreviewChannelNameView;
    private TextView guidePreviewProgramInfoView;
    private ArrayList<EpgProgram> guidePrograms = new ArrayList<>();

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

        boolean phoneGuideLayout =
                isPhoneLayout();
        if (phoneGuideLayout) {
            setRequestedOrientation(
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        final Map<String, Channel> channelMap = new HashMap<>();
        TextView status = new TextView(this);
        for (Channel channel : channels) {
            if (channel.tvgId != null && !channel.tvgId.trim().isEmpty()) {
                channelMap.put(normalizeEpgId(channel.tvgId), channel);
            }
        }

        screen.removeAllViews();
        screen.setOrientation(LinearLayout.VERTICAL);

        int previewHeight = phoneGuideLayout ? dp(110) : dp(250);

        LinearLayout previewRow = new LinearLayout(this);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        if (phoneGuideLayout) {
            previewRow.setBackgroundColor(PANEL);
        }

        previewPlayerView = new PlayerView(this);
        previewPlayerView.setUseController(false);
        previewPlayerView.setFocusable(false);
        previewPlayerView.setBackgroundColor(Color.BLACK);
        previewRow.addView(previewPlayerView, new LinearLayout.LayoutParams(
                0, previewHeight, phoneGuideLayout ? 1.2f : 1f));

        LinearLayout previewInfo = new LinearLayout(this);
        previewInfo.setOrientation(LinearLayout.VERTICAL);
        if (phoneGuideLayout) {
            previewInfo.setBackgroundColor(PANEL);
        }
        previewInfo.setPadding(dp(10), dp(8), dp(8), dp(8));
        guidePreviewChannelNameView = label("Now Playing", 16);
        previewInfo.addView(guidePreviewChannelNameView);
        guidePreviewProgramInfoView = label(
                "Programme information unavailable", 13);
        previewInfo.addView(guidePreviewProgramInfoView);
        previewRow.addView(previewInfo, new LinearLayout.LayoutParams(
                0, previewHeight, phoneGuideLayout ? 0.8f : 1f));

        screen.addView(previewRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, previewHeight));

        LinearLayout guideContent = new LinearLayout(this);
        guideContent.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout categoryList = new LinearLayout(this);
        categoryList.setOrientation(LinearLayout.VERTICAL);
        guideCategoryScrollView = new ScrollView(this);
        guideCategoryScrollView.addView(categoryList);
        guideCategoryScrollView.setVisibility(android.view.View.GONE);
        guideContent.addView(guideCategoryScrollView,
                new LinearLayout.LayoutParams(dp(240),
                        LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        guideContent.addView(results, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        screen.addView(guideContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        if (isPhoneLayout()) {
            android.widget.Button categoriesButton =
                    new android.widget.Button(this);
            categoriesButton.setText("CATEGORIES");
            categoriesButton.setOnClickListener(v -> {
                if (guideCategoryScrollView != null) {
                    int visibility = guideCategoryScrollView.getVisibility();
                    guideCategoryScrollView.setVisibility(
                            visibility == android.view.View.VISIBLE
                                    ? android.view.View.GONE
                                    : android.view.View.VISIBLE);
                }
            });
            screen.addView(categoriesButton, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
        }

        Thread worker = new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(epgUrl.trim());
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "StreamNestIPTV/1.0");
                connection.setRequestProperty("Accept-Encoding", "gzip");

                ArrayList<EpgProgram> programs = new ArrayList<>();
                InputStream rawInput = connection.getInputStream();
                String contentEncoding = connection.getContentEncoding();
                boolean gzip = (contentEncoding != null
                        && contentEncoding.toLowerCase(Locale.US).contains("gzip"))
                        || url.getPath().toLowerCase(Locale.US).endsWith(".gz");
                try (InputStream input = gzip
                        ? new GZIPInputStream(rawInput) : rawInput) {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setInput(input, "UTF-8");

                    String currentChannel = null;
                    String currentTitle = null;
                    long currentStart = 0;
                    long currentStop = 0;
                    int event = parser.getEventType();

                    while (event != XmlPullParser.END_DOCUMENT
                            && programs.size() < 100000) {
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
                runOnUiThread(() -> {
                    guidePrograms = loaded;
                    renderGuidePrograms(loaded, channelMap, status, categoryList, results);
                });
            } catch (Exception e) {
                final String message = e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage();
                android.util.Log.e("StreamNest", "Could not load the EPG: " + message);
                runOnUiThread(() -> status.setText("Could not load EPG: " + message));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
        worker.start();
    }

    private String normalizeEpgId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
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
            Map<String, Channel> channelMap, TextView status,
            LinearLayout categoryList, LinearLayout results) {
        categoryList.removeAllViews();
        results.removeAllViews();

        long now = System.currentTimeMillis();
        long horizon = now + 24L * 60L * 60L * 1000L;
        int matched = 0;
        Map<String, ArrayList<EpgProgram>> grouped = new HashMap<>();
        for (Channel channel : channels) {
            String category = channel.group;
            if (category == null || category.trim().isEmpty()) {
                category = "Uncategorized";
            }
            if (!grouped.containsKey(category)) {
                grouped.put(category, new ArrayList<>());
            }
        }

        for (EpgProgram program : programs) {
            Channel channel = channelMap.get(normalizeEpgId(program.channelId));
            if (channel == null) continue;
            matched++;

            if (program.stopMillis > now && program.startMillis < horizon) {
                String category = channel.group;
                if (category == null || category.trim().isEmpty()) {
                    category = "Uncategorized";
                }
                if (!grouped.containsKey(category)) {
                    grouped.put(category, new ArrayList<>());
                }
                grouped.get(category).add(program);
            }
        }

        if (matched == 0) {
            status.setText("No EPG programmes matched the playlist channel IDs. "
                    + "Channels are still available to watch in Live TV.");
        } else {
            boolean hasVisiblePrograms = false;
            for (ArrayList<EpgProgram> list : grouped.values()) {
                if (!list.isEmpty()) {
                    hasVisiblePrograms = true;
                    break;
                }
            }
            if (!hasVisiblePrograms) {
                status.setText("No current or upcoming EPG programmes were found. "
                        + "Channels are still available to watch in Live TV.");
            }
        }

        int totalVisible = 0;
        for (ArrayList<EpgProgram> list : grouped.values()) {
            Collections.sort(list, Comparator.comparingLong(p -> p.startMillis));
            totalVisible += list.size();
        }

        if (matched == 0) {
            status.setText("No EPG programmes matched playlist channel IDs. "
                    + programs.size() + " programmes were parsed. Check the EPG URL "
                    + "and channel IDs.");
        } else if (totalVisible == 0) {
            status.setText("Matched " + matched + " EPG programmes, but none are "
                    + "current or upcoming.");
        } else {
            status.setText("Guide loaded: " + totalVisible + " listings across "
                    + grouped.size() + " categories. Select a category on the left.");
        }

        TreeSet<String> categories = new TreeSet<>(grouped.keySet());

        for (String category : categories) {
            int channelCount = 0;
            for (Channel channel : channels) {
                String channelCategory = channel.group;
                if (channelCategory == null || channelCategory.trim().isEmpty()) {
                    channelCategory = "Uncategorized";
                }
                if (category.equals(channelCategory)) {
                    channelCount++;
                }
            }
            TextView item = label(category + " (" + channelCount + ")", 15);
            item.setPadding(dp(10), dp(14), dp(8), dp(14));
            item.setBackgroundColor(PANEL);
            item.setFocusable(true);
            item.setClickable(true);
            item.setOnFocusChangeListener((v, focused) ->
                    v.setBackgroundColor(focused ? BLUE : PANEL));
            item.setOnClickListener(v -> {
                for (int i = 0; i < categoryList.getChildCount(); i++) {
                    android.view.View child = categoryList.getChildAt(i);
                    child.setBackgroundColor(child == item ? BLUE : PANEL);
                }
                renderGuideCategoryPrograms(category, grouped, channelMap, results);
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            categoryList.addView(item, params);
        }

        if (categoryList.getChildCount() > 0) {
            String lastUrl = getPreferences(MODE_PRIVATE)
                    .getString("last_channel_url", "");
            String preferredCategory = null;

            for (Channel channel : channels) {
                if (channel.url != null && channel.url.equals(lastUrl)) {
                    preferredCategory = channel.group;
                    if (preferredCategory == null
                            || preferredCategory.trim().isEmpty()) {
                        preferredCategory = "Uncategorized";
                    }
                    break;
                }
            }

            android.view.View selectedCategoryView =
                    categoryList.getChildAt(0);
            if (preferredCategory != null) {
                for (int i = 0; i < categoryList.getChildCount(); i++) {
                    android.view.View child = categoryList.getChildAt(i);
                    if (child instanceof TextView
                            && ((TextView) child).getText().toString()
                                    .startsWith(preferredCategory + " (")) {
                        selectedCategoryView = child;
                        break;
                    }
                }
            }
            selectedCategoryView.performClick();
        }
    }

    private void renderGuideCategoryPrograms(String category,
            Map<String, ArrayList<EpgProgram>> grouped,
            Map<String, Channel> channelMap, LinearLayout results) {
        results.removeAllViews();



        ArrayList<EpgProgram> categoryPrograms = grouped.get(category);
if (categoryPrograms == null) {
    categoryPrograms = new ArrayList<>();
}

        final long slotMillis = 30L * 60L * 1000L;
        final long gridStart =
                (System.currentTimeMillis() / slotMillis) * slotMillis;
        final long gridEnd = gridStart + 24L * 60L * 60L * 1000L;
        final int pixelsPerMinute = Math.max(2,
                (getResources().getConfiguration().screenWidthDp - 144) / 120);
        final int channelWidth = dp(120);
        final int slotWidth = dp(30 * pixelsPerMinute);
        final int rowHeight = dp(isPhoneLayout() ? 54 : 44);

        SimpleDateFormat clock =
                new SimpleDateFormat("h:mm a", Locale.getDefault());

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setDividerDrawable(
                new android.graphics.drawable.ColorDrawable(0xFF777777));
        grid.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

        LinearLayout timeHeader = new LinearLayout(this);
        timeHeader.setOrientation(LinearLayout.HORIZONTAL);
        timeHeader.setDividerDrawable(
                new android.graphics.drawable.ColorDrawable(0xFF777777));
        timeHeader.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

        TextView channelHeader = label("CHANNEL", 13);
        channelHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        channelHeader.setPadding(dp(6), dp(6), dp(6), dp(6));
        channelHeader.setBackgroundColor(PANEL);
        timeHeader.addView(channelHeader, new LinearLayout.LayoutParams(
                channelWidth, dp(42)));

        for (long time = gridStart; time < gridEnd; time += slotMillis) {
            TextView tick = label(clock.format(new Date(time)), 12);
            tick.setGravity(android.view.Gravity.CENTER);
            tick.setPadding(dp(2), dp(4), dp(2), dp(4));
            tick.setBackgroundColor(PANEL);
            LinearLayout.LayoutParams tickParams =
                    new LinearLayout.LayoutParams(slotWidth, dp(42));
            timeHeader.addView(tick, tickParams);
        }
        // Time ruler is displayed separately above the scrolling channel rows.

        Map<String, ArrayList<EpgProgram>> byChannel = new java.util.TreeMap<>();
        Map<String, Channel> rowChannels = new java.util.TreeMap<>();

        // Create a row for every playlist channel in this category.
        for (Channel channel : channels) {
            String channelCategory = channel.group;
            if (channelCategory == null || channelCategory.trim().isEmpty()) {
                channelCategory = "Uncategorized";
            }
            if (!category.equals(channelCategory)) continue;

            String name = channel.name;
            if (name == null || name.trim().isEmpty()) name = "Unnamed channel";

            rowChannels.put(name, channel);
            byChannel.put(name, new ArrayList<>());
        }

        // Add EPG programmes where a matching channel ID exists.
        for (EpgProgram program : categoryPrograms) {
            Channel channel = channelMap.get(normalizeEpgId(program.channelId));
            if (channel == null) continue;

            String name = channel.name;
            if (name == null || name.trim().isEmpty()) name = "Unnamed channel";

            if (byChannel.containsKey(name)) {
                byChannel.get(name).add(program);
            }
        }

        Channel firstPreviewChannel = null;
        for (Map.Entry<String, ArrayList<EpgProgram>> entry : byChannel.entrySet()) {
            ArrayList<EpgProgram> programs = entry.getValue();
            Collections.sort(programs, Comparator.comparingLong(
                    program -> program.startMillis));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setDividerDrawable(
                    new android.graphics.drawable.ColorDrawable(0xFF777777));
            row.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

            Channel rowChannel = rowChannels.get(entry.getKey());
        if (rowChannel == null) continue;
        if (firstPreviewChannel == null) firstPreviewChannel = rowChannel;
            LinearLayout channelCell = new LinearLayout(this);
            channelCell.setOrientation(LinearLayout.HORIZONTAL);
            channelCell.setGravity(android.view.Gravity.CENTER_VERTICAL);
            channelCell.setPadding(dp(5), dp(3), dp(5), dp(3));
            channelCell.setBackgroundColor(PANEL);
            channelCell.setFocusable(true);
            channelCell.setClickable(true);
            channelCell.setOnFocusChangeListener((v, focused) ->
                    v.setBackgroundColor(focused ? BLUE : PANEL));
            channelCell.setOnClickListener(v -> playGuidePreview(rowChannel));

            android.widget.ImageView logoView =
                    new android.widget.ImageView(this);
            logoView.setScaleType(
                    android.widget.ImageView.ScaleType.FIT_CENTER);
            channelCell.addView(logoView, new LinearLayout.LayoutParams(
                    dp(34), dp(34)));

            TextView channelLabel = label(entry.getKey(), 13);
            channelLabel.setGravity(
                    android.view.Gravity.CENTER_VERTICAL);
            channelLabel.setPadding(dp(6), dp(4), dp(2), dp(4));
            channelCell.addView(channelLabel, new LinearLayout.LayoutParams(
                    0, rowHeight, 1f));

            row.addView(channelCell, new LinearLayout.LayoutParams(
                    channelWidth, rowHeight));

            if (rowChannel != null && rowChannel.logoUrl != null
                    && !rowChannel.logoUrl.trim().isEmpty()) {
                loadChannelLogo(logoView, rowChannel.logoUrl);
            }

            if (programs.isEmpty()) {
                View noInfoRow = new View(this) {
                    private final android.graphics.Paint linePaint =
                            new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    private final android.graphics.Paint textPaint =
                            new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

                    {
                        linePaint.setColor(0xFF777777);
                        linePaint.setStrokeWidth(Math.max(1, dp(1)));
                        textPaint.setColor(Color.WHITE);
                        textPaint.setTextSize(dp(11));
                        textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
                    }

                    @Override
                    protected void onDraw(android.graphics.Canvas canvas) {
                        super.onDraw(canvas);
                        float cellWidth = dp(30 * pixelsPerMinute);

                        for (int i = 0; i < 48; i++) {
                            float left = i * cellWidth;
                            float right = left + cellWidth;
                            canvas.drawLine(left, 0, left, getHeight(), linePaint);

                            canvas.save();
                            canvas.clipRect(left, 0, right, getHeight());
                            float center = (left + right) / 2;
                            canvas.drawText("No Information", center, dp(17), textPaint);
                            long time = gridStart + i * slotMillis;
                            canvas.drawText(clock.format(new Date(time)),
                                    center, dp(35), textPaint);
                            canvas.restore();
                        }

                        canvas.drawLine(getWidth() - dp(1), 0,
                                getWidth() - dp(1), getHeight(), linePaint);
                        canvas.drawLine(0, getHeight() - dp(1),
                                getWidth(), getHeight() - dp(1), linePaint);
                    }
                };
                noInfoRow.setBackgroundColor(PANEL);
                noInfoRow.setFocusable(false);
                noInfoRow.setClickable(false);
                row.addView(noInfoRow, new LinearLayout.LayoutParams(
                        dp(24 * 60 * pixelsPerMinute), rowHeight));
                grid.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, rowHeight));
                continue;
            }

            long cursor = gridStart;
            for (EpgProgram program : programs) {
                long startTime = Math.max(program.startMillis, gridStart);
                long stopTime = Math.min(program.stopMillis, gridEnd);
                if (stopTime <= startTime) continue;

                if (startTime > cursor) {
                    long gapCursor = cursor;
                    while (gapCursor < startTime) {
                        long gapStop = Math.min(startTime, gapCursor + slotMillis);
                        int gapMinutes = Math.max(1, (int) Math.ceil(
                                (gapStop - gapCursor) / 60000.0));
                        TextView gap = label(
                                "No Information\n"
                                        + clock.format(new Date(gapCursor)), 12);
                        gap.setGravity(android.view.Gravity.CENTER);
                        gap.setPadding(dp(4), dp(3), dp(4), dp(3));
                        android.graphics.drawable.GradientDrawable gapBackground =
                        new android.graphics.drawable.GradientDrawable();
                gapBackground.setColor(PANEL);
                gapBackground.setStroke(dp(1), 0xFF777777);
                gap.setBackground(gapBackground);
                        gap.setFocusable(true);
                        gap.setClickable(true);
                        gap.setOnFocusChangeListener((v, focused) ->
                                v.setBackgroundColor(focused ? BLUE : PANEL));
                        gap.setMaxLines(3);
                        gap.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        LinearLayout.LayoutParams gapParams =
                                new LinearLayout.LayoutParams(
                                        Math.max(1, dp(gapMinutes * pixelsPerMinute)),
                                        rowHeight);
                        row.addView(gap, gapParams);
                        gapCursor = gapStop;
                    }
                }

                long durationMillis = stopTime - startTime;
                int durationMinutes = Math.max(1, (int) Math.ceil(durationMillis / 60000.0));
                int brickWidth = Math.max(1, dp(
                        durationMinutes * pixelsPerMinute));

                String title = program.title.isEmpty()
                        ? "(Untitled programme)" : program.title;
                TextView brick = label(
                        title + "  |  " + clock.format(new Date(startTime))
                                + " - " + clock.format(new Date(stopTime)),
                        14);
                brick.setGravity(android.view.Gravity.CENTER_VERTICAL);
                brick.setPadding(dp(5), 0, dp(5), 0);
                brick.setSingleLine(true);
                brick.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
                brick.setMarqueeRepeatLimit(-1);
                brick.setFocusable(true);
                brick.setClickable(true);

                android.graphics.drawable.GradientDrawable normalBrick =
                        new android.graphics.drawable.GradientDrawable();
                normalBrick.setColor(PANEL);
                normalBrick.setStroke(dp(1), 0xFF777777);

                android.graphics.drawable.GradientDrawable focusedBrick =
                        new android.graphics.drawable.GradientDrawable();
                focusedBrick.setColor(BLUE);
                focusedBrick.setStroke(dp(1), 0xFFFFFFFF);

                brick.setBackground(normalBrick);
                brick.setOnFocusChangeListener((v, focused) -> {
                    v.setBackground(focused ? focusedBrick : normalBrick);
                    v.setSelected(focused);
                });

                Channel channel = channelMap.get(normalizeEpgId(program.channelId));
                if (channel != null) {
                    if (firstPreviewChannel == null) {
                        firstPreviewChannel = channel;
                    }
                    brick.setOnClickListener(v -> {
                        if (guidePreviewChannel != null
                                && channel.url.equals(guidePreviewChannel.url)) {
                            playChannel(channel);
                        } else {
                            playGuidePreview(channel);
                        }
                    });
                }

                LinearLayout.LayoutParams brickParams =
                        new LinearLayout.LayoutParams(brickWidth, rowHeight);
                row.addView(brick, brickParams);

                cursor = Math.max(cursor, stopTime);
            }

            while (cursor < gridEnd) {
                long gapStop = Math.min(gridEnd, cursor + slotMillis);
                int gapMinutes = Math.max(1, (int) Math.ceil(
                        (gapStop - cursor) / 60000.0));
                TextView gap = label(
                        "No Information\n" + clock.format(new Date(cursor)), 12);
                gap.setGravity(android.view.Gravity.CENTER);
                gap.setPadding(dp(4), dp(3), dp(4), dp(3));
                android.graphics.drawable.GradientDrawable gapBackground =
                        new android.graphics.drawable.GradientDrawable();
                gapBackground.setColor(PANEL);
                gapBackground.setStroke(dp(1), 0xFF777777);
                gap.setBackground(gapBackground);
                gap.setFocusable(true);
                gap.setClickable(true);
                gap.setOnFocusChangeListener((v, focused) ->
                        v.setBackgroundColor(focused ? BLUE : PANEL));
                gap.setMaxLines(3);
                gap.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams gapParams =
                        new LinearLayout.LayoutParams(
                                Math.max(1, dp(gapMinutes * pixelsPerMinute)),
                                rowHeight);

                row.addView(gap, gapParams);
                cursor = gapStop;
            }

            grid.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, rowHeight));
        }

        String lastChannelUrl = getPreferences(MODE_PRIVATE)
                .getString("last_channel_url", "");
        Channel lastChannel = null;

        if (!lastChannelUrl.isEmpty()) {
            for (Channel candidate : channels) {
                if (lastChannelUrl.equals(candidate.url)) {
                    lastChannel = candidate;
                    break;
                }
            }
        }

        Channel previewChannel = firstPreviewChannel;
        if (lastChannel != null) {
            String lastCategory = lastChannel.group;
            if (lastCategory == null || lastCategory.trim().isEmpty()) {
                lastCategory = "Uncategorized";
            }
            if (category.equals(lastCategory)) {
                previewChannel = lastChannel;
            }
        }

        if (previewChannel != null) {
            playGuidePreview(previewChannel);
        }

        android.widget.FrameLayout gridFrame =
                new android.widget.FrameLayout(this);
        gridFrame.addView(grid, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        android.view.View currentTimeMarker = new android.view.View(this);
        currentTimeMarker.setBackgroundColor(Color.RED);
        currentTimeMarker.setElevation(dp(8));
        currentTimeMarker.setVisibility(View.VISIBLE);
        android.widget.FrameLayout.LayoutParams markerParams =
                new android.widget.FrameLayout.LayoutParams(
                        dp(3), android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        markerParams.leftMargin = channelWidth;
        markerParams.topMargin = 0;
        gridFrame.addView(currentTimeMarker, markerParams);
        currentTimeMarker.bringToFront();

        final android.os.Handler clockHandler =
                new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] markerUpdater = new Runnable[1];
        markerUpdater[0] = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                int minutes = (int) ((now - gridStart) / 60000L);
                int markerX = channelWidth
                        + dp(Math.max(0, minutes) * pixelsPerMinute);
                android.widget.FrameLayout.LayoutParams params =
                        (android.widget.FrameLayout.LayoutParams)
                                currentTimeMarker.getLayoutParams();
                params.leftMargin = markerX;
                if (gridFrame.getHeight() > 0) {
                    params.height = gridFrame.getHeight();
                }
                currentTimeMarker.setLayoutParams(params);
                currentTimeMarker.bringToFront();
                clockHandler.postDelayed(this, 30000L);
            }
        };
        gridFrame.post(markerUpdater[0]);
        gridFrame.addOnAttachStateChangeListener(
                new android.view.View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(android.view.View v) {
                    }

                    @Override
                    public void onViewDetachedFromWindow(android.view.View v) {
                        clockHandler.removeCallbacks(markerUpdater[0]);
                    }
                });

        HorizontalScrollView headerScroll = new HorizontalScrollView(this);
        headerScroll.setHorizontalScrollBarEnabled(false);
        headerScroll.addView(timeHeader);

        HorizontalScrollView horizontalScroll = new HorizontalScrollView(this);
        horizontalScroll.setHorizontalScrollBarEnabled(true);
        horizontalScroll.addView(gridFrame);

        headerScroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            if (horizontalScroll.getScrollX() != x) {
                horizontalScroll.scrollTo(x, 0);
            }
        });
        horizontalScroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            if (headerScroll.getScrollX() != x) {
                headerScroll.scrollTo(x, 0);
            }
        });

        ScrollView rowVerticalScroll = new ScrollView(this);
        rowVerticalScroll.setFillViewport(false);
        rowVerticalScroll.addView(horizontalScroll);

        LinearLayout timeline = new LinearLayout(this);
        timeline.setOrientation(LinearLayout.VERTICAL);
        timeline.addView(headerScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        timeline.addView(rowVerticalScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        results.addView(timeline, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void loadChannelLogo(
            android.widget.ImageView imageView, String logoUrl) {
        imageView.setTag(logoUrl);
        logoExecutor.execute(() -> {
            java.net.HttpURLConnection connection = null;
            try {
                java.net.URL url = new java.net.URL(logoUrl);
                connection = (java.net.HttpURLConnection)
                        url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                android.graphics.Bitmap bitmap;
                try (java.io.InputStream stream =
                             connection.getInputStream()) {
                    bitmap = android.graphics.BitmapFactory
                            .decodeStream(stream);
                }

                if (bitmap != null) {
                    imageView.post(() -> {
                        if (logoUrl.equals(imageView.getTag())) {
                            imageView.setImageBitmap(bitmap);
                        }
                    });
                }
            } catch (Exception e) {
                android.util.Log.d("StreamNest",
                        "Channel logo unavailable: " + logoUrl);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void playGuidePreview(Channel channel) {
        if (channel.url == null || channel.url.trim().isEmpty()
                || previewPlayerView == null) {
            return;
        }
        guidePreviewChannel = channel;

        if (guidePreviewChannelNameView != null) {
            guidePreviewChannelNameView.setText(channel.name);
        }

        String currentProgram = "Programme information unavailable";
        long now = System.currentTimeMillis();
        for (EpgProgram program : guidePrograms) {
            if (channel.tvgId != null
                    && normalizeEpgId(channel.tvgId).equals(normalizeEpgId(program.channelId))
                    && program.startMillis <= now
                    && program.stopMillis > now) {
                currentProgram = program.title;
                break;
            }
        }
        if (guidePreviewProgramInfoView != null) {
            guidePreviewProgramInfoView.setText("Time: " + new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date()) + "  |  Now: " + currentProgram);
        }

        if (channel.url.equals(previewChannelUrl) && previewPlayer != null) {
            return;
        }

        try {
            if (previewPlayer == null) {
                previewPlayer = new ExoPlayer.Builder(this).build();
                previewPlayerView.setPlayer(previewPlayer);
            }

            previewChannelUrl = channel.url;
            previewPlayer.setMediaItem(MediaItem.fromUri(channel.url));
            previewPlayer.prepare();
            previewPlayer.play();
        } catch (Exception e) {
            android.util.Log.e("StreamNest", "TV Guide preview failed", e);
        }
    }

    private void releaseGuidePreviewPlayer() {
        if (previewPlayerView != null) {
            previewPlayerView.setPlayer(null);
        }
        if (previewPlayer != null) {
            previewPlayer.release();
            previewPlayer = null;
        }
        previewChannelUrl = null;
    }

    private void releaseGuidePreview() {
        if (previewPlayerView != null) {
            previewPlayerView.setPlayer(null);
        }
        if (previewPlayer != null) {
            previewPlayer.release();
            previewPlayer = null;
        }
        previewChannelUrl = null;
        previewPlayerView = null;
        guidePreviewChannel = null;
    }

    private boolean launchLastPlayedChannel() {
    String lastUrl = getPreferences(MODE_PRIVATE)
            .getString("last_channel_url", "");

    if (lastUrl == null || lastUrl.trim().isEmpty()) {
        return false;
    }

    for (Channel channel : channels) {
        if (lastUrl.equals(channel.url)) {
            playChannel(channel);
            return true;
        }
    }

    return false;
}

private void playChannel(Channel channel) {
        getPreferences(MODE_PRIVATE).edit()
                .putString("last_channel_url", channel.url)
                .putString("last_channel_name", channel.name)
                .apply();

        releaseGuidePreviewPlayer();

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("channel_name", channel.name);
        intent.putExtra("stream_url", channel.url);
        startActivity(intent);
    }
}
