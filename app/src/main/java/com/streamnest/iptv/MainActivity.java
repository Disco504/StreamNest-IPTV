package com.streamnest.iptv;
import android.widget.ImageView;

import android.util.Xml;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
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
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.UUID;

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

    private final ThreadPoolExecutor logoExecutor =
            new ThreadPoolExecutor(
                    4, 4, 0L, TimeUnit.MILLISECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>(),
                    new ThreadPoolExecutor.AbortPolicy());

    private static final int NAVY = Color.rgb(15, 20, 35);
    private static final int PANEL = Color.rgb(27, 36, 54);
    private static final int BLUE = Color.rgb(25, 115, 190);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(175, 190, 210);

    private static final String PREF_SAVED_PLAYLISTS = "saved_playlists";
    private ArrayList<PlaylistProfile> savedPlaylists = new ArrayList<>();
    private String activePlaylistId = "";

    private LinearLayout screen;
    private LinearLayout categoryPanel;
    private TextView phoneCategoryButton;
    private LinearLayout channelPanel;
    private ArrayList<Channel> channels = new ArrayList<>();
    private CatalogDatabase catalogDatabase;
    private Set<String> favorites = new LinkedHashSet<>();
    private String selectedCategory = "All Channels";
    private boolean showingLiveTv = false;
    private boolean showingTvGuide = false;
    private boolean startupGuidePending = false;
    private boolean startupPreviewSuppressed = false;
    private View startupCoverView;
    private boolean startupPlayerLaunched = false;
    private boolean guideBackLongPressHandled = false;
    private ScrollView guideCategoryScrollView;
    private ScrollView guideMainMenuScroll;
    private boolean showingTvVod = false;
    private boolean showingMovieDetails = false;
    private LinearLayout movieGridScreen;
    private View movieReturnFocus;
    private View tvVodPosterReturnFocus;
    private ScrollView tvVodMainMenuScroll;
    private ScrollView tvVodCategoryScroll;
    private LinearLayout tvVodCategoryList;
    private LinearLayout tvVodMovieGrid;
    private ScrollView tvVodMovieScroll;
    private TextView tvVodMovieStatus;
    private ArrayList<Channel> tvVodLoadedMovies = new ArrayList<>();
    private int tvVodNextMovieIndex = 0;
    private int tvVodMovieLoadGeneration = 0;
    private boolean tvVodAppendingMovies = false;
    private int tvVodGridColumns = 4;
    private int tvVodGridColumn = 0;
    private LinearLayout tvVodCurrentGridRow;
    private String tvVodSelectedCategoryId = "";
    private String tvVodSelectedCategoryName = "";
    private boolean showingTvSeries = false;
    private boolean showingTvSeriesEpisodes = false;
    private ScrollView tvSeriesCategoryScroll;
    private int tvSeriesCategoryScrollY;
    private View tvSeriesPosterReturnFocus;
    private View tvSeriesCategoryReturnFocus;
    private ScrollView tvSeriesMainMenuScroll;
    private LinearLayout tvSeriesCategoryList;
    private LinearLayout tvSeriesGrid;
    private ScrollView tvSeriesGridScroll;
    private LinearLayout tvSeriesGridScreen;
    private View tvSeriesReturnFocus;
    private int tvSeriesReturnScrollY;
    private TextView tvSeriesStatus;
    private String tvSeriesSelectedCategoryId = "";
    private ArrayList<Channel> tvSeriesLoadedShows = new ArrayList<>();
    private int tvSeriesNextShowIndex = 0;
    private int tvSeriesLoadGeneration = 0;
    private ThreadPoolExecutor tvSeriesArtworkExecutor =
            new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>(),
                    new ThreadPoolExecutor.AbortPolicy());
    private boolean tvSeriesAppendingShows = false;
    private int tvSeriesGridColumns = 4;
    private int tvSeriesGridColumn = 0;
    private LinearLayout tvSeriesCurrentGridRow;
    private String tvSeriesSelectedCategoryName = "";

    private ExoPlayer previewPlayer;
    private PlayerView previewPlayerView;
    private String previewChannelUrl;
    private Channel guidePreviewChannel;
    private int guideRenderGeneration = 0;
    private Runnable guideAppendMoreRows;
    private String guidePendingMoveChannelKey = "";
    private int guidePendingMoveScrollY = -1;
    private static final int GUIDE_CHANNEL_BATCH_SIZE = 40;
    private final ArrayList<View> guideChannelNameCells = new ArrayList<>();
    private static class PlaylistProfile {
        String id;
        String name;
        String type;
        String url = "";
        String server = "";
        String username = "";
        String password = "";
    }

    private static class Channel {
        String name;
        String url;
        String group;
        String tvgId;
        String logoUrl;
        String xtreamStreamId = "";
        String vodId = "";
        String vodExtension = "mp4";
        String seriesBackdrop = "";
        String episodeArtwork = "";
        String episodeDescription = "";
        String episodeNumber = "";
        String resumeEpisodeId = "";

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

        Channel(String name, String url, String group, String tvgId,
                String logoUrl, String xtreamStreamId) {
            this(name, url, group, tvgId, logoUrl);
            this.xtreamStreamId = xtreamStreamId;
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


        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(NAVY);
        setContentView(screen);

        catalogDatabase = new CatalogDatabase(getApplicationContext());
        loadSavedPlaylists();
        migratePlaylistOrderPreferences();
        migratePlaylistFavorites();
        favorites = new LinkedHashSet<>(getPreferences(MODE_PRIVATE)
                .getStringSet(playlistPreferenceKey("favorite_urls"), Collections.emptySet()));
        if (savedPlaylists.isEmpty()) {
            showWelcomeScreen();
        } else {
            syncActivePlaylistToConnection();
            restoreSavedConnection();
        }
    }

    private void saveSavedPlaylists() {
        JSONArray array = new JSONArray();
        try {
            for (PlaylistProfile profile : savedPlaylists) {
                JSONObject item = new JSONObject();
                item.put("id", profile.id);
                item.put("name", profile.name);
                item.put("type", profile.type);
                item.put("url", profile.url);
                item.put("server", profile.server);
                item.put("username", profile.username);
                item.put("password", profile.password);
                array.put(item);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not save playlists",
                    Toast.LENGTH_LONG).show();
            return;
        }
        getPreferences(MODE_PRIVATE).edit()
                .putString(PREF_SAVED_PLAYLISTS, array.toString())
                .putString("active_playlist_id", activePlaylistId)
                .apply();
    }

    private void loadSavedPlaylists() {
        android.content.SharedPreferences prefs =
                getPreferences(MODE_PRIVATE);
        savedPlaylists.clear();

        String stored = prefs.getString(PREF_SAVED_PLAYLISTS, "");
        if (!stored.isEmpty()) {
            try {
                JSONArray array = new JSONArray(stored);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    PlaylistProfile profile = new PlaylistProfile();
                    profile.id = item.optString("id", "");
                    profile.name = item.optString("name", "My Playlist");
                    profile.type = item.optString("type", "");
                    profile.url = item.optString("url", "");
                    profile.server = item.optString("server", "");
                    profile.username = item.optString("username", "");
                    profile.password = item.optString("password", "");
                    if (!profile.id.isEmpty()
                            && ("m3u".equals(profile.type)
                            || "xtream".equals(profile.type))) {
                        savedPlaylists.add(profile);
                    }
                }
            } catch (Exception e) {
                savedPlaylists.clear();
            }
        }

        activePlaylistId = prefs.getString("active_playlist_id", "");

        if (savedPlaylists.isEmpty()) {
            String type = prefs.getString("last_connection", "");
            PlaylistProfile profile = new PlaylistProfile();
            profile.id = UUID.randomUUID().toString();
            profile.type = type;

            if ("xtream".equals(type)) {
                profile.server = prefs.getString("xtream_server", "");
                profile.username = prefs.getString("xtream_username", "");
                profile.password = prefs.getString("xtream_password", "");
                profile.name = profile.username.isEmpty()
                        ? "Xtream Playlist" : profile.username;
                if (profile.server.isEmpty() || profile.username.isEmpty()
                        || profile.password.isEmpty()) return;
            } else if ("m3u".equals(type)
                    || (type.isEmpty()
                    && !prefs.getString("playlist_url", "").isEmpty())) {
                profile.type = "m3u";
                profile.url = prefs.getString("playlist_url", "");
                profile.name = "M3U Playlist";
                if (profile.url.isEmpty()) return;
            } else {
                return;
            }

            savedPlaylists.add(profile);
            activePlaylistId = profile.id;
            saveSavedPlaylists();
        } else if (activePlaylistId.isEmpty()) {
            activePlaylistId = savedPlaylists.get(0).id;
            saveSavedPlaylists();
        }
    }

    private String playlistPreferenceKey(String key) {
        return "playlist_" + activePlaylistId + "_" + key;
    }

    private void migratePlaylistOrderPreferences() {
        if (activePlaylistId.isEmpty()) return;
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String[] keys = {"movie_category_order", "series_category_order",
                "guide_category_order", "guide_channel_orders"};
        android.content.SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        for (String key : keys) {
            if (prefs.contains(key)) {
                String scopedKey = playlistPreferenceKey(key);
                if (!prefs.contains(scopedKey)) {
                    editor.putString(scopedKey, prefs.getString(key, ""));
                }
                editor.remove(key);
                changed = true;
            }
        }
        if (changed) editor.apply();
    }

    private void migratePlaylistFavorites() {
        if (activePlaylistId.isEmpty()) return;
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        if (prefs.contains("favorite_urls")) {
            String scopedKey = playlistPreferenceKey("favorite_urls");
            android.content.SharedPreferences.Editor editor = prefs.edit();
            if (!prefs.contains(scopedKey)) {
                editor.putStringSet(scopedKey,
                        new LinkedHashSet<>(prefs.getStringSet("favorite_urls", Collections.emptySet())));
            }
            editor.remove("favorite_urls").apply();
        }
    }

    private void loadPlaylistFavorites() {
        favorites = new LinkedHashSet<>(getPreferences(MODE_PRIVATE)
                .getStringSet(playlistPreferenceKey("favorite_urls"), Collections.emptySet()));
    }

    private void syncActivePlaylistToConnection() {
        PlaylistProfile active = null;
        for (PlaylistProfile profile : savedPlaylists) {
            if (profile.id.equals(activePlaylistId)) {
                active = profile;
                break;
            }
        }

        if (active == null && !savedPlaylists.isEmpty()) {
            active = savedPlaylists.get(0);
            activePlaylistId = active.id;
        }
        if (active == null) return;

        android.content.SharedPreferences.Editor editor =
                getPreferences(MODE_PRIVATE).edit();
        editor.putString("last_connection", active.type);

        if ("m3u".equals(active.type)) {
            editor.putString("playlist_url", active.url);
        } else if ("xtream".equals(active.type)) {
            editor.putString("xtream_server", active.server);
            editor.putString("xtream_username", active.username);
            editor.putString("xtream_password", active.password);
        }

        editor.putString("active_playlist_id", activePlaylistId).apply();
    }

    private void restoreXtreamCache(
            String base, String username, String password,
            boolean launchAfterLoad) {
        new Thread(() -> {
            ArrayList<Channel> cached = new ArrayList<>();
            try {
                String providerKey = CatalogDatabase.makeProviderKey(
                        base + "|" + username);
                int count = catalogDatabase.getCatalogCount(
                        providerKey, "live", null);

                for (int offset = 0; offset < count; offset += 500) {
                    ArrayList<CatalogDatabase.CatalogItem> page =
                            catalogDatabase.getCatalogPage(
                                    providerKey, "live", null, 500, offset);
                    for (CatalogDatabase.CatalogItem item : page) {
                        String id = item.itemId;
                        if (id == null || id.isEmpty()) continue;

                        String extension = item.extra;
                        if (extension == null || extension.isEmpty()) {
                            extension = "ts";
                        }

                        String url = base + "/live/"
                                + Uri.encode(username) + "/"
                                + Uri.encode(password) + "/"
                                + Uri.encode(id) + "."
                                + Uri.encode(extension);

                        cached.add(new Channel(
                                item.name, url, item.category,
                                item.epgId, item.artworkUrl, id));
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("StreamNest",
                        "Cached Xtream restore failed", e);
            }

            runOnUiThread(() -> {
                if (cached.isEmpty()) {
                    loadXtreamChannels(
                            base, username, password, launchAfterLoad);
                    return;
                }

                channels = cached;
                selectedCategory = "All Channels";
                if (launchAfterLoad) showTvGuide();
                loadXtreamChannels(base, username, password, false);
            });
        }).start();
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
                boolean launchOnBoot =
                        prefs.getBoolean("launch_live_tv_on_boot", true);
                startupGuidePending = launchOnBoot;
                startupPreviewSuppressed = launchOnBoot;
                restoreXtreamCache(server, username, password, launchOnBoot);
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
                boolean launchOnBoot =
                        prefs.getBoolean("launch_live_tv_on_boot", true);
                startupGuidePending = launchOnBoot;
                startupPreviewSuppressed = launchOnBoot;
                loadPlaylist(playlistUrl, launchOnBoot);
            }
        }
    }

    private boolean isAtLeftEdgeOfPosterRow(View focused, LinearLayout grid) {
        if (focused == null || grid == null || focused.getParent() == null) return false;
        android.view.ViewParent parent = focused.getParent();
        if (!(parent instanceof LinearLayout) || parent.getParent() != grid) return false;

        LinearLayout row = (LinearLayout) parent;
        return row.indexOfChild(focused) == 0;
    }

    private void focusFirstGuideMenuItem() {
        if (guideMainMenuScroll == null
                || !(guideMainMenuScroll.getChildAt(0) instanceof LinearLayout)) {
            return;
        }
        LinearLayout menu = (LinearLayout) guideMainMenuScroll.getChildAt(0);
        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && child.isFocusable()) {
                child.requestFocus();
                return;
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP
                    && guideBackLongPressHandled) {
                guideBackLongPressHandled = false;
                return true;
            }
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0) {
                guideBackLongPressHandled = false;
            }
        }
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && isTelevisionDevice()) {
            int key = event.getKeyCode();
            View focused = getCurrentFocus();

        // Keep TV Guide focus from moving above its first visible row.
        if (key == android.view.KeyEvent.KEYCODE_DPAD_UP
                && showingTvGuide && focused != null) {

            // Stop at the first focusable category.
            if (guideCategoryScrollView != null
                    && guideCategoryScrollView.getVisibility() == View.VISIBLE
                    && guideCategoryScrollView.getChildAt(0) instanceof LinearLayout) {
                LinearLayout items = (LinearLayout)
                        guideCategoryScrollView.getChildAt(0);
                for (int i = 0; i < items.getChildCount(); i++) {
                    View child = items.getChildAt(i);
                    if (child.getVisibility() == View.VISIBLE
                            && child.isFocusable()) {
                        if (focused == child) return true;
                        break;
                    }
                }
            }

            // Stop at the first channel name in the fixed column.
            if (!guideChannelNameCells.isEmpty()
                    && focused == guideChannelNameCells.get(0)) {
                return true;
            }

            // Stop at the top program row and all of its bricks.
            android.view.ViewParent rowParent = focused.getParent();
            if (rowParent instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) rowParent;
                android.view.ViewParent gridParent = row.getParent();
                if (gridParent instanceof LinearLayout) {
                    LinearLayout guideGrid = (LinearLayout) gridParent;
                    android.view.ViewParent frameParent = guideGrid.getParent();
                    if (frameParent instanceof android.widget.FrameLayout
                            && ((android.widget.FrameLayout) frameParent).getParent()
                                    instanceof android.widget.HorizontalScrollView) {
                        android.view.ViewParent verticalParent =
                                ((android.widget.HorizontalScrollView) frameParent.getParent())
                                        .getParent();
                        if (verticalParent instanceof android.widget.ScrollView
                                && guideGrid.getChildCount() > 0
                                && guideGrid.getChildAt(0) == row) {
                            return true;
                        }
                    }
                }
            }
        }

            if (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {

                if (showingTvSeries
                        && tvSeriesCategoryScroll != null
                        && tvSeriesCategoryScroll.getVisibility() != View.VISIBLE
                        && isAtLeftEdgeOfPosterRow(focused, tvSeriesGrid)) {
                    tvSeriesPosterReturnFocus = focused;
                    tvSeriesCategoryScroll.setVisibility(View.VISIBLE);
                    focusTvSeriesSelectedCategory();
                    return true;
                }

                if (showingTvSeries
                        && tvSeriesCategoryScroll != null
                        && tvSeriesCategoryScroll.getVisibility() == View.VISIBLE
                        && tvSeriesMainMenuScroll != null
                        && tvSeriesMainMenuScroll.getVisibility() != View.VISIBLE
                        && focused != null
                        && focused.getParent() == tvSeriesCategoryList) {
                    tvSeriesMainMenuScroll.setVisibility(View.VISIBLE);
                    View menu = tvSeriesMainMenuScroll.getChildAt(0);
                    if (menu instanceof LinearLayout
                            && ((LinearLayout) menu).getChildCount() > 0) {
                        ((LinearLayout) menu).getChildAt(0).requestFocus();
                    }
                    return true;
                }

                if (showingTvVod) {
                    if (tvVodCategoryScroll != null
                            && tvVodCategoryScroll.getVisibility() != View.VISIBLE
                            && isAtLeftEdgeOfPosterRow(focused, tvVodMovieGrid)) {
                        tvVodPosterReturnFocus = focused;
                        tvVodCategoryScroll.setVisibility(View.VISIBLE);
                        focusTvVodSelectedCategory();
                        return true;
                    }

                    if (tvVodCategoryScroll != null
                            && tvVodCategoryScroll.getVisibility() == View.VISIBLE
                            && tvVodMainMenuScroll != null
                            && tvVodMainMenuScroll.getVisibility() != View.VISIBLE
                            && focused != null
                            && focused.getParent() == tvVodCategoryList) {
                        tvVodMainMenuScroll.setVisibility(View.VISIBLE);
                        View menu = tvVodMainMenuScroll.getChildAt(0);
                        if (menu instanceof LinearLayout
                                && ((LinearLayout) menu).getChildCount() > 0) {
                            ((LinearLayout) menu).getChildAt(0).requestFocus();
                        }
                        return true;
                    }
                }

                if (showingTvGuide
                        && guideCategoryScrollView != null
                        && guideCategoryScrollView.getVisibility() == View.VISIBLE
                        && guideMainMenuScroll != null
                        && guideMainMenuScroll.getVisibility() != View.VISIBLE
                        && focused != null
                        && guideCategoryScrollView.getChildAt(0) instanceof LinearLayout
                        && focused.getParent() == guideCategoryScrollView.getChildAt(0)) {
                    guideMainMenuScroll.setVisibility(View.VISIBLE);
                    focusFirstGuideMenuItem();
                    return true;
                }

                if (showingTvGuide
                        && guideCategoryScrollView != null
                        && guideCategoryScrollView.getVisibility() != View.VISIBLE
                        && focused != null
                        && guideChannelNameCells.contains(focused)) {
                    guideCategoryScrollView.setVisibility(View.VISIBLE);

                    View list = guideCategoryScrollView.getChildAt(0);
                    if (list instanceof LinearLayout) {
                        LinearLayout items = (LinearLayout) list;
                        View selectedCategory = null;
                        for (int i = 0; i < items.getChildCount(); i++) {
                            View child = items.getChildAt(i);
                            if (child.isSelected()) {
                                selectedCategory = child;
                                break;
                            }
                        }
                        if (selectedCategory == null && items.getChildCount() > 0) {
                            selectedCategory = items.getChildAt(0);
                        }
                        if (selectedCategory != null) selectedCategory.requestFocus();
                    }
                    return true;
                }
            }

            if (key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (showingTvSeries
                        && tvSeriesMainMenuScroll != null
                        && tvSeriesMainMenuScroll.getVisibility() == View.VISIBLE) {
                    tvSeriesMainMenuScroll.setVisibility(View.GONE);
                    focusTvSeriesSelectedCategory();
                    return true;
                }

                if (showingTvSeries
                        && tvSeriesCategoryScroll != null
                        && tvSeriesCategoryScroll.getVisibility() == View.VISIBLE) {
                    tvSeriesCategoryScroll.setVisibility(View.GONE);
                    if (tvSeriesPosterReturnFocus != null
                            && tvSeriesPosterReturnFocus.getParent() != null) {
                        tvSeriesPosterReturnFocus.requestFocus();
                    } else {
                        focusFirstTvSeriesPoster();
                    }
                    return true;
                }

                if (showingTvVod) {
                    if (tvVodMainMenuScroll != null
                            && tvVodMainMenuScroll.getVisibility() == View.VISIBLE) {
                        tvVodMainMenuScroll.setVisibility(View.GONE);
                        focusTvVodSelectedCategory();
                        return true;
                    }

                    if (tvVodCategoryScroll != null
                            && tvVodCategoryScroll.getVisibility() == View.VISIBLE) {
                        tvVodCategoryScroll.setVisibility(View.GONE);
                        if (tvVodPosterReturnFocus != null
                                && tvVodPosterReturnFocus.getParent() != null) {
                            tvVodPosterReturnFocus.requestFocus();
                        } else {
                            focusFirstTvVodPoster();
                        }
                        return true;
                    }
                }

                if (showingTvGuide
                        && guideMainMenuScroll != null
                        && guideMainMenuScroll.getVisibility() == View.VISIBLE) {
                    guideMainMenuScroll.setVisibility(View.GONE);
                    focusGuideSelectedCategory();
                    return true;
                }

                if (showingTvGuide
                        && guideCategoryScrollView != null
                        && guideCategoryScrollView.getVisibility() == View.VISIBLE) {
                    guideCategoryScrollView.setVisibility(View.GONE);
                    return true;
                }
            }
        }

        if (showingTvGuide
                && event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.isLongPress()
                && guidePreviewChannel != null) {
            guideBackLongPressHandled = true;
            playChannel(guidePreviewChannel);
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (showingMovieDetails) {
            showingMovieDetails = false;
            if (movieGridScreen != null) {
                screen = movieGridScreen;
                setContentView(screen);
                movieGridScreen = null;
            }
            if (movieReturnFocus != null) {
                movieReturnFocus.requestFocus();
                movieReturnFocus = null;
            }
            return;
        }
        if (showingTvVod) {
            if (tvVodCategoryScroll != null
                    && tvVodCategoryScroll.getVisibility() == View.VISIBLE) {
                tvVodCategoryScroll.setVisibility(View.GONE);
                if (tvVodPosterReturnFocus != null
                        && tvVodPosterReturnFocus.getParent() != null) {
                    tvVodPosterReturnFocus.requestFocus();
                } else {
                    focusFirstTvVodPoster();
                }
            } else if (tvVodCategoryScroll != null) {
                tvVodCategoryScroll.setVisibility(View.VISIBLE);
                focusTvVodSelectedCategory();
            }
            return;
        } else if (showingTvSeriesEpisodes) {
            showingTvSeriesEpisodes = false;
            showingTvSeries = true;
            if (tvSeriesGridScreen != null) {
                screen = tvSeriesGridScreen;
                setContentView(tvSeriesGridScreen);
                tvSeriesGridScroll.post(() -> {
                    tvSeriesGridScroll.scrollTo(0, tvSeriesReturnScrollY);
                    if (tvSeriesReturnFocus != null) {
                        tvSeriesReturnFocus.requestFocus();
                    }
                });
            } else {
                showSeries();
            }
            return;
        } else if (showingTvSeries) {
            if (tvSeriesCategoryScroll != null
                    && tvSeriesCategoryScroll.getVisibility() == View.VISIBLE) {
                tvSeriesCategoryScrollY = tvSeriesCategoryScroll.getScrollY();
                tvSeriesCategoryReturnFocus = tvSeriesCategoryList.findFocus();
                tvSeriesCategoryScroll.setVisibility(View.GONE);
                if (tvSeriesPosterReturnFocus != null
                        && tvSeriesPosterReturnFocus.getParent() != null) {
                    tvSeriesPosterReturnFocus.requestFocus();
                } else {
                    focusFirstTvSeriesPoster();
                }
            } else if (tvSeriesCategoryScroll != null) {
                tvSeriesCategoryScroll.setVisibility(View.VISIBLE);
                tvSeriesCategoryScroll.post(() -> {
                    if (tvSeriesCategoryReturnFocus != null) {
                        tvSeriesCategoryReturnFocus.requestFocus();
                    } else {
                        focusTvSeriesSelectedCategory();
                    }
                    tvSeriesCategoryScroll.scrollTo(0, tvSeriesCategoryScrollY);
                });
            }
            return;
        } else if (showingTvGuide && guideCategoryScrollView != null) {
            if (isTelevisionDevice()) {
                if (guideMainMenuScroll != null
                        && guideMainMenuScroll.getVisibility() == View.VISIBLE) {
                    super.onBackPressed();
                } else if (guideCategoryScrollView.getVisibility() == View.VISIBLE) {
                    guideCategoryScrollView.setVisibility(View.GONE);
                    if (guideMainMenuScroll != null) {
                        guideMainMenuScroll.setVisibility(View.VISIBLE);
                        focusFirstGuideMenuItem();
                    }
                } else {
                    guideCategoryScrollView.setVisibility(View.VISIBLE);
                    focusGuideSelectedCategory();
                }
            } else if (guideCategoryScrollView.getVisibility() == View.VISIBLE) {
                guideCategoryScrollView.setVisibility(View.GONE);
                String selectedUrl = guidePreviewChannel != null
                        ? guidePreviewChannel.url
                        : getPreferences(MODE_PRIVATE)
                                .getString("last_channel_url", "");
                boolean focusedChannel = false;
                for (View cell : guideChannelNameCells) {
                    if (selectedUrl != null && selectedUrl.equals(cell.getTag())) {
                        cell.requestFocus();
                        focusedChannel = true;
                        break;
                    }
                }
                if (!focusedChannel && !guideChannelNameCells.isEmpty()) {
                    guideChannelNameCells.get(0).requestFocus();
                }
            } else {
                guideCategoryScrollView.setVisibility(View.VISIBLE);
                focusGuideSelectedCategory();
            }
            return;
        } else if (showingLiveTv || showingTvGuide) {
            showHomeScreen();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (previewPlayer != null) {
            previewPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (startupPlayerLaunched) {
            removeStartupCover();
            startupPlayerLaunched = false;
        }
        if (showingTvGuide && previewPlayerView != null
                && guidePreviewChannel != null) {
            playGuidePreview(guidePreviewChannel);
        }
    }

    private void removeStartupCover() {
        if (startupCoverView != null
                && startupCoverView.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) startupCoverView.getParent())
                    .removeView(startupCoverView);
        }
        startupCoverView = null;
    }


    private LinearLayout createMoviePosterCard(Channel movie) {
        boolean tv = isTelevisionDevice();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackgroundColor(PANEL);
        card.setPadding(dp(4), dp(4), dp(4), dp(tv ? 3 : 6));

        android.widget.ImageView poster = new android.widget.ImageView(this);
        poster.setLayoutParams(new LinearLayout.LayoutParams(
                tv ? LinearLayout.LayoutParams.MATCH_PARENT : dp(116),
                dp(tv ? 250 : 164)));
        poster.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(Color.rgb(35, 45, 62));
        if (movie.logoUrl != null && !movie.logoUrl.isEmpty()) {
            loadChannelLogo(poster, movie.logoUrl);
        }
        card.addView(poster);

        TextView name = label(movie.name, tv ? 14 : 13);
        if (tv) {
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
            name.setMarqueeRepeatLimit(-1);
            name.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
            name.setPadding(dp(3), 0, dp(3), 0);
        } else {
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            name.setPadding(dp(3), dp(6), dp(3), dp(2));
        }
        card.addView(name, new LinearLayout.LayoutParams(
                tv ? LinearLayout.LayoutParams.MATCH_PARENT : dp(116),
                dp(tv ? 32 : 48)));

        card.setOnFocusChangeListener((v, focused) -> {
            v.setBackgroundColor(focused ? BLUE : PANEL);
            name.setSelected(tv && focused);
        });
        card.setOnClickListener(v -> showMovieDetails(movie, card));

        return card;
    }

    private void addMovieCategoryBrowser(
            LinearLayout parent, ArrayList<String[]> categories,
            String base, String user, String pass) {

        TextView title = label("BROWSE ALL CATEGORIES", 19);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(WHITE);
        title.setPadding(0, dp(14), 0, dp(8));
        parent.addView(title);

        ArrayList<String[]> ordered = new ArrayList<>();
        ordered.add(new String[]{"__ALL_MOVIES__", "ALL MOVIES"});
            if (getActiveMovieProgress().length() > 0) {
                ordered.add(new String[]{"__CONTINUE_WATCHING__", "CONTINUE WATCHING"});
            }
            ordered.add(new String[]{"__MY_LIST__", "MY LIST"});

        String[] releases = null;
        for (String[] category : categories) {
            if (category[1].toLowerCase().contains("new release")) {
                releases = category;
                break;
            }
        }

        if (releases != null) ordered.add(releases);

        for (String[] category : categories) {
            if (category != releases) ordered.add(category);
        }

        for (int i = 0; i < ordered.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            for (int j = 0; j < 2; j++) {
                int index = i + j;
                if (index < ordered.size()) {
                    String[] category = ordered.get(index);
                    TextView button = label(category[1], 15);
                    button.setTextColor(WHITE);
                    button.setFocusable(true);
                    button.setClickable(true);
                    button.setMinHeight(dp(48));
                    button.setPadding(dp(10), dp(10), dp(10), dp(10));
                    button.setBackgroundColor(PANEL);
                    button.setOnFocusChangeListener((v, focused) ->
                            v.setBackgroundColor(focused ? BLUE : PANEL));
                    button.setOnClickListener(v ->
                            showMoviesInCategory(category[0], category[1],
                                    base, user, pass));

                    LinearLayout.LayoutParams params =
                            new LinearLayout.LayoutParams(0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    params.setMargins(dp(3), dp(3), dp(3), dp(3));
                    row.addView(button, params);
                } else {
                    row.addView(new android.view.View(this),
                            new LinearLayout.LayoutParams(0, dp(48), 1f));
                }
            }
            parent.addView(row);
        }
    }

    private void showMovies() {
        if (isTelevisionDevice()) {
            showTvMovies();
            return;
        }
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
        screen.setPadding(dp(14), dp(8), dp(14), dp(8));

        TextView heading = label("MOVIES", 25);
        heading.setTextColor(Color.rgb(95, 190, 255));
        heading.setTypeface(null, Typeface.BOLD);
        screen.addView(heading);

        TextView status = label("Loading your movie library...", 14);
        status.setTextColor(MUTED);
        screen.addView(status);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView back = label("BACK TO HOME", 15);
        back.setFocusable(true);
        back.setClickable(true);
        back.setPadding(dp(8), dp(12), dp(8), dp(12));
        back.setOnFocusChangeListener((v, focused) ->
                v.setBackgroundColor(focused ? BLUE : NAVY));
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
                    String name = item.optString(
                            "category_name", "Uncategorized");
                    if (!id.isEmpty()) {
                        categories.add(new String[]{id, name});
                    }
                }



                for (int i = 0; i < categories.size(); i++) {
                    if (categories.get(i)[1].toLowerCase()
                            .contains("new release")) {
                        String[] item = categories.remove(i);
                        categories.add(0, item);
                        break;
                    }
                }

                } catch (Exception e) {
                failure = e.getMessage() == null
                        ? "Could not load movie categories." : e.getMessage();
            }

            final String error = failure;
            final ArrayList<String[]> loadedCategories = categories;
            runOnUiThread(() -> {
                content.removeAllViews();
                if (error != null) {
                    status.setText("Unable to load movies: " + error);
                    return;
                }
                if (loadedCategories.isEmpty()) {
                    status.setText(
                            "No movie categories were returned by your provider.");
                    return;
                }

                                    status.setText(loadedCategories.size() + " categories");
                    addMovieCategoryBrowser(content, loadedCategories, base, user, pass);
            });
        }).start();
    }


    private void showTvMovies() {
        String base = getPreferences(MODE_PRIVATE).getString("xtream_server", "");
        String user = getPreferences(MODE_PRIVATE).getString("xtream_username", "");
        String pass = getPreferences(MODE_PRIVATE).getString("xtream_password", "");

        if (base.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Movies")
                    .setMessage("Connect your Xtream Codes account in Settings.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        releaseGuidePreview();
        showingLiveTv = false;
        showingTvGuide = false;
        showingTvVod = true;

        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(NAVY);
        screen.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView heading = label("MOVIES", 24);
        heading.setTextColor(Color.rgb(95, 190, 255));
        heading.setTypeface(null, Typeface.BOLD);
        screen.addView(heading);

        tvVodMovieStatus = label("Loading your movie library...", 14);
        tvVodMovieStatus.setTextColor(MUTED);
        screen.addView(tvVodMovieStatus);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        screen.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        tvVodMainMenuScroll = new ScrollView(this);
        tvVodMainMenuScroll.setVisibility(View.GONE);
        LinearLayout mainMenu = new LinearLayout(this);
        mainMenu.setOrientation(LinearLayout.VERTICAL);
        mainMenu.setPadding(dp(5), dp(5), dp(5), dp(5));
        addHomeSection(mainMenu, "SEARCH", "Find live TV channels");
        addHomeSection(mainMenu, "TV GUIDE", "Browse the TV schedule");
        addHomeSection(mainMenu, "MOVIES", "Browse movies");
        addHomeSection(mainMenu, "TV SERIES", "Browse TV series");
        addHomeSection(mainMenu, "CATCH-UP", "Recently aired programs");
        addHomeSection(mainMenu, "SETTINGS", "Playlist and app settings");
        tvVodMainMenuScroll.addView(mainMenu);
        body.addView(tvVodMainMenuScroll, new LinearLayout.LayoutParams(
                dp(190), LinearLayout.LayoutParams.MATCH_PARENT));

        tvVodCategoryScroll = new ScrollView(this);
        tvVodCategoryScroll.setVisibility(View.GONE);
        tvVodCategoryList = new LinearLayout(this);
        tvVodCategoryList.setOrientation(LinearLayout.VERTICAL);
        tvVodCategoryList.setPadding(dp(5), dp(5), dp(5), dp(5));
        tvVodCategoryScroll.addView(tvVodCategoryList);
        body.addView(tvVodCategoryScroll, new LinearLayout.LayoutParams(
                dp(190), LinearLayout.LayoutParams.MATCH_PARENT));

        tvVodMovieScroll = new ScrollView(this);
        tvVodMovieGrid = new LinearLayout(this);
        tvVodMovieGrid.setOrientation(LinearLayout.VERTICAL);
        tvVodMovieGrid.setPadding(dp(5), dp(5), dp(5), dp(5));
        tvVodMovieScroll.addView(tvVodMovieGrid);
        body.addView(tvVodMovieScroll, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        setContentView(screen);
        tvVodMovieScroll.setOnScrollChangeListener(
                (View v, int x, int y, int oldX, int oldY) -> {
                    View content = tvVodMovieScroll.getChildAt(0);
                    if (content != null
                            && y + tvVodMovieScroll.getHeight()
                            >= content.getHeight() - dp(500)) {
                        appendTvVodMovieBatch();
                    }
                });


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
                    if (!id.isEmpty()) categories.add(new String[]{id, name});
                }

                for (int i = 0; i < categories.size(); i++) {
                    if (categories.get(i)[1].toLowerCase().contains("new release")) {
                        String[] item = categories.remove(i);
                        categories.add(0, item);
                        break;
                    }
                }
            } catch (Exception e) {
                failure = e.getMessage() == null
                        ? "Could not load movie categories." : e.getMessage();
            }

            final String error = failure;
            final ArrayList<String[]> loadedCategories = categories;
            runOnUiThread(() -> {
                tvVodCategoryList.removeAllViews();

                if (error != null) {
                    tvVodMovieStatus.setText("Unable to load categories: " + error);
                    return;
                }
                tvVodCategoryList.addView(createTvVodCategoryItem(
                        "__ALL_MOVIES__", "ALL MOVIES", base, user, pass));
                if (getActiveMovieProgress().length() > 0) {
                    tvVodCategoryList.addView(createTvVodCategoryItem(
                            "__CONTINUE_WATCHING__", "CONTINUE WATCHING",
                            base, user, pass));
                }
                tvVodCategoryList.addView(createTvVodCategoryItem(
                        "__MY_LIST__", "MY LIST", base, user, pass));

                if (loadedCategories.isEmpty()) {
                    tvVodMovieStatus.setText("No movie categories were returned.");
                    return;
                }

                for (String[] category : loadedCategories) {
                    tvVodCategoryList.addView(createTvVodCategoryItem(
                            category[0], category[1], base, user, pass));
                }

                int movieFixedCount = 0;
                while (movieFixedCount < tvVodCategoryList.getChildCount() && String.valueOf(tvVodCategoryList.getChildAt(movieFixedCount).getTag()).startsWith("__")) movieFixedCount++;
                applySavedCategoryOrder(tvVodCategoryList, "movie_category_order", movieFixedCount);
                String[] first = loadedCategories.get(0);
                loadTvVodMovieGrid(first[0], first[1], base, user, pass);
            });
        }).start();
    }

    private TextView createTvVodCategoryItem(
            String id, String name, String base, String user, String pass) {
        TextView item = label(name, 15);
        item.setTextColor(WHITE);
        item.setBackgroundColor(PANEL);
        item.setFocusable(true);
        item.setClickable(true);
        item.setPadding(dp(10), dp(12), dp(8), dp(12));
        item.setTag(id);
        item.setOnFocusChangeListener((v, focused) ->
                v.setBackgroundColor(focused ? BLUE : PANEL));
        item.setOnClickListener(v -> {
            tvVodSelectedCategoryId = id;
            tvVodSelectedCategoryName = name;
            tvVodMainMenuScroll.setVisibility(View.GONE);
            tvVodCategoryScroll.setVisibility(View.GONE);
            loadTvVodMovieGrid(id, name, base, user, pass);
        });
        item.setOnLongClickListener(v -> {
            int fixedCount = 0;
            while (fixedCount < tvVodCategoryList.getChildCount() && String.valueOf(tvVodCategoryList.getChildAt(fixedCount).getTag()).startsWith("__")) fixedCount++;
            showCategoryMoveMenu(item, tvVodCategoryList, tvVodCategoryScroll, "movie_category_order", fixedCount);
            return true;
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(3), 0, dp(3));
        item.setLayoutParams(params);
        return item;
    }

    private void loadTvVodMovieGrid(
            String categoryId, String categoryName,
            String base, String user, String pass) {
        tvVodSelectedCategoryId = categoryId;
        tvVodSelectedCategoryName = categoryName;
        final int generation = ++tvVodMovieLoadGeneration;

        tvVodMovieStatus.setText("Loading " + categoryName + "...");
        tvVodMovieGrid.removeAllViews();
        tvVodLoadedMovies.clear();
        tvVodNextMovieIndex = 0;
        tvVodGridColumn = 0;
        tvVodCurrentGridRow = null;
        tvVodAppendingMovies = false;

        new Thread(() -> {
            ArrayList<Channel> loaded = new ArrayList<>();
            String failure = null;
            try {
                if ("__MY_LIST__".equals(categoryId)) {
                    loaded.addAll(buildSavedMovieChannels(base, user, pass));
                } else if ("__CONTINUE_WATCHING__".equals(categoryId)) {
                    loaded.addAll(buildContinueWatchingMovies(base, user, pass));
                } else {
                String api = base + "/player_api.php?username="
                        + apiEncode(user) + "&password=" + apiEncode(pass);
                String streamsUrl = api + "&action=get_vod_streams";
                if (!"__ALL_MOVIES__".equals(categoryId)) {
                    streamsUrl += "&category_id=" + apiEncode(categoryId);
                }
                forEachXtreamObject(streamsUrl, item -> {
                    String id = seriesJsonString(item, "stream_id", "");
                    if (id.isEmpty()) return;

                    String extension = seriesJsonString(item, "container_extension", "mp4");
                    String url = base + "/movie/" + Uri.encode(user) + "/"
                            + Uri.encode(pass) + "/" + Uri.encode(id) + "."
                            + Uri.encode(extension);
                    String name = seriesJsonString(item, "name", "Untitled movie");
                    String icon = seriesJsonString(item, "stream_icon", "");

                    Channel movie = new Channel(
                            name, url, categoryName, "", icon);
                    movie.vodId = id;
                    movie.vodExtension = extension;
                    loaded.add(movie);
                });
                }
            } catch (Exception e) {
                failure = e.getMessage() == null
                        ? "Could not load movies." : e.getMessage();
            }

            final String error = failure;
            runOnUiThread(() -> {
                if (generation != tvVodMovieLoadGeneration || !showingTvVod) {
                    return;
                }
                if (error != null) {
                    tvVodMovieStatus.setText("Unable to load movies: " + error);
                    return;
                }
                if (loaded.isEmpty()) {
                    tvVodMovieStatus.setText("0 movies");
                    tvVodMovieGrid.addView(label(
                            "No movies were returned in this category.", 16));
                    return;
                }

                tvVodLoadedMovies = loaded;
                tvVodMovieStatus.setText(loaded.size() + " movies");
                tvVodMovieScroll.post(() -> {
                    boolean tv = isTelevisionDevice();
                    int widthDp = tv
                            ? (int) (tvVodMovieScroll.getWidth()
                                    / getResources().getDisplayMetrics().density)
                            : getResources().getConfiguration().screenWidthDp;
                    tvVodGridColumns = Math.max(tv ? 2 : 3,
                            Math.min(7, widthDp / 170));
                    appendTvVodMovieBatch();
                    focusFirstTvVodPoster();
                });
            });
        }).start();
    }

    private void appendTvVodMovieBatch() {
        if (tvVodAppendingMovies || tvVodLoadedMovies.isEmpty()
                || tvVodNextMovieIndex >= tvVodLoadedMovies.size()) {
            return;
        }

        tvVodAppendingMovies = true;
        int end = Math.min(
                tvVodNextMovieIndex + 40, tvVodLoadedMovies.size());

        while (tvVodNextMovieIndex < end) {
            Channel movie = tvVodLoadedMovies.get(tvVodNextMovieIndex++);

            if (tvVodGridColumn == 0) {
                tvVodCurrentGridRow = new LinearLayout(this);
                tvVodCurrentGridRow.setOrientation(LinearLayout.HORIZONTAL);
                tvVodCurrentGridRow.setGravity(Gravity.TOP);
                tvVodMovieGrid.addView(tvVodCurrentGridRow,
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT));
            }

            LinearLayout card = createMoviePosterCard(movie);
            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            cardParams.setMargins(dp(4), dp(5), dp(4), dp(8));
            tvVodCurrentGridRow.addView(card, cardParams);

            tvVodGridColumn++;
            if (tvVodGridColumn >= tvVodGridColumns) {
                tvVodGridColumn = 0;
                tvVodCurrentGridRow = null;
            }
        }

        if (tvVodNextMovieIndex >= tvVodLoadedMovies.size() && tvVodCurrentGridRow != null) {
            keepIncompletePosterRowWidth(tvVodCurrentGridRow, tvVodGridColumns);
        }
        tvVodAppendingMovies = false;

        tvVodMovieScroll.post(() -> {
            View content = tvVodMovieScroll.getChildAt(0);
            if (content != null
                    && tvVodNextMovieIndex < tvVodLoadedMovies.size()
                    && content.getHeight()
                    <= tvVodMovieScroll.getHeight() + dp(500)) {
                appendTvVodMovieBatch();
            }
        });
    }

    private void keepIncompletePosterRowWidth(
            LinearLayout row, int columns) {
        if (row == null || columns <= 0) return;
        row.post(() -> {
            int cellWidth = row.getWidth() / columns - dp(8);
            if (cellWidth <= 0) return;
            for (int i = 0; i < row.getChildCount(); i++) {
                View child = row.getChildAt(i);
                LinearLayout.LayoutParams params =
                        (LinearLayout.LayoutParams) child.getLayoutParams();
                params.width = cellWidth;
                params.weight = 0f;
                child.setLayoutParams(params);
            }
        });
    }

    private void focusTvVodSelectedCategory() {
        if (tvVodCategoryList == null) return;
        for (int i = 0; i < tvVodCategoryList.getChildCount(); i++) {
            View child = tvVodCategoryList.getChildAt(i);
            if (tvVodSelectedCategoryId.equals(String.valueOf(child.getTag()))) {
                child.requestFocus();
                return;
            }
        }
        if (tvVodCategoryList.getChildCount() > 0) {
            tvVodCategoryList.getChildAt(0).requestFocus();
        }
    }

    private void focusFirstTvVodPoster() {
        if (tvVodMovieGrid == null || tvVodMovieGrid.getChildCount() == 0) return;
        View firstRow = tvVodMovieGrid.getChildAt(0);
        if (firstRow instanceof LinearLayout
                && ((LinearLayout) firstRow).getChildCount() > 0) {
            ((LinearLayout) firstRow).getChildAt(0).requestFocus();
        }
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
                if ("__MY_LIST__".equals(categoryId)) {
                    loaded.addAll(buildSavedMovieChannels(base, user, pass));
                } else if ("__CONTINUE_WATCHING__".equals(categoryId)) {
                    loaded.addAll(buildContinueWatchingMovies(base, user, pass));
                } else {
                String api = base + "/player_api.php?username="
                        + apiEncode(user) + "&password=" + apiEncode(pass);

                String streamsUrl = api + "&action=get_vod_streams";
                if (!"__ALL_MOVIES__".equals(categoryId)) {
                    streamsUrl += "&category_id=" + apiEncode(categoryId);
                }
                JSONArray streams = getXtreamArray(streamsUrl);

                for (int i = 0; i < streams.length(); i++) {
                    JSONObject item = streams.getJSONObject(i);
                    String id = item.optString("stream_id", "");
                    if (id.isEmpty()) continue;

                    String extension = item.optString("container_extension", "mp4");

                    String url = base + "/movie/" + Uri.encode(user) + "/"
                            + Uri.encode(pass) + "/" + Uri.encode(id) + "."
                            + Uri.encode(extension);

                    Channel movie = new Channel(
                            item.optString("name", "Untitled movie"),
                            url,
                            categoryName,
                            "",
                            item.optString("stream_icon", ""));
                    movie.vodId = id;
                    movie.vodExtension = extension;
                    loaded.add(movie);
                }
                }
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

                int screenWidthDp = getResources().getConfiguration().screenWidthDp;
                int columns = screenWidthDp < 600
                        ? 2 : Math.max(4, Math.min(6, screenWidthDp / 170));

                LinearLayout gridRow = null;
                int column = 0;

                for (Channel movie : loaded) {
                    if (column == 0) {
                        gridRow = new LinearLayout(this);
                        gridRow.setOrientation(LinearLayout.HORIZONTAL);
                        gridRow.setGravity(Gravity.TOP);

                        movieList.addView(gridRow,
                                new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT));
                    }

                    LinearLayout card = createMoviePosterCard(movie);
                    LinearLayout.LayoutParams cardParams =
                            new LinearLayout.LayoutParams(
                                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    cardParams.setMargins(dp(4), dp(5), dp(4), dp(8));
                    gridRow.addView(card, cardParams);

                    column++;
                    if (column >= columns) column = 0;
                }

                if (movieList.getChildCount() > 0) {
                    LinearLayout firstRow =
                            (LinearLayout) movieList.getChildAt(0);
                    if (firstRow.getChildCount() > 0) {
                        firstRow.getChildAt(0).requestFocus();
                    }
                }
            });
        }).start();
    }

    private JSONArray getSavedMovieList() {
        String json = getPreferences(MODE_PRIVATE)
                .getString("movie_my_list", "[]");
        try {
            return new JSONArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private boolean isMovieSaved(String vodId) {
        if (vodId == null || vodId.isEmpty()) return false;
        JSONArray saved = getSavedMovieList();
        for (int i = 0; i < saved.length(); i++) {
            JSONObject entry = saved.optJSONObject(i);
            if (entry != null && vodId.equals(entry.optString("id", ""))) {
                return true;
            }
        }
        return false;
    }

    private void saveMovieToList(Channel movie) {
        if (movie == null || movie.vodId == null || movie.vodId.isEmpty()) return;
        JSONArray saved = getSavedMovieList();
        for (int i = 0; i < saved.length(); i++) {
            JSONObject entry = saved.optJSONObject(i);
            if (entry != null && movie.vodId.equals(entry.optString("id", ""))) {
                return;
            }
        }
        JSONObject entry = new JSONObject();
        try {
            entry.put("id", movie.vodId);
            entry.put("name", movie.name);
            entry.put("logo", movie.logoUrl);
            entry.put("extension", movie.vodExtension);
            saved.put(entry);
            getPreferences(MODE_PRIVATE).edit()
                    .putString("movie_my_list", saved.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void removeMovieFromList(String vodId) {
        JSONArray saved = getSavedMovieList();
        JSONArray updated = new JSONArray();
        for (int i = 0; i < saved.length(); i++) {
            JSONObject entry = saved.optJSONObject(i);
            if (entry != null && !vodId.equals(entry.optString("id", ""))) {
                updated.put(entry);
            }
        }
        getPreferences(MODE_PRIVATE).edit()
                .putString("movie_my_list", updated.toString()).apply();
    }

    private JSONArray getSavedSeriesList() {
        String json = getPreferences(MODE_PRIVATE).getString("series_my_list", "[]");
        try { return new JSONArray(json); }
        catch (Exception e) { return new JSONArray(); }
    }

    private boolean isSeriesSaved(String id) {
        if (id == null || id.isEmpty()) return false;
        JSONArray saved = getSavedSeriesList();
        for (int i = 0; i < saved.length(); i++) {
            JSONObject entry = saved.optJSONObject(i);
            if (entry != null && id.equals(entry.optString("id", ""))) return true;
        }
        return false;
    }

    private void saveSeriesToList(Channel series) {
        if (series == null || series.url == null || series.url.isEmpty()) return;
        if (isSeriesSaved(series.url)) return;
        JSONObject entry = new JSONObject();
        try {
            entry.put("id", series.url);
            entry.put("name", series.name);
            entry.put("logo", series.logoUrl);
            JSONArray saved = getSavedSeriesList();
            saved.put(entry);
            getPreferences(MODE_PRIVATE).edit()
                    .putString("series_my_list", saved.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void removeSeriesFromList(String id) {
        JSONArray saved = getSavedSeriesList();
        JSONArray updated = new JSONArray();
        for (int i = 0; i < saved.length(); i++) {
            JSONObject entry = saved.optJSONObject(i);
            if (entry != null && !id.equals(entry.optString("id", ""))) {
                updated.put(entry);
            }
        }
        getPreferences(MODE_PRIVATE).edit()
                .putString("series_my_list", updated.toString()).apply();
    }

    private ArrayList<Channel> buildSavedMovieChannels(
            String base, String user, String pass) {
        ArrayList<Channel> movies = new ArrayList<>();
        JSONArray saved = getSavedMovieList();
        for (int i = 0; i < saved.length(); i++) {
            JSONObject entry = saved.optJSONObject(i);
            if (entry == null) continue;
            String id = entry.optString("id", "");
            if (id.isEmpty()) continue;
            String extension = entry.optString("extension", "mp4");
            String url = base + "/movie/" + Uri.encode(user) + "/"
                    + Uri.encode(pass) + "/" + Uri.encode(id) + "."
                    + Uri.encode(extension);
            Channel movie = new Channel(
                    entry.optString("name", "Untitled movie"),
                    url, "MY LIST", "", entry.optString("logo", ""));
            movie.vodId = id;
            movie.vodExtension = extension;
            movies.add(movie);
        }
        return movies;
    }


    private JSONObject getActiveMovieProgress() {
        JSONObject result = new JSONObject();
        try {
            String playlist = getPreferences(MODE_PRIVATE)
                    .getString("active_playlist_id", "");
            JSONObject store = new JSONObject(getSharedPreferences(
                    "vod_progress", MODE_PRIVATE).getString("items", "{}"));
            ArrayList<JSONObject> entries = new ArrayList<>();
            java.util.Iterator<String> keys = store.keys();
            while (keys.hasNext()) {
                JSONObject item = store.optJSONObject(keys.next());
                if (item == null || !"movie".equals(item.optString("type"))
                        || !playlist.equals(item.optString("playlist_id"))
                        || item.optString("vod_id").isEmpty()) continue;
                entries.add(item);
            }
            Collections.sort(entries, (a, b) ->
                    Long.compare(b.optLong("updated"), a.optLong("updated")));
            for (int i = 0; i < entries.size(); i++) {
                result.put(String.valueOf(i), entries.get(i));
            }
        } catch (Exception e) {
            android.util.Log.e("StreamNest", "Could not read movie progress", e);
        }
        return result;
    }

    private ArrayList<Channel> buildContinueWatchingMovies(
            String base, String user, String pass) {
        ArrayList<Channel> movies = new ArrayList<>();
        JSONObject progress = getActiveMovieProgress();
        for (int i = 0; i < progress.length(); i++) {
            JSONObject item = progress.optJSONObject(String.valueOf(i));
            if (item == null) continue;
            String id = item.optString("vod_id", "");
            String extension = item.optString("extension", "mp4");
            String url = base + "/movie/" + Uri.encode(user) + "/"
                    + Uri.encode(pass) + "/" + Uri.encode(id) + "."
                    + Uri.encode(extension);
            Channel movie = new Channel(item.optString("title", "Movie"),
                    url, "CONTINUE WATCHING", "",
                    item.optString("artwork", ""));
            movie.vodId = id;
            movie.vodExtension = extension;
            movies.add(movie);
        }
        return movies;
    }

    private ArrayList<JSONObject> getActiveSeriesProgress() {
        ArrayList<JSONObject> entries = new ArrayList<>();
        try {
            String playlist = getPreferences(MODE_PRIVATE)
                    .getString("active_playlist_id", "");
            JSONObject store = new JSONObject(getSharedPreferences(
                    "vod_progress", MODE_PRIVATE).getString("items", "{}"));
            java.util.Iterator<String> keys = store.keys();
            while (keys.hasNext()) {
                JSONObject item = store.optJSONObject(keys.next());
                if (item == null || !"episode".equals(item.optString("type"))
                        || !playlist.equals(item.optString("playlist_id"))
                        || item.optString("series_id").isEmpty()
                        || item.optString("episode_id").isEmpty()) continue;
                entries.add(item);
            }
            Collections.sort(entries, (a, b) ->
                    Long.compare(b.optLong("updated"), a.optLong("updated")));
            ArrayList<JSONObject> unique = new ArrayList<>();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (JSONObject item : entries) {
                if (seen.add(item.optString("series_id"))) unique.add(item);
            }
            return unique;
        } catch (Exception e) {
            android.util.Log.e("StreamNest", "Could not read series progress", e);
            return entries;
        }
    }

    private ArrayList<Channel> buildContinueWatchingSeries() {
        ArrayList<Channel> shows = new ArrayList<>();
        for (JSONObject item : getActiveSeriesProgress()) {
            Channel series = new Channel(item.optString("series_name", "Series"),
                    item.optString("series_id"), "CONTINUE WATCHING", "",
                    item.optString("series_artwork", ""));
            series.resumeEpisodeId = item.optString("episode_id", "");
            shows.add(series);
        }
        return shows;
    }

    private ArrayList<Channel> buildSavedSeriesChannels() {
        ArrayList<Channel> shows = new ArrayList<>();
        JSONArray saved = getSavedSeriesList();
        for (int i = 0; i < saved.length(); i++) {
            JSONObject item = saved.optJSONObject(i);
            if (item == null || item.optString("id").isEmpty()) continue;
            shows.add(new Channel(item.optString("name", "Series"),
                    item.optString("id"), "FAVORITES", "",
                    item.optString("logo", "")));
        }
        return shows;
    }

    private void showMovieDetails(Channel movie, View returnFocus) {
        if (movie == null) return;

        String base = getPreferences(MODE_PRIVATE).getString("xtream_server", "");
        String user = getPreferences(MODE_PRIVATE).getString("xtream_username", "");
        String pass = getPreferences(MODE_PRIVATE).getString("xtream_password", "");

        showingMovieDetails = true;
        movieGridScreen = screen;
        movieReturnFocus = returnFocus;

        boolean tvDetails = isTelevisionDevice();

        LinearLayout detailsScreen = new LinearLayout(this);
        detailsScreen.setOrientation(LinearLayout.VERTICAL);
        detailsScreen.setBackgroundColor(NAVY);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(20), dp(20), dp(20));

        android.widget.FrameLayout hero = new android.widget.FrameLayout(this);
        hero.setBackgroundColor(tvDetails ? NAVY : Color.rgb(25, 35, 50));
        android.widget.ImageView backdrop = new android.widget.ImageView(this);
        if (tvDetails) {
            backdrop.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            int artworkWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.75f);
            hero.addView(backdrop, new android.widget.FrameLayout.LayoutParams(
                    artworkWidth, -1, Gravity.RIGHT | Gravity.TOP));
        } else {
            backdrop.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            hero.addView(backdrop, new android.widget.FrameLayout.LayoutParams(-1, -1));
        }
        if (!tvDetails) {
            View shade = new View(this);
            shade.setBackgroundColor(0x77000000);
            hero.addView(shade,
                    new android.widget.FrameLayout.LayoutParams(-1, -1));
        }
        if (tvDetails) {
            int leftFadeWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.75f);
            int bottomFadeHeight = dp(170);

            View leftFade = new View(this);
            leftFade.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{NAVY, NAVY, 0xFF101629, 0xCC101629, Color.TRANSPARENT}));
            android.widget.FrameLayout.LayoutParams leftParams =
                    new android.widget.FrameLayout.LayoutParams(
                            leftFadeWidth, -1, Gravity.LEFT);
            hero.addView(leftFade, leftParams);

            View bottomFade = new View(this);
            bottomFade.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.TRANSPARENT, NAVY}));
            android.widget.FrameLayout.LayoutParams bottomParams =
                    new android.widget.FrameLayout.LayoutParams(
                            -1, bottomFadeHeight, Gravity.BOTTOM);
            hero.addView(bottomFade, bottomParams);
        }
        if (!tvDetails) {
            content.addView(hero, new LinearLayout.LayoutParams(
                    -1, dp(210)));
        }

        TextView title = label(movie.name, 26);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp(16), 0, dp(8));
        content.addView(title);

        TextView meta = label("Loading movie information...", 14);
        meta.setTextColor(MUTED);
        content.addView(meta);

        TextView plot = label("Loading description...", 16);
        plot.setTextColor(Color.WHITE);
        plot.setPadding(0, dp(14), 0, dp(12));
        content.addView(plot);

        TextView cast = label("", 14);
        cast.setTextColor(MUTED);
        content.addView(cast);

        TextView director = label("", 14);
        director.setTextColor(MUTED);
        content.addView(director);

        TextView play = label("▶  PLAY", 20);
        play.setTextColor(Color.WHITE);
        play.setTypeface(null, Typeface.BOLD);
        play.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        play.setFocusable(true);
        play.setClickable(true);
        play.setPadding(0, dp(14), 0, dp(14));
        play.setBackgroundColor(Color.TRANSPARENT);
        play.setOnFocusChangeListener((v, focused) ->
                play.setTextColor(focused ? Color.YELLOW : Color.WHITE));
        play.setOnClickListener(v -> playVodItem(movie));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                -1, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.setMargins(0, dp(18), 0, dp(8));
        content.addView(play, actionParams);

        TextView myList = label("", 16);
        myList.setTextColor(Color.WHITE);
        myList.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        myList.setFocusable(true);
        myList.setClickable(true);
        myList.setPadding(0, dp(13), 0, dp(13));
        myList.setBackgroundColor(Color.TRANSPARENT);
        Runnable updateMyListText = () -> myList.setText(
                isMovieSaved(movie.vodId)
                        ? "✓  REMOVE FROM MY LIST"
                        : "+  ADD TO MY LIST");
        updateMyListText.run();
        myList.setOnFocusChangeListener((v, focused) ->
                myList.setTextColor(focused ? Color.YELLOW : Color.WHITE));
        myList.setOnClickListener(v -> {
            if (isMovieSaved(movie.vodId)) {
                removeMovieFromList(movie.vodId);
            } else {
                saveMovieToList(movie);
            }
            updateMyListText.run();
        });
        content.addView(myList);

        TextView back = label("‹  BACK", 16);
        back.setTextColor(MUTED);
        back.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        back.setFocusable(true);
        back.setClickable(true);
        back.setPadding(0, dp(14), 0, dp(14));
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnFocusChangeListener((v, focused) ->
                back.setTextColor(focused ? Color.YELLOW : MUTED));
        back.setOnClickListener(v -> onBackPressed());
        content.addView(back);

        TextView external = label("EXTERNAL PLAYER", 16);
        external.setTextColor(Color.WHITE);
        external.setGravity(Gravity.CENTER);
        external.setFocusable(true);
        external.setClickable(true);
        external.setOnClickListener(v -> playVodExternal(movie));

        if (tvDetails) {
            content.removeView(play);
            content.removeView(myList);
            content.removeView(back);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);

            TextView[] actionViews = {myList, play, external};
            for (TextView action : actionViews) {
                action.setGravity(Gravity.CENTER);
                action.setTextSize(14);
                action.setPadding(dp(4), dp(12), dp(4), dp(12));
                action.setTextColor(Color.WHITE);
                action.setOnFocusChangeListener((v, focused) -> {
                    if (focused) {
                        GradientDrawable selected = new GradientDrawable();
                        selected.setColor(0xFF1769C2);
                        selected.setCornerRadius(dp(6));
                        selected.setStroke(dp(2), 0xFF55A5FF);
                        action.setBackground(selected);
                    } else {
                        action.setBackgroundColor(Color.TRANSPARENT);
                    }
                });
                actions.addView(action,
                        new LinearLayout.LayoutParams(0, -2, 1f));
            }

            actions.setFocusable(false);
            content.addView(actions);
        }

        scroll.addView(content);
        if (tvDetails) {
            content.removeView(hero);
            content.removeView(title);
            content.removeView(meta);
            content.removeView(plot);

            View actions = content.getChildAt(content.getChildCount() - 1);
            content.removeView(actions);

            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(dp(24), dp(12), 0, dp(8));
            details.addView(title);
            details.addView(meta);
            details.addView(plot);

            int detailsWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.45f);
            hero.addView(details, new android.widget.FrameLayout.LayoutParams(
                    detailsWidth, -1, Gravity.LEFT | Gravity.TOP));

            detailsScreen.addView(hero,
                    new LinearLayout.LayoutParams(-1, dp(340)));
            detailsScreen.addView(scroll,
                    new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout.LayoutParams bottomActionsParams =
                    new LinearLayout.LayoutParams(-1, dp(64));
            bottomActionsParams.setMargins(dp(24), 0, dp(24), dp(12));
            detailsScreen.addView(actions, bottomActionsParams);
        } else {
            detailsScreen.addView(scroll,
                    new LinearLayout.LayoutParams(-1, 0, 1f));
        }

        screen = detailsScreen;
        setContentView(screen);

        if (movie.logoUrl != null && !movie.logoUrl.isEmpty()) {
            loadMovieBackdrop(backdrop, movie.logoUrl);
        }
        play.requestFocus();

        if (base.isEmpty() || user.isEmpty() || pass.isEmpty()
                || movie.vodId == null || movie.vodId.isEmpty()) {
            meta.setText("Movie information is unavailable.");
            plot.setText("You can still play this movie.");
            return;
        }

        new Thread(() -> {
            JSONObject info = null;
            String error = null;
            try {
                String api = base + "/player_api.php?username="
                        + apiEncode(user) + "&password=" + apiEncode(pass);
                JSONObject response = getXtreamObject(api
                        + "&action=get_vod_info&vod_id="
                        + apiEncode(movie.vodId));
                info = response.optJSONObject("info");
                if (info == null) error = "No movie details were returned.";
            } catch (Exception e) {
                error = e.getMessage() == null
                        ? "Could not load movie details." : e.getMessage();
            }

            final JSONObject result = info;
            final String failure = error;
            runOnUiThread(() -> {
                if (screen != detailsScreen) return;
                if (result == null) {
                    meta.setText(failure);
                    plot.setText("You can still play this movie.");
                    return;
                }

                String description = result.optString("plot", "");
                plot.setText(description.isEmpty()
                        ? "No description available." : description);

                String genre = result.optString("genre", "");
                String released = result.optString("releasedate", "");
                String rating = result.optString("rating", "");
                String details = "";
                if (!released.isEmpty()) details += released;
                if (!genre.isEmpty()) details += (details.isEmpty() ? "" : "  •  ") + genre;
                if (!rating.isEmpty()) details += (details.isEmpty() ? "" : "  •  ")
                        + "Rating: " + rating;
                meta.setText(details.isEmpty() ? "Movie details" : details);

                Object castValue = result.opt("cast");
                String castText = "";
                if (castValue instanceof JSONArray) {
                    JSONArray names = (JSONArray) castValue;
                    StringBuilder joined = new StringBuilder();
                    for (int i = 0; i < names.length(); i++) {
                        String name = names.optString(i, "");
                        if (!name.isEmpty()) {
                            if (joined.length() > 0) joined.append(", ");
                            joined.append(name);
                        }
                    }
                    castText = joined.toString();
                } else if (castValue != null && !JSONObject.NULL.equals(castValue)) {
                    castText = String.valueOf(castValue);
                }
                cast.setText(castText.isEmpty() ? "" : "CAST: " + castText);

                String directorName = result.optString("director", "");
                director.setText(directorName.isEmpty()
                        ? "" : "DIRECTOR: " + directorName);

                String imageUrl = "";
                JSONArray backdrops = result.optJSONArray("backdrop_path");
                if (backdrops != null && backdrops.length() > 0) {
                    imageUrl = backdrops.optString(0, "");
                }
                if (imageUrl.isEmpty()) {
                    imageUrl = result.optString("movie_image", "");
                }
                if (!imageUrl.isEmpty()) {
                    if (!imageUrl.startsWith("http")) {
                        imageUrl = base + "/" + imageUrl.replaceFirst("^/+", "");
                    }
                    loadMovieBackdrop(backdrop, imageUrl);
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
        intent.putExtra("vod_id", item.vodId);
        intent.putExtra("vod_extension", item.vodExtension);
        intent.putExtra("vod_artwork", item.logoUrl);
        intent.putExtra("is_live_stream", false);
        intent.putExtra("active_playlist_id", getPreferences(MODE_PRIVATE).getString("active_playlist_id", ""));
        intent.putExtra("show_on_screen_clock",
                getPreferences(MODE_PRIVATE).getBoolean("show_on_screen_clock", true));
        startActivity(intent);
    }

    private void playSeriesEpisode(Channel series, ArrayList<Channel> episodes, int index) {
        if (episodes == null || index < 0 || index >= episodes.size()) return;

        ArrayList<String> urls = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> episodeIds = new ArrayList<>();
        ArrayList<String> episodeArtwork = new ArrayList<>();

        for (int i = index; i < episodes.size(); i++) {
            Channel episode = episodes.get(i);
            if (episode == null || episode.url == null
                    || episode.url.trim().isEmpty()) continue;
            urls.add(episode.url);
            names.add(episode.name == null ? "Episode" : episode.name);
            episodeIds.add(episode.vodId == null ? "" : episode.vodId);
            episodeArtwork.add(episode.episodeArtwork == null
                    ? "" : episode.episodeArtwork);
        }

        if (urls.isEmpty()) return;

        releaseGuidePreviewPlayer();
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("channel_name", names.get(0));
        intent.putExtra("stream_url", urls.get(0));
        intent.putExtra("is_live_stream", false);
        intent.putExtra("active_playlist_id", getPreferences(MODE_PRIVATE).getString("active_playlist_id", ""));
        intent.putExtra("show_on_screen_clock",
                getPreferences(MODE_PRIVATE).getBoolean("show_on_screen_clock", true));
        intent.putStringArrayListExtra("series_episode_urls", urls);
        intent.putStringArrayListExtra("series_episode_names", names);
        intent.putStringArrayListExtra("series_episode_ids", episodeIds);
        intent.putStringArrayListExtra("series_episode_artwork", episodeArtwork);
        intent.putExtra("series_id", episodes.get(index).xtreamStreamId);
        intent.putExtra("series_name", series.name);
        intent.putExtra("series_artwork", series.logoUrl);
        intent.putExtra("auto_play_next_episode",
                getPreferences(MODE_PRIVATE).getBoolean("auto_play_next_episode", false));
        startActivity(intent);
    }

    private void playVodExternal(Channel item) {
        if (item == null || item.url == null || item.url.trim().isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(item.url), "video/*");
            startActivity(Intent.createChooser(intent, "Play movie with"));
        } catch (Exception e) {
            Toast.makeText(this, "No compatible external player found.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showSeries() {
        if (isTelevisionDevice()) {
            showTvSeries();
            return;
        }
        showPhoneSeries();
    }

    private void showPhoneSeries() {
        showingTvVod = false;
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

    private void showTvSeries() {
        showingTvSeriesEpisodes = false;
        String base = getPreferences(MODE_PRIVATE).getString("xtream_server", "");
        String user = getPreferences(MODE_PRIVATE).getString("xtream_username", "");
        String pass = getPreferences(MODE_PRIVATE).getString("xtream_password", "");

        if (base.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("TV Series")
                    .setMessage("Connect your Xtream Codes account in Settings.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        releaseGuidePreview();
        showingLiveTv = false;
        showingTvGuide = false;
        showingTvVod = false;
        showingTvSeries = true;

        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(NAVY);
        screen.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView heading = label("TV SERIES", 24);
        heading.setTextColor(Color.rgb(95, 190, 255));
        heading.setTypeface(null, Typeface.BOLD);
        screen.addView(heading);

        tvSeriesStatus = label("Loading your series library...", 14);
        tvSeriesStatus.setTextColor(MUTED);
        screen.addView(tvSeriesStatus);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        screen.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        tvSeriesMainMenuScroll = new ScrollView(this);
        tvSeriesMainMenuScroll.setVisibility(View.GONE);

        LinearLayout seriesMainMenu = new LinearLayout(this);
        seriesMainMenu.setOrientation(LinearLayout.VERTICAL);
        seriesMainMenu.setPadding(dp(5), dp(5), dp(5), dp(5));
        addHomeSection(seriesMainMenu, "SEARCH", "Find live TV channels");
        addHomeSection(seriesMainMenu, "TV GUIDE", "Browse the TV schedule");
        addHomeSection(seriesMainMenu, "MOVIES", "Browse movies");
        addHomeSection(seriesMainMenu, "TV SERIES", "Browse TV series");
        addHomeSection(seriesMainMenu, "CATCH-UP", "Recently aired programs");
        addHomeSection(seriesMainMenu, "SETTINGS", "Playlist and app settings");

        tvSeriesMainMenuScroll.addView(seriesMainMenu);
        body.addView(tvSeriesMainMenuScroll, new LinearLayout.LayoutParams(
                dp(190), LinearLayout.LayoutParams.MATCH_PARENT));

        tvSeriesCategoryScroll = new ScrollView(this);
        tvSeriesCategoryScroll.setVisibility(View.GONE);

        tvSeriesCategoryList = new LinearLayout(this);
        tvSeriesCategoryList.setOrientation(LinearLayout.VERTICAL);
        tvSeriesCategoryList.setPadding(dp(5), dp(5), dp(5), dp(5));
        tvSeriesCategoryScroll.addView(tvSeriesCategoryList);

        body.addView(tvSeriesCategoryScroll, new LinearLayout.LayoutParams(
                dp(190), LinearLayout.LayoutParams.MATCH_PARENT));

        tvSeriesGridScroll = new ScrollView(this);
        tvSeriesGrid = new LinearLayout(this);
        tvSeriesGrid.setOrientation(LinearLayout.VERTICAL);
        tvSeriesGrid.setPadding(dp(5), dp(5), dp(5), dp(5));
        tvSeriesGridScroll.addView(tvSeriesGrid);

        body.addView(tvSeriesGridScroll, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        tvSeriesGridScroll.setOnScrollChangeListener(
                (View v, int x, int y, int oldX, int oldY) -> {
            View content = tvSeriesGridScroll.getChildAt(0);
            if (content != null
                    && y + tvSeriesGridScroll.getHeight()
                    >= content.getHeight() - dp(500)) {
                appendTvSeriesShowBatch();
            }
        });

        tvSeriesGridScreen = screen;
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
            } catch (Exception e) {
                failure = e.getMessage() == null
                        ? "Could not load series categories." : e.getMessage();
            }

            final String error = failure;
            runOnUiThread(() -> {
                tvSeriesCategoryList.removeAllViews();
                ArrayList<String[]> allCategories = new ArrayList<>();
                if (!getActiveSeriesProgress().isEmpty()) {
                    allCategories.add(new String[]{
                            "__SERIES_CONTINUE_WATCHING__", "CONTINUE WATCHING"});
                }
                allCategories.add(new String[]{"__SERIES_FAVORITES__", "FAVORITES"});
                if (error == null) {
                    allCategories.addAll(categories);
                } else {
                    tvSeriesStatus.setText("Unable to load provider categories: " + error);
                }

                for (String[] category : allCategories) {
                    tvSeriesCategoryList.addView(createTvSeriesCategoryItem(
                            category[0], category[1], base, user, pass));
                }

                int seriesFixedCount = 0;
                while (seriesFixedCount < tvSeriesCategoryList.getChildCount() && String.valueOf(tvSeriesCategoryList.getChildAt(seriesFixedCount).getTag()).startsWith("__")) seriesFixedCount++;
                applySavedCategoryOrder(tvSeriesCategoryList, "series_category_order", seriesFixedCount);
                String[] selected = allCategories.get(0);
                for (String[] category : allCategories) {
                    if (category[0].equals(tvSeriesSelectedCategoryId)) {
                        selected = category;
                        break;
                    }
                }
                loadTvSeriesGrid(selected[0], selected[1], base, user, pass);
            });
        }).start();
    }

    private TextView createTvSeriesCategoryItem(
            String id, String name, String base, String user, String pass) {
        TextView item = label(name, 15);
        item.setTextColor(WHITE);
        item.setBackgroundColor(PANEL);
        item.setFocusable(true);
        item.setClickable(true);
        item.setPadding(dp(10), dp(12), dp(8), dp(12));
        item.setTag(id);
        item.setOnFocusChangeListener((v, focused) ->
                v.setBackgroundColor(focused ? BLUE : PANEL));
        item.setOnClickListener(v -> {
            tvSeriesSelectedCategoryId = id;
            tvSeriesSelectedCategoryName = name;
            tvSeriesCategoryScroll.setVisibility(View.GONE);
            loadTvSeriesGrid(id, name, base, user, pass);
        });

        item.setOnLongClickListener(v -> {
            int fixedCount = 0;
            while (fixedCount < tvSeriesCategoryList.getChildCount() && String.valueOf(tvSeriesCategoryList.getChildAt(fixedCount).getTag()).startsWith("__")) fixedCount++;
            showCategoryMoveMenu(item, tvSeriesCategoryList, tvSeriesCategoryScroll, "series_category_order", fixedCount);
            return true;
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(3), 0, dp(3));
        item.setLayoutParams(params);
        return item;
    }

    private void focusTvSeriesSelectedCategory() {
        if (tvSeriesCategoryList == null) return;
        for (int i = 0; i < tvSeriesCategoryList.getChildCount(); i++) {
            View child = tvSeriesCategoryList.getChildAt(i);
            if (tvSeriesSelectedCategoryId.equals(String.valueOf(child.getTag()))) {
                child.requestFocus();
                return;
            }
        }
        if (tvSeriesCategoryList.getChildCount() > 0) {
            tvSeriesCategoryList.getChildAt(0).requestFocus();
        }
    }

    private void focusFirstTvSeriesPoster() {
        if (tvSeriesGrid == null || tvSeriesGrid.getChildCount() == 0) return;
        View firstRow = tvSeriesGrid.getChildAt(0);
        if (firstRow instanceof LinearLayout
                && ((LinearLayout) firstRow).getChildCount() > 0) {
            ((LinearLayout) firstRow).getChildAt(0).requestFocus();
        }
    }

    private void loadTvSeriesGrid(
            String categoryId, String categoryName,
            String base, String user, String pass) {
        tvSeriesArtworkExecutor.shutdownNow();
        tvSeriesArtworkExecutor = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(),
                new ThreadPoolExecutor.AbortPolicy());

        final int generation = ++tvSeriesLoadGeneration;
        tvSeriesSelectedCategoryId = categoryId;
        tvSeriesSelectedCategoryName = categoryName;
        tvSeriesStatus.setText("Loading " + categoryName + "...");
        tvSeriesGrid.removeAllViews();
        tvSeriesLoadedShows.clear();
        tvSeriesNextShowIndex = 0;
        tvSeriesGridColumn = 0;
        tvSeriesCurrentGridRow = null;
        tvSeriesAppendingShows = false;

        new Thread(() -> {
            ArrayList<Channel> loaded = new ArrayList<>();
            String failure = null;
            try {
                    if ("__SERIES_CONTINUE_WATCHING__".equals(categoryId)) {
                        loaded.addAll(buildContinueWatchingSeries());
                    } else if ("__SERIES_FAVORITES__".equals(categoryId)) {
                        loaded.addAll(buildSavedSeriesChannels());
                    } else {
                        String api = base + "/player_api.php?username="
                                + apiEncode(user) + "&password=" + apiEncode(pass);
                        String streamsUrl = api + "&action=get_series&category_id="
                                + apiEncode(categoryId);

                        forEachXtreamObject(streamsUrl, item -> {
                            String id = item.has("series_id")
                                    && !item.get("series_id").isJsonNull()
                                    ? item.get("series_id").getAsString() : "";
                            if (id.isEmpty()) return;

                            String name = item.has("name")
                                    && !item.get("name").isJsonNull()
                                    ? item.get("name").getAsString() : "Untitled series";
                            String cover = item.has("cover")
                                    && !item.get("cover").isJsonNull()
                                    ? item.get("cover").getAsString() : "";

                            loaded.add(new Channel(
                                    name, id, categoryName, "", cover));
                        });
                    }
                } catch (Exception e) {
                failure = e.getMessage() == null
                        ? "Could not load shows." : e.getMessage();
            }

            final String error = failure;
            runOnUiThread(() -> {
                if (generation != tvSeriesLoadGeneration
                        || !showingTvSeries
                        || !categoryId.equals(tvSeriesSelectedCategoryId)) {
                    return;
                }

                tvSeriesGrid.removeAllViews();
                if (error != null) {
                    tvSeriesStatus.setText("Unable to load shows: " + error);
                    tvSeriesGrid.addView(label(
                            "Check your Xtream login and connection.", 14));
                    return;
                }

                tvSeriesStatus.setText(loaded.size() + " shows");
                if (loaded.isEmpty()) {
                    tvSeriesGrid.addView(label(
                            "No shows were returned in this category.", 16));
                    return;
                }

                int width = getResources().getConfiguration().screenWidthDp;
                tvSeriesGridColumns = Math.max(3, Math.min(7, width / 170));
                tvSeriesLoadedShows = loaded;
                appendTvSeriesShowBatch();
                focusFirstTvSeriesPoster();
            });
        }).start();
    }

    private void appendTvSeriesShowBatch() {
        if (tvSeriesAppendingShows
                || tvSeriesLoadedShows.isEmpty()
                || tvSeriesNextShowIndex >= tvSeriesLoadedShows.size()
                || !showingTvSeries) {
            return;
        }

        tvSeriesAppendingShows = true;
        int end = Math.min(
                tvSeriesNextShowIndex + 40, tvSeriesLoadedShows.size());

        while (tvSeriesNextShowIndex < end) {
            Channel series = tvSeriesLoadedShows.get(tvSeriesNextShowIndex);

            if (tvSeriesGridColumn == 0) {
                tvSeriesCurrentGridRow = new LinearLayout(this);
                tvSeriesCurrentGridRow.setOrientation(
                        LinearLayout.HORIZONTAL);
                tvSeriesCurrentGridRow.setGravity(Gravity.TOP);
                tvSeriesGrid.addView(tvSeriesCurrentGridRow,
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT));
            }

            LinearLayout card = createSeriesPosterCard(
                    series,
                    getPreferences(MODE_PRIVATE).getString(
                            "xtream_server", ""),
                    getPreferences(MODE_PRIVATE).getString(
                            "xtream_username", ""),
                    getPreferences(MODE_PRIVATE).getString(
                            "xtream_password", ""));

            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            cardParams.setMargins(dp(4), dp(5), dp(4), dp(8));
            tvSeriesCurrentGridRow.addView(card, cardParams);

            tvSeriesGridColumn++;
            if (tvSeriesGridColumn >= tvSeriesGridColumns) {
                tvSeriesGridColumn = 0;
                tvSeriesCurrentGridRow = null;
            }
            tvSeriesNextShowIndex++;
        }

        if (tvSeriesNextShowIndex >= tvSeriesLoadedShows.size()
                && tvSeriesCurrentGridRow != null) {
            keepIncompletePosterRowWidth(
                    tvSeriesCurrentGridRow, tvSeriesGridColumns);
        }
        tvSeriesAppendingShows = false;
        tvSeriesGridScroll.post(() -> {
            View content = tvSeriesGridScroll.getChildAt(0);
            if (content != null
                    && tvSeriesNextShowIndex < tvSeriesLoadedShows.size()
                    && content.getHeight()
                    <= tvSeriesGridScroll.getHeight() + dp(500)) {
                appendTvSeriesShowBatch();
            }
        });
    }

    private LinearLayout createSeriesPosterCard(
            Channel series, String base, String user, String pass) {

        boolean tv = isTelevisionDevice();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackgroundColor(PANEL);
        card.setPadding(dp(4), dp(4), dp(4), dp(tv ? 3 : 6));

        android.widget.ImageView poster = new android.widget.ImageView(this);
        poster.setLayoutParams(new LinearLayout.LayoutParams(
                tv ? LinearLayout.LayoutParams.MATCH_PARENT : dp(116),
                dp(tv ? 250 : 164)));
        poster.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        if (tv) {
            GradientDrawable posterBackground = new GradientDrawable();
            posterBackground.setColor(Color.rgb(35, 45, 62));
            posterBackground.setCornerRadius(dp(8));
            poster.setBackground(posterBackground);
            poster.setClipToOutline(true);
        } else {
            poster.setBackgroundColor(Color.rgb(35, 45, 62));
        }

        if (series.logoUrl != null && !series.logoUrl.isEmpty()) {
            loadChannelLogo(poster, series.logoUrl, 300, tvSeriesArtworkExecutor);
        }

        card.addView(poster);

        TextView name = label(series.name, tv ? 14 : 13);
        if (tv) {
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
            name.setMarqueeRepeatLimit(-1);
            name.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
            name.setPadding(dp(3), 0, dp(3), 0);
        } else {
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            name.setPadding(dp(3), dp(6), dp(3), dp(2));
        }

        card.addView(name, new LinearLayout.LayoutParams(
                tv ? LinearLayout.LayoutParams.MATCH_PARENT : dp(116),
                dp(tv ? 32 : 48)));

        card.setOnFocusChangeListener((v, focused) -> {
            v.setBackgroundColor(focused ? BLUE : PANEL);
            name.setSelected(tv && focused);
        });

        card.setOnClickListener(v -> {
            tvSeriesReturnFocus = v;
            tvSeriesReturnScrollY = tvSeriesGridScroll != null
                    ? tvSeriesGridScroll.getScrollY() : 0;
            showSeriesEpisodes(series, base, user, pass);
        });

        return card;
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

                String streamsUrl = api + "&action=get_series&category_id="
                        + apiEncode(categoryId);

                forEachXtreamObject(streamsUrl, item -> {
                    String id = item.has("series_id")
                            && !item.get("series_id").isJsonNull()
                            ? item.get("series_id").getAsString() : "";
                    if (id.isEmpty()) return;

                    String name = item.has("name")
                            && !item.get("name").isJsonNull()
                            ? item.get("name").getAsString() : "Untitled series";
                    String cover = item.has("cover")
                            && !item.get("cover").isJsonNull()
                            ? item.get("cover").getAsString() : "";

                    loaded.add(new Channel(
                            name, id, categoryName, "", cover));
                });
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

                int screenWidthDp =
                        getResources().getConfiguration().screenWidthDp;

                int columns = screenWidthDp < 600
                        ? 2 : Math.max(4, Math.min(6, screenWidthDp / 170));

                LinearLayout gridRow = null;
                int column = 0;

                for (Channel series : loaded) {
                    if (column == 0) {
                        gridRow = new LinearLayout(this);
                        gridRow.setOrientation(LinearLayout.HORIZONTAL);
                        gridRow.setGravity(Gravity.TOP);

                        seriesList.addView(gridRow,
                                new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT));
                    }

                    LinearLayout card = createSeriesPosterCard(
                            series, base, user, pass);

                    LinearLayout.LayoutParams cardParams =
                            new LinearLayout.LayoutParams(
                                    0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    1f);

                    cardParams.setMargins(
                            dp(4), dp(5), dp(4), dp(8));

                    gridRow.addView(card, cardParams);

                    column++;
                    if (column >= columns) column = 0;
                }

                if (seriesList.getChildCount() > 0) {
                    LinearLayout firstRow =
                            (LinearLayout) seriesList.getChildAt(0);

                    if (firstRow.getChildCount() > 0) {
                        firstRow.getChildAt(0).requestFocus();
                    }
                }
            });
        }).start();
    }

    private void showSeriesEpisodes(Channel series, String base, String user, String pass) {
        releaseGuidePreview();
        tvSeriesLoadGeneration++;
        showingTvSeriesEpisodes = isTelevisionDevice();
        showingTvSeries = false;
        showingLiveTv = false;
        showingTvGuide = false;

        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(NAVY);
        screen.setPadding(dp(12), dp(8), dp(12), dp(8));

        boolean tvLayout = isTelevisionDevice();
        if (tvLayout) screen.setPadding(0, 0, 0, 0);
        TextView seriesPlot = label("Loading description...", 16);
        seriesPlot.setTextColor(Color.WHITE);
        seriesPlot.setMaxLines(8);

        TextView heading = label(series.name, 22);
        heading.setTextColor(Color.rgb(95, 190, 255));
        heading.setTypeface(null, Typeface.BOLD);

        final Channel[] firstSeriesEpisode = {null};
        ArrayList<Channel> episodes = new ArrayList<>();
        LinearLayout seriesActions = null;
        final ImageView[] seriesBackdropView = {null};
        if (tvLayout) {
            FrameLayout heroFrame = new FrameLayout(this);
            ImageView backdrop = new ImageView(this);
            seriesBackdropView[0] = backdrop;
            backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
            int artworkWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.75f);
            heroFrame.addView(backdrop, new FrameLayout.LayoutParams(artworkWidth, -1, Gravity.RIGHT | Gravity.TOP));
            loadMovieBackdrop(backdrop, series.logoUrl);

            GradientDrawable leftFade = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{NAVY, NAVY, 0xFF101629, 0xCC101629, Color.TRANSPARENT});
            View leftOverlay = new View(this);
            leftOverlay.setBackground(leftFade);
            heroFrame.addView(leftOverlay, new FrameLayout.LayoutParams(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.75f), -1, Gravity.LEFT));


            GradientDrawable bottomFade = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.TRANSPARENT, NAVY});
            View bottomOverlay = new View(this);
            bottomOverlay.setBackground(bottomFade);
            heroFrame.addView(bottomOverlay, new FrameLayout.LayoutParams(
                    -1, dp(170), Gravity.BOTTOM));

            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(dp(24), dp(12), 0, dp(8));
            details.addView(heading);
            seriesPlot.setPadding(0, dp(12), dp(16), 0);
            details.addView(seriesPlot);

            TextView play = label("▶  PLAY", 20);
            play.setTextColor(Color.WHITE);
            play.setTypeface(null, Typeface.BOLD);
            play.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            play.setFocusable(true);
            play.setClickable(true);
            play.setPadding(0, dp(14), 0, dp(14));
            play.setBackgroundColor(Color.TRANSPARENT);
            play.setOnFocusChangeListener((v, focused) ->
                    play.setTextColor(focused ? Color.YELLOW : Color.WHITE));
            play.setOnClickListener(v -> {
                if (!episodes.isEmpty()) playSeriesEpisode(series, episodes, 0);
            });

            TextView myList = label("", 16);
            myList.setTextColor(Color.WHITE);
            myList.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            myList.setFocusable(true);
            myList.setClickable(true);
            myList.setPadding(0, dp(13), 0, dp(13));
            myList.setBackgroundColor(Color.TRANSPARENT);
            Runnable updateMyListText = () -> myList.setText(
                    isSeriesSaved(series.url) ? "✓  REMOVE FROM FAVORITES" : "+  ADD TO FAVORITES");
            updateMyListText.run();
            myList.setOnFocusChangeListener((v, focused) ->
                    myList.setTextColor(focused ? Color.YELLOW : Color.WHITE));
            myList.setOnClickListener(v -> {
                if (isSeriesSaved(series.url)) removeSeriesFromList(series.url);
                else saveSeriesToList(series);
                updateMyListText.run();
            });

            TextView external = label("EXTERNAL PLAYER", 16);
            external.setTextColor(Color.WHITE);
            external.setGravity(Gravity.CENTER);
            external.setFocusable(true);
            external.setClickable(true);
            external.setOnClickListener(v -> {
                if (firstSeriesEpisode[0] != null) {
                    playVodExternal(firstSeriesEpisode[0]);
                }
            });

            seriesActions = new LinearLayout(this);
            seriesActions.setOrientation(LinearLayout.HORIZONTAL);
            seriesActions.setGravity(Gravity.CENTER_VERTICAL);
            seriesActions.setPadding(dp(24), 0, 0, 0);

            TextView[] actionViews = {myList, play, external};
            for (TextView action : actionViews) {
                action.setGravity(Gravity.CENTER);
                action.setTextSize(14);
                action.setTextColor(Color.WHITE);
                action.setPadding(dp(12), dp(12), dp(12), dp(12));
                action.setOnFocusChangeListener((v, focused) -> {
                    if (focused) {
                        GradientDrawable selected = new GradientDrawable();
                        selected.setColor(0xFF1769C2);
                        selected.setCornerRadius(dp(6));
                        selected.setStroke(dp(2), 0xFF55A5FF);
                        action.setBackground(selected);
                    } else {
                        action.setBackgroundColor(Color.TRANSPARENT);
                    }
                });
                LinearLayout.LayoutParams actionParams =
                        new LinearLayout.LayoutParams(-2, -2);
                actionParams.setMargins(0, 0, dp(10), 0);
                seriesActions.addView(action, actionParams);
            }
            seriesActions.setFocusable(false);

            FrameLayout.LayoutParams detailsParams = new FrameLayout.LayoutParams(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.45f),
                    -1, Gravity.LEFT | Gravity.TOP);
            heroFrame.addView(details, detailsParams);
            LinearLayout.LayoutParams heroParams =
                    new LinearLayout.LayoutParams(-1, dp(340));
            heroParams.setMargins(-dp(12), 0, -dp(12), 0);
            screen.addView(heroFrame, heroParams);
        } else {
            screen.addView(heading);
        }
        TextView status = label("Loading seasons and episodes...", 15);
        screen.addView(status);
        if (tvLayout && seriesActions != null) {
            screen.addView(seriesActions,
                    new LinearLayout.LayoutParams(-1, -2));
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(screen);

        new Thread(() -> {
            String failure = null;
            ArrayList<String> seasonLabels = new ArrayList<>();
            String[] seriesDescription = {""};

            try {
                String api = base + "/player_api.php?username="
                        + apiEncode(user) + "&password=" + apiEncode(pass)
                        + "&action=get_series_info&series_id="
                        + apiEncode(series.url);
                readSeriesEpisodes(api, series, episodes, seriesDescription);
            } catch (Exception e) {
                failure = e.getMessage() == null ? "Could not load episodes." : e.getMessage();
            }

            final String error = failure;
            runOnUiThread(() -> {
                if (seriesBackdropView[0] != null) {
                    String artwork = series.seriesBackdrop == null || series.seriesBackdrop.isEmpty()
                            ? series.logoUrl : series.seriesBackdrop;
                    loadMovieBackdrop(seriesBackdropView[0], artwork);
                }
                seriesPlot.setText(seriesDescription[0] == null
                        || seriesDescription[0].trim().isEmpty()
                        ? "No description available."
                        : seriesDescription[0]);
                list.removeAllViews();
                if (error != null) {
                    status.setText("Unable to load episodes: " + error);
                    list.addView(label("Check your provider connection and try again.", 14));
                    return;
                }

                if (!episodes.isEmpty()) firstSeriesEpisode[0] = episodes.get(0);
                status.setText(episodes.size() + " episodes");
                if (episodes.isEmpty()) {
                    list.addView(label("No episodes were returned for this series.", 16));
                    return;
                }

                if (series.resumeEpisodeId != null
                        && !series.resumeEpisodeId.isEmpty()) {
                    for (int i = 0; i < episodes.size(); i++) {
                        if (series.resumeEpisodeId.equals(episodes.get(i).vodId)) {
                            playSeriesEpisode(series, episodes, i);
                            return;
                        }
                    }
                }

                String currentSeason = "";
                View firstEpisodeView = null;
                HorizontalScrollView seasonScroll = null;
                LinearLayout seasonRow = null;

                for (int episodeIndex = 0; episodeIndex < episodes.size(); episodeIndex++) {
                    final int selectedEpisodeIndex = episodeIndex;
                    Channel episode = episodes.get(episodeIndex);
                    String season = episode.group == null ? "" : episode.group;
                    if (!season.equals(currentSeason)) {
                        currentSeason = season;
                        TextView seasonTitle = label(
                                season.isEmpty() ? "Episodes" : season, 18);
                        seasonTitle.setTextColor(Color.rgb(95, 190, 255));
                        seasonTitle.setTypeface(null, Typeface.BOLD);
                        seasonTitle.setPadding(dp(4), dp(12), dp(4), dp(6));
                        list.addView(seasonTitle);

                        if (tvLayout) {
                            seasonScroll = new HorizontalScrollView(this);
                            seasonScroll.setHorizontalScrollBarEnabled(false);
                            seasonRow = new LinearLayout(this);
                            seasonRow.setOrientation(LinearLayout.HORIZONTAL);
                            seasonScroll.addView(seasonRow);
                            list.addView(seasonScroll);
                        }
                    }

                    if (tvLayout) {
                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setFocusable(true);
                        card.setClickable(true);
                        card.setPadding(dp(6), dp(6), dp(6), dp(8));
                        card.setBackgroundColor(PANEL);

                        ImageView poster = new ImageView(this);
                        poster.setLayoutParams(new LinearLayout.LayoutParams(
                                dp(170), dp(115)));
                        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        GradientDrawable episodeBackground = new GradientDrawable();
                        episodeBackground.setColor(Color.rgb(35, 45, 62));
                        episodeBackground.setCornerRadius(dp(8));
                        poster.setBackground(episodeBackground);
                        poster.setClipToOutline(true);
                        if (episode.episodeArtwork != null
                                && !episode.episodeArtwork.isEmpty()) {
                            loadChannelLogo(poster, episode.episodeArtwork, 1080);
                        }
                        card.addView(poster);

                        TextView title = label(episode.name, 16);
                        title.setTextColor(Color.WHITE);
                        title.setTypeface(null, Typeface.BOLD);
                        title.setMaxLines(2);
                        title.setPadding(0, dp(8), 0, dp(4));
                        card.addView(title);

                        if (episode.episodeDescription != null
                                && !episode.episodeDescription.trim().isEmpty()) {
                            TextView synopsis = label(episode.episodeDescription, 13);
                            synopsis.setTextColor(Color.LTGRAY);
                            synopsis.setMaxLines(4);
                            card.addView(synopsis);
                        }

                        card.setOnFocusChangeListener((v, focused) ->
                                v.setBackgroundColor(focused ? BLUE : PANEL));
                        card.setOnClickListener(v -> playSeriesEpisode(series, episodes, selectedEpisodeIndex));

                        LinearLayout.LayoutParams cardParams =
                                new LinearLayout.LayoutParams(
                                        dp(182), LinearLayout.LayoutParams.WRAP_CONTENT);
                        cardParams.setMargins(dp(4), dp(3), dp(4), dp(8));
                        seasonRow.addView(card, cardParams);
                        if (firstEpisodeView == null) firstEpisodeView = card;
                    } else {
                        LinearLayout row = new LinearLayout(this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setFocusable(true);
                        row.setClickable(true);
                        row.setPadding(dp(6), dp(6), dp(6), dp(6));
                        row.setBackgroundColor(PANEL);

                        ImageView poster = new ImageView(this);
                        poster.setLayoutParams(new LinearLayout.LayoutParams(
                                dp(76), dp(104)));
                        poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        poster.setBackgroundColor(Color.rgb(35, 45, 62));
                        if (episode.episodeArtwork != null
                                && !episode.episodeArtwork.isEmpty()) {
                            loadChannelLogo(poster, episode.episodeArtwork, 1080);
                        }
                        row.addView(poster);

                        LinearLayout details = new LinearLayout(this);
                        details.setOrientation(LinearLayout.VERTICAL);
                        details.setPadding(dp(12), dp(4), dp(6), dp(4));

                        TextView title = label(episode.name, 14);
                        title.setTextColor(Color.WHITE);
                        title.setTypeface(null, Typeface.BOLD);
                        details.addView(title);

                        if (episode.episodeDescription != null
                                && !episode.episodeDescription.trim().isEmpty()) {
                            TextView synopsis = label(episode.episodeDescription, 12);
                            synopsis.setTextColor(Color.LTGRAY);
                            synopsis.setMaxLines(4);
                            details.addView(synopsis);
                        }

                        row.addView(details, new LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                        row.setOnFocusChangeListener((v, focused) ->
                                v.setBackgroundColor(focused ? BLUE : PANEL));
                        row.setOnClickListener(v -> playSeriesEpisode(series, episodes, selectedEpisodeIndex));

                        LinearLayout.LayoutParams params =
                                new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT);
                        params.setMargins(0, dp(3), 0, dp(3));
                        list.addView(row, params);
                        if (firstEpisodeView == null) firstEpisodeView = row;
                    }
                }

                if (firstEpisodeView != null) firstEpisodeView.requestFocus();
            });
        }).start();
    }

    private void readSeriesEpisodes(
            String urlText, Channel series, ArrayList<Channel> episodes,
            String[] seriesDescription)
            throws Exception {
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
                 InputStreamReader inputReader = new InputStreamReader(
                         input, StandardCharsets.UTF_8);
                 JsonReader reader = new JsonReader(inputReader)) {
                reader.beginObject();

                while (reader.hasNext()) {
                    String key = reader.nextName();

                    if ("info".equals(key) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                        JsonObject info = JsonParser.parseReader(reader).getAsJsonObject();
                        seriesDescription[0] = seriesJsonString(info, "plot", "");
                        if (seriesDescription[0].isEmpty()) seriesDescription[0] = seriesJsonString(info, "description", "");
                        if (seriesDescription[0].isEmpty()) seriesDescription[0] = seriesJsonString(info, "overview", "");

                        com.google.gson.JsonElement backdrop = info.get("backdrop_path");
                        if (backdrop != null && !backdrop.isJsonNull()) {
                            if (backdrop.isJsonArray()) {
                                for (com.google.gson.JsonElement image : backdrop.getAsJsonArray()) {
                                    if (image.isJsonPrimitive()) {
                                        String url = image.getAsString();
                                        if (!url.isEmpty()) {
                                            series.seriesBackdrop = url;
                                            break;
                                        }
                                    }
                                }
                            } else if (backdrop.isJsonPrimitive()) {
                                series.seriesBackdrop = backdrop.getAsString();
                            }
                        }
                        if (series.seriesBackdrop.isEmpty()) {
                            series.seriesBackdrop = seriesJsonString(info, "backdrop", "");
                        }
                        if (series.seriesBackdrop.isEmpty()) {
                            series.seriesBackdrop = seriesJsonString(info, "backdrop_url", "");
                        }
                    } else if (!"episodes".equals(key)) {
                        reader.skipValue();
                    } else if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        readSeriesEpisodeArray(reader, series, episodes);
                    } else if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            reader.nextName();
                            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                readSeriesEpisodeArray(
                                        reader, series, episodes);
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                    } else {
                        reader.skipValue();
                    }
                }

                reader.endObject();
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void readSeriesEpisodeArray(
            JsonReader reader, Channel series, ArrayList<Channel> episodes)
            throws Exception {
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                JsonObject episode =
                        JsonParser.parseReader(reader).getAsJsonObject();
                addSeriesEpisode(episode, series, episodes);
            } else {
                reader.skipValue();
            }
        }
        reader.endArray();
    }

    private String seriesJsonString(
            JsonObject item, String key, String fallback) {
        if (!item.has(key) || item.get(key).isJsonNull()) return fallback;
        return item.get(key).getAsString();
    }

    private String seriesEpisodeString(JsonObject ep, String key, String fallback) {
        String value = seriesJsonString(ep, key, "");
        if (!value.isEmpty()) return value;
        if (ep.has("info") && ep.get("info").isJsonObject())
            return seriesJsonString(ep.getAsJsonObject("info"), key, fallback);
        return fallback;
    }

    private void addSeriesEpisode(
            JsonObject ep, Channel series, ArrayList<Channel> episodes) {
        String id = seriesJsonString(ep, "id", "");
        if (id.isEmpty()) {
            id = seriesJsonString(ep, "episode_id", "");
        }
        if (id.isEmpty()) return;

        String extension = seriesJsonString(
                ep, "container_extension", "mp4");
        String season = seriesJsonString(ep, "season", "");
        String episodeNumber = seriesJsonString(ep, "episode_num", "");
        String title = seriesJsonString(
                ep, "title", "Episode " + (episodes.size() + 1));
        String name = episodeNumber.isEmpty()
                ? title : "Episode " + episodeNumber + " — " + title;

        String url = getPreferences(MODE_PRIVATE).getString(
                "xtream_server", "")
                + "/series/" + Uri.encode(getPreferences(MODE_PRIVATE)
                        .getString("xtream_username", "")) + "/"
                + Uri.encode(getPreferences(MODE_PRIVATE)
                        .getString("xtream_password", ""))
                + "/" + Uri.encode(id) + "." + Uri.encode(extension);

        String artwork = seriesEpisodeString(ep, "movie_image", "");
        if (artwork.isEmpty()) artwork = seriesEpisodeString(ep, "cover_big", "");
        if (artwork.isEmpty()) artwork = seriesEpisodeString(ep, "cover", "");
        if (artwork.isEmpty()) artwork = seriesEpisodeString(ep, "stream_icon", "");
        String description = seriesEpisodeString(ep, "plot", "");
        if (description.isEmpty()) description = seriesEpisodeString(ep, "description", "");
        if (description.isEmpty()) description = seriesEpisodeString(ep, "overview", "");
        Channel episode = new Channel(name, url, season.isEmpty() ? "Episodes" : "Season " + season, "", "");
        episode.vodId = id;
        episode.xtreamStreamId = series.url;
        episode.vodExtension = extension;
        episode.episodeArtwork = artwork;
        episode.episodeDescription = description;
        episode.episodeNumber = episodeNumber;
        episodes.add(episode);
    }

    private void showHomeScreen() {
        showTvGuide();
    }

    private void styleStreamNestDialog(AlertDialog dialog) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(NAVY);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(2), BLUE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(background);
        }
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = dialog.findViewById(titleId);
        if (title != null) title.setTextColor(WHITE);
        int messageId = getResources().getIdentifier("message", "id", "android");
        TextView message = dialog.findViewById(messageId);
        if (message != null) message.setTextColor(MUTED);
        for (int id : new int[]{AlertDialog.BUTTON_POSITIVE,
                AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            if (dialog.getButton(id) != null) {
                dialog.getButton(id).setTextColor(WHITE);
            }
        }
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Search channels, movies, or TV series");
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(PANEL);
        inputBg.setCornerRadius(dp(8));
        inputBg.setStroke(dp(1), BLUE);
        input.setBackground(inputBg);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);

        AlertDialog searchDialog = new AlertDialog.Builder(this)
                .setTitle("Search StreamNest")
                .setView(input)
                .setPositiveButton("Search", (dialog, which) -> {
                    String query = input.getText().toString().trim()
                            .toLowerCase(java.util.Locale.ROOT);

                    if (query.isEmpty()) {
                        Toast.makeText(this, "Enter a search term.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    ArrayList<Channel> liveMatches = new ArrayList<>();
                    for (Channel channel : new ArrayList<>(channels)) {
                        if (channel.name != null && channel.name
                                .toLowerCase(java.util.Locale.ROOT)
                                .contains(query)) {
                            liveMatches.add(channel);
                        }
                    }

                    String base = getPreferences(MODE_PRIVATE)
                            .getString("xtream_server", "");
                    String user = getPreferences(MODE_PRIVATE)
                            .getString("xtream_username", "");
                    String pass = getPreferences(MODE_PRIVATE)
                            .getString("xtream_password", "");

                    AlertDialog loadingDialog = new AlertDialog.Builder(this)
                            .setTitle("Searching")
                            .setMessage("Searching Live TV, Movies, and TV Series...")
                            .setCancelable(false)
                            .create();
                    loadingDialog.show();
                    styleStreamNestDialog(loadingDialog);

                    new Thread(() -> {
                        ArrayList<Channel> results = new ArrayList<>();
                        ArrayList<String> types = new ArrayList<>();
                        ArrayList<String> names = new ArrayList<>();
                        StringBuilder errors = new StringBuilder();

                        for (Channel channel : liveMatches) {
                            results.add(channel);
                            types.add("LIVE TV");
                            names.add("[LIVE TV] " + channel.name);
                        }

                        if (!base.isEmpty() && !user.isEmpty()
                                && !pass.isEmpty()) {
                            String api = base + "/player_api.php?username="
                                    + Uri.encode(user) + "&password="
                                    + Uri.encode(pass);

                            try {
                                JSONArray movies = getXtreamArray(
                                        api + "&action=get_vod_streams");
                                int movieCount = 0;
                                for (int i = 0; i < movies.length(); i++) {
                                    JSONObject item = movies.getJSONObject(i);
                                    String id = item.optString("stream_id", "");
                                    String name = item.optString("name", "Untitled movie");

                                    if (id.isEmpty() || !name
                                            .toLowerCase(java.util.Locale.ROOT)
                                            .contains(query)) {
                                        continue;
                                    }

                                    String extension = item.optString(
                                            "container_extension", "mp4");
                                    String url = base + "/movie/"
                                            + Uri.encode(user) + "/"
                                            + Uri.encode(pass) + "/"
                                            + Uri.encode(id) + "."
                                            + Uri.encode(extension);

                                    Channel movie = new Channel(
                                            name, url,
                                            item.optString("category_name", "Movies"),
                                            "",
                                            item.optString("stream_icon", ""));

                                    movie.vodId = id;
                                    movie.vodExtension = extension;
                                    results.add(movie);
                                    types.add("MOVIE");
                                    names.add("[MOVIE] " + name);
                                    movieCount++;

                                    if (movieCount >= 100) break;
                                }
                            } catch (Exception e) {
                                errors.append("Movies: ")
                                        .append(e.getMessage()).append("\\n");
                            }

                            try {
                                JSONArray seriesList = getXtreamArray(
                                        api + "&action=get_series");

                                int seriesCount = 0;
                                for (int i = 0; i < seriesList.length(); i++) {
                                    JSONObject item = seriesList.getJSONObject(i);
                                    String id = item.optString("series_id", "");
                                    String name = item.optString("name", "Untitled series");

                                    if (id.isEmpty() || !name
                                            .toLowerCase(java.util.Locale.ROOT)
                                            .contains(query)) {
                                        continue;
                                    }

                                    Channel series = new Channel(
                                            name, id,
                                            item.optString("category_name", "TV Series"),
                                            "",
                                            item.optString("cover", ""));

                                    results.add(series);
                                    types.add("TV SERIES");
                                    names.add("[TV SERIES] " + name);
                                    seriesCount++;

                                    if (seriesCount >= 100) break;
                                }
                            } catch (Exception e) {
                                errors.append("TV Series: ")
                                        .append(e.getMessage()).append("\n");
                            }
                        }

                        runOnUiThread(() -> {
                            loadingDialog.dismiss();

                            if (names.isEmpty()) {
                                String message = "No matching content found.";
                                if (base.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                                    message += "\nConnect your Xtream account to search movies and TV series.";
                                } else if (errors.length() > 0) {
                                    message += "\n" + errors;
                                }

                                AlertDialog emptyDialog = new AlertDialog.Builder(this)
                                        .setTitle("Search Results")
                                        .setMessage(message)
                                        .setPositiveButton("OK", null)
                                .create();
                        emptyDialog.show();
                        styleStreamNestDialog(emptyDialog);
                                return;
                            }

                            android.widget.ListView resultList = new android.widget.ListView(this);
                    resultList.setAdapter(new android.widget.ArrayAdapter<String>(
                        this, android.R.layout.simple_list_item_1, names));

                    AlertDialog resultsDialog = new AlertDialog.Builder(this)
                        .setTitle("Search Results (" + names.size() + ")")
                        .setView(resultList)
                        .setNegativeButton("Close", null)
                        .create();

                    resultList.setOnItemClickListener((parent, view, index, id) -> {
                        Channel selected = results.get(index);
                        String type = types.get(index);
                        resultsDialog.dismiss();

                        if ("LIVE TV".equals(type)) {
                            playChannel(selected);
                        } else if ("MOVIE".equals(type)) {
                            playVodItem(selected);
                        } else {
                            showSeriesEpisodes(selected, base, user, pass);
                        }
                    });

                    resultList.setOnItemLongClickListener((parent, view, index, id) -> {
                        Channel selected = results.get(index);
                        String type = types.get(index);
                        String message;

                        if ("LIVE TV".equals(type)) {
                            if (selected.url == null || selected.url.isEmpty()) {
                                message = "Unable to add to Favorites";
                            } else if (favorites.add(selected.url)) {
                                getPreferences(MODE_PRIVATE).edit()
                                    .putStringSet(playlistPreferenceKey("favorite_urls"),
                                        new LinkedHashSet<>(favorites)).apply();
                                message = "Added to Favorites";
                            } else {
                                message = "Already in Favorites";
                            }
                        } else if ("MOVIE".equals(type)) {
                            boolean wasSaved = isMovieSaved(selected.vodId);
                            saveMovieToList(selected);
                            message = wasSaved ? "Already in MY LIST"
                                : isMovieSaved(selected.vodId) ? "Added to MY LIST"
                                : "Unable to add to MY LIST";
                        } else {
                            boolean wasSaved = isSeriesSaved(selected.url);
                            saveSeriesToList(selected);
                            message = wasSaved ? "Already in Series Favorites"
                                : isSeriesSaved(selected.url) ? "Added to Series Favorites"
                                : "Unable to add to Favorites";
                        }

                        android.widget.Toast.makeText(this, message,
                            android.widget.Toast.LENGTH_SHORT).show();
                        return true;
                    });

                    resultsDialog.show();
                    styleStreamNestDialog(resultsDialog);
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .create();

        searchDialog.show();
        styleStreamNestDialog(searchDialog);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || (event != null
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                searchDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
    }

    private void focusGuideSelectedCategory() {
        if (guideCategoryScrollView == null) return;

        Channel selectedChannel = guidePreviewChannel;
        if (selectedChannel == null) {
            String lastUrl = getPreferences(MODE_PRIVATE)
                    .getString("last_channel_url", "");
            for (Channel channel : channels) {
                if (channel.url != null && channel.url.equals(lastUrl)) {
                    selectedChannel = channel;
                    break;
                }
            }
        }

        if (selectedChannel == null) return;

        String group = selectedChannel.group;
        if (group == null || group.trim().isEmpty()) {
            group = "Uncategorized";
        }

        View list = guideCategoryScrollView.getChildAt(0);
        if (list instanceof LinearLayout) {
            LinearLayout items = (LinearLayout) list;
            for (int i = 0; i < items.getChildCount(); i++) {
                View child = items.getChildAt(i);
                if (child instanceof TextView
                        && ((TextView) child).getText().toString()
                        .startsWith(group + " (")) {
                    child.requestFocus();
                    return;
                }
            }
        }
    }

    private void addHomeSection(LinearLayout menu, String title, String subtitle) {
        String symbol;
        switch (title) {
            case "SEARCH": symbol = "⌕"; break;
            case "TV GUIDE": symbol = "▤"; break;
            case "MOVIES": symbol = "▣"; break;
            case "TV SERIES": symbol = "▥"; break;
            case "CATCH-UP": symbol = "↶"; break;
            default: symbol = "⚙"; break;
        }

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(10), dp(9), dp(8), dp(9));
        item.setBackgroundColor(0x00000000);
        item.setFocusable(true);
        item.setClickable(true);

        TextView icon = new TextView(this);
        icon.setText(symbol);
        icon.setTextSize(20);
        icon.setTextColor(WHITE);
        icon.setGravity(Gravity.CENTER);
        icon.setIncludeFontPadding(false);
        item.addView(icon, new LinearLayout.LayoutParams(dp(34),
                dp(30)));

        TextView text = label(title, 16);
        text.setTextColor(WHITE);
        item.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        item.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                GradientDrawable selected = new GradientDrawable();
                selected.setColor(0xFF1769C2);
                selected.setCornerRadius(dp(6));
                selected.setStroke(dp(2), 0xFF55A5FF);
                item.setBackground(selected);
            } else {
                item.setBackgroundColor(Color.TRANSPARENT);
            }
        });

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
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        AlertDialog dialog = new AlertDialog.Builder(this).create();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(24), dp(22), dp(18));
        panel.setBackgroundColor(NAVY);

        TextView heading = label("SETTINGS", 22);
        heading.setTypeface(null, Typeface.BOLD);
        panel.addView(heading);

        addSettingsSwitch(panel, prefs, "Launch Live TV on app boot",
                "launch_live_tv_on_boot", true);
        addSettingsSwitch(panel, prefs, "Launch Live TV after playlist change",
                "launch_live_tv_on_change", true);
        addSettingsSwitch(panel, prefs, "Auto Play Next Episode",
                "auto_play_next_episode", false);
        addSettingsSwitch(panel, prefs, "On Screen Clock",
                "show_on_screen_clock", true);

        View spacer = new View(this);
        panel.addView(spacer, new LinearLayout.LayoutParams(1, dp(12)));

        TextView playlists = label("MY PLAYLISTS", 16);
        setPlaylistFocusStyle(playlists);
        playlists.setFocusable(true);
        playlists.setOnClickListener(v -> {
            dialog.dismiss();
            showPlaylistManager();
        });
        panel.addView(playlists);

        TextView epg = label("XMLTV EPG URL", 16);
        setPlaylistFocusStyle(epg);
        epg.setFocusable(true);
        epg.setOnClickListener(v -> {
            dialog.dismiss();
            showEpgDialog();
        });
        panel.addView(epg);

        TextView done = label("DONE", 16);
        done.setGravity(Gravity.CENTER);
        done.setBackgroundColor(BLUE);
        done.setFocusable(true);
        done.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        doneParams.topMargin = dp(12);
        panel.addView(done, doneParams);

        dialog.setView(panel);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(
                    android.R.color.transparent);
            dialog.getWindow().setGravity(Gravity.END);
            int width = Math.min(dp(420),
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.88f));
            dialog.getWindow().setLayout(width,
                    LinearLayout.LayoutParams.MATCH_PARENT);
        }
        playlists.requestFocus();
    }

    private void addSettingsSwitch(LinearLayout panel,
            android.content.SharedPreferences prefs, String title,
            String key, boolean defaultValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(5), dp(4), dp(5));

        TextView name = label(title, 15);
        row.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(key, checked).apply());
        row.addView(toggle);
        panel.addView(row);
    }

    private void showEpgDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Enter XMLTV EPG URL");
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(PANEL);
        inputBg.setCornerRadius(dp(8));
        inputBg.setStroke(dp(1), BLUE);
        input.setBackground(inputBg);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(getPreferences(MODE_PRIVATE)
                .getString("epg_url", ""));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("TV Guide (XMLTV)")
                .setMessage("Enter the separate EPG/XMLTV URL from your provider.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, which) -> {
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
                .create();

        dialog.setOnShowListener(v -> {
            GradientDrawable background = new GradientDrawable();
            background.setColor(NAVY);
            background.setCornerRadius(dp(12));
            background.setStroke(dp(2), BLUE);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(background);
            }
            dialog.getButton(-1).setTextColor(WHITE);
            dialog.getButton(-2).setTextColor(WHITE);
            int messageId = getResources().getIdentifier(
                    "message", "id", "android");
            TextView message = dialog.findViewById(messageId);
            if (message != null) message.setTextColor(MUTED);
        });
        dialog.show();
    }

    private void setPlaylistFocusStyle(TextView view) {
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                GradientDrawable selected = new GradientDrawable();
                selected.setColor(0xFF1769C2);
                selected.setCornerRadius(dp(6));
                selected.setStroke(dp(2), 0xFF55A5FF);
                view.setBackground(selected);
            } else {
                view.setBackgroundColor(Color.TRANSPARENT);
            }
        });
    }

    private void showWelcomeScreen() {
        screen.removeAllViews();
        screen.setGravity(Gravity.CENTER);
        screen.setPadding(dp(24), dp(24), dp(24), dp(24));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.streamnest_splash);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setAdjustViewBounds(true);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                Math.min(dp(620), getResources().getDisplayMetrics().widthPixels - dp(48)),
                dp(250));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        screen.addView(logo, logoParams);

        int buttonWidth = Math.min(dp(420),
                getResources().getDisplayMetrics().widthPixels - dp(48));

        TextView manage = label("MY PLAYLISTS", 20);
        manage.setGravity(Gravity.CENTER);
        manage.setFocusable(true);
        setPlaylistFocusStyle(manage);
        manage.setOnClickListener(v -> showPlaylistManager());
        LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(
                buttonWidth, dp(64));
        manageParams.gravity = Gravity.CENTER_HORIZONTAL;
        screen.addView(manage, manageParams);

        TextView add = label("ADD PLAYLIST", 20);
        add.setGravity(Gravity.CENTER);
        add.setFocusable(true);
        setPlaylistFocusStyle(add);
        add.setOnClickListener(v -> showConnectionDialog());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                buttonWidth, dp(64));
        addParams.gravity = Gravity.CENTER_HORIZONTAL;
        addParams.topMargin = dp(12);
        screen.addView(add, addParams);

        manage.requestFocus();
    }

    private void showPlaylistManager() {
        screen.removeAllViews();
        screen.setGravity(Gravity.TOP);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(20), dp(12), dp(20), dp(12));

        int availableWidth = getResources().getDisplayMetrics().widthPixels - dp(40);
        int rowWidth = Math.min(dp(520), availableWidth);
        int buttonWidth = Math.min(dp(420), availableWidth);

        TextView title = label("MY PLAYLISTS", 26);
        title.setGravity(Gravity.CENTER);
        screen.addView(title);

        TextView subtitle = label(
                "Select a playlist to load, or add a new connection.", 14);
        subtitle.setGravity(Gravity.CENTER);
        screen.addView(subtitle);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        if (savedPlaylists.isEmpty()) {
            TextView empty = label("No playlists saved yet.", 17);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty);
        }

        for (PlaylistProfile profile : savedPlaylists) {
            String type = "m3u".equals(profile.type)
                    ? "M3U PLAYLIST" : "XTREAM CODES";
            String selected = profile.id.equals(activePlaylistId)
                    ? "  • ACTIVE" : "";
            TextView item = label(
                    profile.name + selected + "\n" + type, 18);
            item.setGravity(Gravity.CENTER);
            item.setFocusable(true);
            setPlaylistFocusStyle(item);
            item.setClickable(true);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    rowWidth, dp(68));
            params.gravity = Gravity.CENTER_HORIZONTAL;
            params.bottomMargin = dp(8);
            list.addView(item, params);
            item.setOnClickListener(v -> showPlaylistActions(profile));
        }

        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        TextView add = label("+  ADD PLAYLIST", 18);
        add.setGravity(Gravity.CENTER);
        add.setFocusable(true);
        setPlaylistFocusStyle(add);
        add.setOnClickListener(v -> showConnectionDialog());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                buttonWidth, dp(58));
        addParams.gravity = Gravity.CENTER_HORIZONTAL;
        screen.addView(add, addParams);

        TextView back = label("BACK TO TV GUIDE", 16);
        back.setGravity(Gravity.CENTER);
        back.setFocusable(true);
        setPlaylistFocusStyle(back);
        back.setOnClickListener(v -> showHomeScreen());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                buttonWidth, dp(52));
        backParams.gravity = Gravity.CENTER_HORIZONTAL;
        screen.addView(back, backParams);
    }

    private void stylePlaylistDialog(AlertDialog dialog) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(NAVY);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(2), BLUE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(background);
        }
        for (int button : new int[]{-1, -2, -3}) {
            if (dialog.getButton(button) != null) {
                dialog.getButton(button).setTextColor(WHITE);
            }
        }
        int messageId = getResources().getIdentifier("message", "id", "android");
        TextView message = dialog.findViewById(messageId);
        if (message != null) message.setTextColor(MUTED);
        android.widget.ListView list = dialog.getListView();
        if (list != null) {
            list.setBackgroundColor(NAVY);
            for (int i = 0; i < list.getChildCount(); i++) {
                View child = list.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(WHITE);
                }
            }
        }
    }

    private void showPlaylistActions(PlaylistProfile profile) {
        AlertDialog actions = new AlertDialog.Builder(this)
                .setTitle(profile.name)
                .setItems(new String[]{"Load Playlist", "Edit Playlist", "Delete Playlist"},
                        (dialog, which) -> {
                    if (which == 0) {
                        activePlaylistId = profile.id;
                        loadPlaylistFavorites();
                        syncActivePlaylistToConnection();
                        if ("m3u".equals(profile.type)) {
                            loadPlaylist(profile.url, true);
                        } else {
                            loadXtreamChannels(profile.server, profile.username, profile.password, true);
                        }
                    } else if (which == 1) {
                        if ("m3u".equals(profile.type)) showPlaylistDialog(profile);
                        else showXtreamDialog(profile);
                    } else {
                        AlertDialog confirm = new AlertDialog.Builder(this)
                                .setTitle("Delete Playlist")
                                .setMessage("Delete " + profile.name + "?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete", (d, w) -> {
                                    boolean wasActive = profile.id.equals(activePlaylistId);
                                    savedPlaylists.remove(profile);
                                    if (wasActive) {
                                        activePlaylistId = savedPlaylists.isEmpty() ? "" : savedPlaylists.get(0).id;
            if (activePlaylistId.isEmpty()) favorites.clear();
            else loadPlaylistFavorites();
                                    }
                                    saveSavedPlaylists();
                                    if (savedPlaylists.isEmpty()) {
                                        getPreferences(MODE_PRIVATE).edit()
                                                .remove("playlist_url")
                                                .remove("xtream_server")
                                                .remove("xtream_username")
                                                .remove("xtream_password")
                                                .remove("last_connection")
                                                .apply();
                                        showWelcomeScreen();
                                    } else if (wasActive) {
                                        syncActivePlaylistToConnection();
                                        PlaylistProfile next = savedPlaylists.get(0);
                                        if ("m3u".equals(next.type)) loadPlaylist(next.url, true);
                                        else loadXtreamChannels(next.server, next.username, next.password, true);
                                    } else {
                                        showPlaylistManager();
                                    }
                                })
                                .create();
                        confirm.setOnShowListener(v -> stylePlaylistDialog(confirm));
                        confirm.show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        actions.setOnShowListener(v -> stylePlaylistDialog(actions));
        actions.show();
    }

    private void showConnectionDialog() {
        AlertDialog connection = new AlertDialog.Builder(this)
                .setTitle("Connect Playlist")
                .setItems(new String[]{"M3U Playlist", "Xtream Codes Login"},
                        (dialog, which) -> {
                    if (which == 0) showPlaylistDialog();
                    else showXtreamDialog();
                })
                .setNegativeButton("Cancel", null)
                .create();
        connection.setOnShowListener(v -> stylePlaylistDialog(connection));
        connection.show();
    }

    private void showXtreamDialog() {
        showXtreamDialog(null);
    }

    private void showXtreamDialog(PlaylistProfile editing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(8);
        form.setPadding(pad, pad, pad, pad);

        EditText profileName = new EditText(this);
        profileName.setSingleLine(true);
        profileName.setHint("Playlist name");
        if (editing != null) profileName.setText(editing.name);
        form.addView(profileName);

        EditText server = new EditText(this);
        server.setSingleLine(true);
        server.setHint("Server URL (https://host:port)");
        server.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);
        server.setText(editing != null ? editing.server
                : getPreferences(MODE_PRIVATE).getString("xtream_server", ""));
        form.addView(server);

        EditText username = new EditText(this);
        username.setSingleLine(true);
        username.setHint("Username");
        username.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        username.setText(editing != null ? editing.username
                : getPreferences(MODE_PRIVATE).getString("xtream_username", ""));
        form.addView(username);

        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setHint("Password");
        password.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (editing != null) password.setText(editing.password);
        form.addView(password);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);

        AlertDialog loginDialog = new AlertDialog.Builder(this)
                .setTitle(editing == null ? "Xtream Codes Login"
                        : "Edit Xtream Playlist")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(editing == null ? "Login" : "Save",
                        (dialog, which) -> {
                    String name = profileName.getText().toString().trim();
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

                    PlaylistProfile profile = editing == null
                            ? new PlaylistProfile() : editing;
                    if (editing == null) {
                        profile.id = UUID.randomUUID().toString();
                        savedPlaylists.add(profile);
                    }
                    profile.name = name.isEmpty() ? user : name;
                    profile.type = "xtream";
                    profile.server = base;
                    profile.username = user;
                    profile.password = pass;
                    activePlaylistId = profile.id;
                    loadPlaylistFavorites();
                    saveSavedPlaylists();

                    getPreferences(MODE_PRIVATE).edit()
                            .putString("xtream_server", base)
                            .putString("xtream_username", user)
                            .putString("xtream_password", pass)
                            .putString("last_connection", "xtream")
                            .apply();

                    loadXtreamChannels(base, user, pass, true);
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

    private interface XtreamObjectHandler {
        void onObject(JsonObject item) throws Exception;
    }

    private void forEachXtreamObject(
            String urlText, XtreamObjectHandler handler) throws Exception {
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
                 InputStreamReader inputReader = new InputStreamReader(
                         input, StandardCharsets.UTF_8);
                 JsonReader reader = new JsonReader(inputReader)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        JsonElement element = JsonParser.parseReader(reader);
                        handler.onObject(element.getAsJsonObject());
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endArray();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
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
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(
                                 input, StandardCharsets.UTF_8))) {
                StringBuilder json = new StringBuilder();
                char[] buffer = new char[8192];
                int count;

                while ((count = reader.read(buffer)) != -1) {
                    json.append(buffer, 0, count);
                }

                return new JSONArray(json.toString());
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JSONObject getXtreamObject(String urlText) throws Exception {
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
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder json = new StringBuilder();
                char[] buffer = new char[8192];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    json.append(buffer, 0, count);
                }
                return new JSONObject(json.toString());
            }
        } finally {
            if (connection != null) connection.disconnect();
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
        if (launchAfterLoad || channels.isEmpty()) {
            Toast.makeText(this, "Connecting to provider...",
                    Toast.LENGTH_SHORT).show();
        }

        new Thread(() -> {
            ArrayList<Channel> loaded = new ArrayList<>();
            String failure = null;
            long refreshId = -1;

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
                    if (!id.isEmpty()) groupNames.put(id, name);
                }

                String providerKey = CatalogDatabase.makeProviderKey(
                        base + "|" + username);
                refreshId = catalogDatabase.beginRefresh(
                        providerKey, "live");
                final long batchRefreshId = refreshId;

                ArrayList<CatalogDatabase.CatalogItem> batch =
                        new ArrayList<>();

                forEachXtreamObject(
                        api + "&action=get_live_streams", item -> {
                    String id = seriesJsonString(item, "stream_id", "");
                    if (id.isEmpty()) return;

                    String name = seriesJsonString(
                            item, "name", "Channel");
                    String categoryId = seriesJsonString(
                            item, "category_id", "");
                    String group = groupNames.get(categoryId);
                    if (group == null || group.isEmpty()) {
                        group = "Uncategorized";
                    }

                    String extension = seriesJsonString(
                            item, "container_extension", "ts");
                    if (extension.isEmpty()) extension = "ts";

                    String streamUrl = base + "/live/"
                            + Uri.encode(username) + "/"
                            + Uri.encode(password) + "/"
                            + Uri.encode(id) + "."
                            + Uri.encode(extension);

                    String tvgId = seriesJsonString(
                            item, "epg_channel_id", "");
                    String logoUrl = seriesJsonString(
                            item, "stream_icon", "");

                    loaded.add(new Channel(
                            name, streamUrl, group, tvgId, logoUrl, id));

                    batch.add(new CatalogDatabase.CatalogItem(
                            "live", id, name, id, group,
                            tvgId, logoUrl, extension));

                    if (batch.size() >= 200) {
                        catalogDatabase.insertBatch(
                                providerKey, "live", batchRefreshId, batch);
                        batch.clear();
                    }
                });

                int epgIdCount = 0;
                for (Channel channel : loaded) {
                    if (channel.tvgId != null && !channel.tvgId.trim().isEmpty()) epgIdCount++;
                }
                android.util.Log.i("StreamNest", "Xtream live channels: " + loaded.size() + ", with EPG IDs: " + epgIdCount);
                if (!loaded.isEmpty()) {
                    if (!batch.isEmpty()) {
                        catalogDatabase.insertBatch(
                                providerKey, "live", batchRefreshId, batch);
                    }
                    catalogDatabase.finishRefresh(refreshId);
                    refreshId = -1;
                } else {
                    catalogDatabase.abandonRefresh(refreshId);
                    refreshId = -1;
                }
            } catch (Exception e) {
                android.util.Log.e("StreamNest",
                        "Xtream connection failed", e);
                failure = e.getMessage();
                if (refreshId >= 0) {
                    try {
                        catalogDatabase.abandonRefresh(refreshId);
                    } catch (Exception cleanupError) {
                        android.util.Log.e("StreamNest",
                                "Catalog cleanup failed", cleanupError);
                    }
                }
            }

            final String error = failure;
            runOnUiThread(() -> {
                boolean keepExisting = !launchAfterLoad && !channels.isEmpty();

                if (error != null) {
                    if (!keepExisting) {
                        Toast.makeText(this,
                                "Xtream login failed. Check the server, credentials and connection.",
                                Toast.LENGTH_LONG).show();
                    }
                } else if (loaded.isEmpty()) {
                    if (!keepExisting) {
                        Toast.makeText(this,
                                "Login returned no live channels.",
                                Toast.LENGTH_LONG).show();
                    }
                } else if (!keepExisting) {
                    channels = loaded;
                    selectedCategory = "All Channels";
                    if (launchAfterLoad) showTvGuide();
                    Toast.makeText(this,
                            "Loaded " + channels.size() + " live channels",
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void showPlaylistDialog() {
        showPlaylistDialog(null);
    }

    private void showPlaylistDialog(PlaylistProfile editing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(8);
        form.setPadding(pad, pad, pad, pad);

        EditText profileName = new EditText(this);
        profileName.setSingleLine(true);
        profileName.setHint("Playlist name");
        if (editing != null) profileName.setText(editing.name);
        form.addView(profileName);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Enter M3U playlist URL");
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(editing != null ? editing.url
                : getPreferences(MODE_PRIVATE).getString("playlist_url", ""));
        form.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(editing == null ? "M3U Playlist" : "Edit M3U Playlist")
                .setMessage("Enter a name and IPTV playlist URL")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!url.startsWith("http://")
                            && !url.startsWith("https://")) {
                        Toast.makeText(this, "Enter a valid URL",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    PlaylistProfile profile = editing == null
                            ? new PlaylistProfile() : editing;
                    if (editing == null) {
                        profile.id = UUID.randomUUID().toString();
                        savedPlaylists.add(profile);
                    }
                    String name = profileName.getText().toString().trim();
                    profile.name = name.isEmpty() ? "M3U Playlist" : name;
                    profile.type = "m3u";
                    profile.url = url;
                    activePlaylistId = profile.id;
                    loadPlaylistFavorites();
                    saveSavedPlaylists();

                    getPreferences(MODE_PRIVATE).edit()
                            .putString("playlist_url", url)
                            .putString("last_connection", "m3u")
                            .apply();
                    Toast.makeText(this, "Loading playlist...",
                            Toast.LENGTH_SHORT).show();
                    loadPlaylist(url, true);
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
                        .putStringSet(playlistPreferenceKey("favorite_urls"),
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
                        .putStringSet(playlistPreferenceKey("favorite_urls"),
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
        String description;
        long startMillis;
        long stopMillis;

        EpgProgram(String channelId, String title, String description,
                long startMillis, long stopMillis) {
            this.channelId = channelId;
            this.title = title;
            this.description = description == null ? "" : description.trim();
            this.startMillis = startMillis;
            this.stopMillis = stopMillis;
        }
    }

    private void showTvGuide() {
        showingTvVod = false;
        showingTvGuide = true;
        String epgUrl = getPreferences(MODE_PRIVATE).getString("epg_url", "");
        if (channels.isEmpty()) {
            startupGuidePending = false;
            startupPreviewSuppressed = false;
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

        final Map<String, ArrayList<Channel>> channelMap = new HashMap<>();
        TextView status = new TextView(this);
        for (Channel channel : channels) {
            String epgId = normalizeEpgId(channel.tvgId);
            if (!epgId.isEmpty()) {
                ArrayList<Channel> matches = channelMap.get(epgId);
                if (matches == null) {
                    matches = new ArrayList<>();
                    channelMap.put(epgId, matches);
                }
                matches.add(channel);
            }
        }

        screen.removeAllViews();
        screen.setOrientation(LinearLayout.VERTICAL);

        int previewHeight = phoneGuideLayout ? dp(110) : dp(190);

        LinearLayout previewRow = new LinearLayout(this);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setBackgroundColor(PANEL);

        previewPlayerView = new PlayerView(this);
        previewPlayerView.setUseController(false);
        previewPlayerView.setResizeMode(
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        previewPlayerView.setFocusable(false);
        previewPlayerView.setBackgroundColor(Color.BLACK);
        if (previewPlayer != null) {
            previewPlayerView.setPlayer(previewPlayer);
        }
        previewRow.addView(previewPlayerView, new LinearLayout.LayoutParams(
                0, previewHeight, phoneGuideLayout ? 1.2f : 0.35f));

        LinearLayout previewInfo = new LinearLayout(this);
        previewInfo.setOrientation(LinearLayout.VERTICAL);
        previewInfo.setBackgroundColor(PANEL);
        previewInfo.setPadding(dp(10), dp(8), dp(8), dp(8));
        guidePreviewChannelNameView = label("Now Playing", 16);
        previewInfo.addView(guidePreviewChannelNameView);
        guidePreviewProgramInfoView = label(
                "Programme information unavailable", 13);
        previewInfo.addView(guidePreviewProgramInfoView);
        previewRow.addView(previewInfo, new LinearLayout.LayoutParams(
                0, previewHeight, phoneGuideLayout ? 0.8f : 0.65f));

        screen.addView(previewRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, previewHeight));
        if (guidePreviewChannel != null && previewPlayer != null) {
            playGuidePreview(guidePreviewChannel);
        }

        LinearLayout guideContent = new LinearLayout(this);
        guideContent.setOrientation(LinearLayout.HORIZONTAL);

        guideMainMenuScroll = null;
        {
            guideMainMenuScroll = new ScrollView(this);
            guideMainMenuScroll.setVisibility(View.GONE);

            LinearLayout guideMainMenu = new LinearLayout(this);
            guideMainMenu.setOrientation(LinearLayout.VERTICAL);
            guideMainMenu.setPadding(dp(5), dp(5), dp(5), dp(5));
            addHomeSection(guideMainMenu, "SEARCH", "Find live TV channels");
            addHomeSection(guideMainMenu, "TV GUIDE", "Browse the TV schedule");
            addHomeSection(guideMainMenu, "MOVIES", "Browse movies");
            addHomeSection(guideMainMenu, "TV SERIES", "Browse TV series");
            addHomeSection(guideMainMenu, "CATCH-UP", "Recently aired programs");
            addHomeSection(guideMainMenu, "SETTINGS", "Playlist and app settings");

            guideMainMenuScroll.addView(guideMainMenu);
            guideContent.addView(guideMainMenuScroll,
                    new LinearLayout.LayoutParams(
                            dp(190), LinearLayout.LayoutParams.MATCH_PARENT));
        }

        LinearLayout categoryList = new LinearLayout(this);
        categoryList.setOrientation(LinearLayout.VERTICAL);
        guideCategoryScrollView = new ScrollView(this);
        guideCategoryScrollView.addView(categoryList);
        guideCategoryScrollView.setVisibility(
                phoneGuideLayout
                        ? android.view.View.GONE
                        : android.view.View.VISIBLE);
        guideContent.addView(guideCategoryScrollView,
                new LinearLayout.LayoutParams(dp(240),
                        LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        guideContent.addView(results, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        android.widget.FrameLayout guideContentFrame =
                new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams contentParams =
                new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        contentParams.topMargin = dp(phoneGuideLayout ? 43 : 0);
        guideContentFrame.addView(guideContent, contentParams);

        View guideDivider = new View(this);
        guideDivider.setBackgroundColor(0xFF777777);
        android.widget.FrameLayout.LayoutParams dividerParams =
                new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1));
        dividerParams.topMargin = dp(phoneGuideLayout ? 42 : 0);
        guideContentFrame.addView(guideDivider, dividerParams);

        screen.addView(guideContentFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        if (isPhoneLayout()) {
            android.widget.Button categoriesButton =
                    new android.widget.Button(this);
            categoriesButton.setText("CATEGORIES");
            categoriesButton.setOnClickListener(v -> {
                if (guideCategoryScrollView == null || guideMainMenuScroll == null) return;

                if (guideMainMenuScroll.getVisibility() == android.view.View.VISIBLE) {
                    guideMainMenuScroll.setVisibility(android.view.View.GONE);
                    guideCategoryScrollView.setVisibility(android.view.View.VISIBLE);
                } else if (guideCategoryScrollView.getVisibility() == android.view.View.VISIBLE) {
                    guideCategoryScrollView.setVisibility(android.view.View.GONE);
                    guideMainMenuScroll.setVisibility(android.view.View.VISIBLE);
                } else {
                    guideCategoryScrollView.setVisibility(android.view.View.VISIBLE);
                }
            });
            screen.addView(categoriesButton, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
        }

        if (startupGuidePending) {
            android.widget.FrameLayout content =
                    findViewById(android.R.id.content);
            startupCoverView = new View(this);
            startupCoverView.setBackgroundColor(NAVY);
            startupCoverView.setClickable(true);
            content.addView(startupCoverView,
                    new android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        }

        if (epgUrl.trim().isEmpty()) {
            android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            String connectionType = prefs.getString("last_connection", "");
            if ("xtream".equals(connectionType)) {
                renderGuidePrograms(new ArrayList<>(), channelMap, status, categoryList, results);
                final ArrayList<Channel> guideChannels = new ArrayList<>(channels);
                final String server = prefs.getString("xtream_server", "");
                final String username = prefs.getString("xtream_username", "");
                final String password = prefs.getString("xtream_password", "");
                Thread worker = new Thread(() -> {
                    ArrayList<EpgProgram> programs = new ArrayList<>();
                    ExecutorService pool = Executors.newFixedThreadPool(4);
                    try {
                        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
                            throw new Exception("Xtream connection details are missing");
                        }
                        String base = server.endsWith("/")
                                ? server.substring(0, server.length() - 1) : server;
                        String api = base + "/player_api.php?username="
                                + apiEncode(username) + "&password=" + apiEncode(password);

                        for (int offset = 0; offset < guideChannels.size(); offset += 4) {
                            ArrayList<Future<ArrayList<EpgProgram>>> batch = new ArrayList<>();
                            int end = Math.min(offset + 4, guideChannels.size());
                            for (int i = offset; i < end; i++) {
                                Channel channel = guideChannels.get(i);
                                if (channel.xtreamStreamId != null
                                        && !channel.xtreamStreamId.isEmpty()
                                        && channel.tvgId != null && !channel.tvgId.isEmpty()) {
                                    batch.add(pool.submit(
                                            () -> loadXtreamChannelEpg(api, channel)));
                                }
                            }
                            for (Future<ArrayList<EpgProgram>> future : batch) {
                                try {
                                    programs.addAll(future.get());
                                } catch (Exception e) {
                                    android.util.Log.w("StreamNest", "Xtream EPG request failed", e);
                                }
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.w("StreamNest", "Unable to load Xtream EPG", e);
                    } finally {
                        pool.shutdownNow();
                    }
                    final ArrayList<EpgProgram> loaded = programs;
                    runOnUiThread(() -> {
                        guidePrograms = loaded;
                        updateGuidePreviewProgramInfo(guidePreviewChannel);
                        renderGuidePrograms(loaded, channelMap, status, categoryList, results);
                    });
                });
                worker.start();
            } else {
                renderGuidePrograms(new ArrayList<>(), channelMap, status, categoryList, results);
            }
        } else {
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
                    String currentDescription = null;
                    long currentStart = 0;
                    long currentStop = 0;
                    int event = parser.getEventType();

                    while (event != XmlPullParser.END_DOCUMENT
                            && programs.size() < 100000) {
                        if (event == XmlPullParser.START_TAG
                                && "programme".equals(parser.getName())) {
                            currentChannel = parser.getAttributeValue(null, "channel");
                            currentTitle = null;
                            currentDescription = null;
                            currentStart = parseXmltvTime(
                                    parser.getAttributeValue(null, "start"));
                            currentStop = parseXmltvTime(
                                    parser.getAttributeValue(null, "stop"));
                        } else if (event == XmlPullParser.START_TAG
                                && "title".equals(parser.getName())
                                && currentChannel != null) {
                            currentTitle = parser.nextText();
                        } else if (event == XmlPullParser.START_TAG
                                && "desc".equals(parser.getName())
                                && currentChannel != null) {
                            currentDescription = parser.nextText();
                        } else if (event == XmlPullParser.END_TAG
                                && "programme".equals(parser.getName())) {
                            if (currentChannel != null && currentTitle != null
                                    && currentStart > 0 && currentStop > currentStart) {
                                programs.add(new EpgProgram(currentChannel,
                                        currentTitle.trim(), currentDescription,
                                        currentStart, currentStop));
                            }
                            currentChannel = null;
                            currentTitle = null;
                            currentDescription = null;
                        }
                        event = parser.next();
                    }
                }

                final ArrayList<EpgProgram> loaded = programs;
                runOnUiThread(() -> {
                    guidePrograms = loaded;
                        updateGuidePreviewProgramInfo(guidePreviewChannel);
                    renderGuidePrograms(loaded, channelMap, status, categoryList, results);
                });
            } catch (Exception e) {
                final String message = e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage();
                android.util.Log.e("StreamNest", "Could not load the EPG: " + message);
                runOnUiThread(() -> {
                    startupGuidePending = false;
                    startupPreviewSuppressed = false;
                    removeStartupCover();
                    status.setText("Could not load EPG: " + message);
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
        worker.start();
        }
    }

    private ArrayList<EpgProgram> loadXtreamChannelEpg(
            String api, Channel channel) throws Exception {
        ArrayList<EpgProgram> programs = new ArrayList<>();
        if (channel.xtreamStreamId == null
                || channel.xtreamStreamId.isEmpty()
                || channel.tvgId == null || channel.tvgId.isEmpty()) {
            return programs;
        }

        String url = api + "&action=get_short_epg&stream_id="
                + Uri.encode(channel.xtreamStreamId) + "&limit=6";
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "StreamNestIPTV/1.0");

            if (connection.getResponseCode() < 200
                    || connection.getResponseCode() >= 300) {
                return programs;
            }

            StringBuilder json = new StringBuilder();
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(input, StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    json.append(buffer, 0, count);
                }
            }

            JSONObject response = new JSONObject(json.toString());
            JSONArray listings = response.optJSONArray("epg_listings");
            if (listings == null) {
                listings = response.optJSONArray("data");
            }
            if (listings == null) return programs;

            for (int i = 0; i < listings.length(); i++) {
                JSONObject item = listings.optJSONObject(i);
                if (item == null) continue;

                String encodedTitle = item.optString("title", "");
                String title = encodedTitle;
                try {
                    byte[] decoded = android.util.Base64.decode(
                            encodedTitle, android.util.Base64.DEFAULT);
                    String decodedTitle = new String(
                            decoded, StandardCharsets.UTF_8).trim();
                    if (!decodedTitle.isEmpty()) title = decodedTitle;
                } catch (IllegalArgumentException ignored) {
                }

                long start = normalizeXtreamTimestamp(item.optLong("start_timestamp", 0));
                long stop = normalizeXtreamTimestamp(item.optLong("stop_timestamp", 0));
                if (start <= 0) start = parseXtreamEpgTime(
                        item.optString("start", ""));
                if (stop <= 0) stop = parseXtreamEpgTime(
                        item.optString("end", ""));
                if (title != null && !title.trim().isEmpty()
                        && start > 0 && stop > start) {
                    programs.add(new EpgProgram(
                            channel.tvgId, title.trim(), "", start, stop));
                }
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
        return programs;
    }

    private long normalizeXtreamTimestamp(long timestamp) {
        if (timestamp <= 0) return 0;
        return timestamp >= 100000000000L ? timestamp : timestamp * 1000L;
    }

    private long parseXtreamEpgTime(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        try {
            long timestamp = Long.parseLong(value.trim());
            return normalizeXtreamTimestamp(timestamp);
        } catch (NumberFormatException ignored) {
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", Locale.US);
            format.setLenient(false);
            Date date = format.parse(value.trim());
            return date == null ? 0 : date.getTime();
        } catch (ParseException ignored) {
            return 0;
        }
    }

    private String normalizeEpgId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static final ThreadLocal<SimpleDateFormat> XMLTV_TIME_WITH_ZONE =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    SimpleDateFormat format =
                            new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
                    format.setLenient(false);
                    return format;
                }
            };

    private static final ThreadLocal<SimpleDateFormat> XMLTV_TIME_DEFAULT_ZONE =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    SimpleDateFormat format =
                            new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                    format.setLenient(false);
                    return format;
                }
            };

    private long parseXmltvTime(String value) {
        if (value == null || value.trim().isEmpty()) return 0;

        String timestamp = value.trim();
        java.text.ParsePosition position = new java.text.ParsePosition(0);
        Date date = XMLTV_TIME_WITH_ZONE.get().parse(timestamp, position);
        if (date != null && position.getIndex() == timestamp.length()) {
            return date.getTime();
        }

        position = new java.text.ParsePosition(0);
        date = XMLTV_TIME_DEFAULT_ZONE.get().parse(timestamp, position);
        if (date != null && position.getIndex() == timestamp.length()) {
            return date.getTime();
        }

        return 0;
    }

    private void applySavedGuideCategoryOrder(java.util.List<String> categories) {
        try {
            JSONArray saved = new JSONArray(getPreferences(MODE_PRIVATE)
                    .getString(playlistPreferenceKey("guide_category_order"), "[]"));
            ArrayList<String> ordered = new ArrayList<>();
            for (int i = 0; i < saved.length(); i++) {
                String name = saved.optString(i, "");
                if (categories.remove(name)) ordered.add(name);
            }
            ordered.addAll(categories);
            categories.clear();
            categories.addAll(ordered);
        } catch (Exception ignored) {
        }
    }

    private void applySavedCategoryOrder(LinearLayout list, String key, int fixedCount) {
        try {
            JSONArray saved = new JSONArray(getPreferences(MODE_PRIVATE).getString(playlistPreferenceKey(key), "[]"));
            ArrayList<View> movable = new ArrayList<>();
            for (int i = fixedCount; i < list.getChildCount(); i++) movable.add(list.getChildAt(i));
            ArrayList<View> ordered = new ArrayList<>();
            for (int i = 0; i < saved.length(); i++) {
                String id = saved.optString(i, "");
                for (View child : movable) {
                    if (id.equals(child.getTag()) && !ordered.contains(child)) { ordered.add(child); break; }
                }
            }
            for (View child : movable) if (!ordered.contains(child)) ordered.add(child);
            while (list.getChildCount() > fixedCount) list.removeViewAt(fixedCount);
            for (View child : ordered) list.addView(child);
        } catch (Exception ignored) {}
    }

    private void saveCategoryOrder(LinearLayout list, String key, int fixedCount) {
        JSONArray order = new JSONArray();
        for (int i = fixedCount; i < list.getChildCount(); i++) {
            Object tag = list.getChildAt(i).getTag();
            if (tag instanceof String) order.put((String) tag);
        }
        getPreferences(MODE_PRIVATE).edit().putString(playlistPreferenceKey(key), order.toString()).apply();
    }

    private void showCategoryMoveMenu(TextView item, LinearLayout list, ScrollView scroll, String key, int fixedCount) {
        int from = list.indexOfChild(item);
        if (from < fixedCount) return;
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackgroundColor(PANEL);
        TextView heading = label("CATEGORY ORDER", 15);
        heading.setGravity(android.view.Gravity.CENTER);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(0, dp(4), 0, dp(10));
        panel.addView(heading);
        String[] arrows = {"↑", "↓", "⇧", "⇩"};
        for (int a = 0; a < arrows.length; a++) {
            final int action = a;
            TextView button = label(arrows[a], 29);
            button.setTextColor(Color.WHITE);
            button.setGravity(android.view.Gravity.CENTER);
            button.setFocusable(true);
            button.setClickable(true);
            String[] descriptions = {"Move Up", "Move Down", "Move to Top", "Move to Bottom"};
            button.setContentDescription(descriptions[a]);
            button.setPadding(dp(12), dp(5), dp(12), dp(5));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            bp.setMargins(0, dp(3), 0, dp(3));
            panel.addView(button, bp);
            button.setOnFocusChangeListener((v, focused) -> {
                android.graphics.drawable.GradientDrawable shape =
                        new android.graphics.drawable.GradientDrawable();
                shape.setColor(focused ? BLUE : 0xFF26364F);
                shape.setCornerRadius(dp(5));
                shape.setStroke(dp(1), focused ? Color.WHITE : 0xFF667788);
                v.setBackground(shape);
            });
            button.setOnClickListener(v -> {
                int current = list.indexOfChild(item);
                int target = current;
                if (action == 0) target--;
                if (action == 1) target++;
                if (action == 2) target = fixedCount;
                if (action == 3) target = list.getChildCount() - 1;
                if (target >= fixedCount && target < list.getChildCount() && target != current) {
                    list.removeViewAt(current);
                    list.addView(item, target);
                    saveCategoryOrder(list, key, fixedCount);
                }
                scroll.post(() -> {
                    scroll.smoothScrollTo(0, item.getTop());
                    button.requestFocus();
                });
            });
        }
        dialog.setContentView(panel);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout((int)(230 * getResources().getDisplayMetrics().density), -2);
        }
        dialog.show();
        window = dialog.getWindow();
        if (window != null) window.setLayout((int)(230 * getResources().getDisplayMetrics().density), -2);
        panel.getChildAt(1).requestFocus();
    }

    private String guideChannelOrderKey(Channel channel) {
        return (channel.name == null ? "" : channel.name) + "\u001f"
                + (channel.url == null ? "" : channel.url) + "\u001f"
                + (channel.tvgId == null ? "" : channel.tvgId) + "\u001f"
                + (channel.xtreamStreamId == null ? "" : channel.xtreamStreamId);
    }

    private void applySavedGuideChannelOrder(String category, ArrayList<Channel> channels) {
        try {
            JSONObject saved = new JSONObject(getPreferences(MODE_PRIVATE)
                    .getString(playlistPreferenceKey("guide_channel_orders"), "{}"));
            JSONArray order = saved.optJSONArray(category);
            if (order == null) return;
            ArrayList<Channel> ordered = new ArrayList<>();
            for (int i = 0; i < order.length(); i++) {
                String key = order.optString(i, "");
                for (Channel channel : channels) {
                    if (key.equals(guideChannelOrderKey(channel)) && !ordered.contains(channel)) {
                        ordered.add(channel);
                        break;
                    }
                }
            }
            for (Channel channel : channels) {
                if (!ordered.contains(channel)) ordered.add(channel);
            }
            channels.clear();
            channels.addAll(ordered);
        } catch (Exception ignored) {
        }
    }

    private void saveGuideChannelOrder(String category, ArrayList<Channel> channels) {
        try {
            JSONObject saved = new JSONObject(getPreferences(MODE_PRIVATE)
                    .getString(playlistPreferenceKey("guide_channel_orders"), "{}"));
            JSONArray order = new JSONArray();
            for (Channel channel : channels) {
                order.put(guideChannelOrderKey(channel));
            }
            saved.put(category, order);
            getPreferences(MODE_PRIVATE).edit()
                    .putString(playlistPreferenceKey("guide_channel_orders"), saved.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void showGuideChannelMoveMenu(View anchor, Channel channel, String category,
            ArrayList<Channel> channels,
            Map<String, ArrayList<EpgProgram>> grouped,
            Map<String, ArrayList<Channel>> channelMap, LinearLayout results) {

        final int menuScrollY;
        android.view.ViewParent parent = anchor.getParent();
        while (parent != null && !(parent instanceof ScrollView)) {
            parent = parent.getParent();
        }
        menuScrollY = parent instanceof ScrollView
                ? ((ScrollView) parent).getScrollY() : 0;

        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackgroundColor(PANEL);

        TextView heading = label("CHANNEL ORDER", 15);
        heading.setGravity(android.view.Gravity.CENTER);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(0, dp(4), 0, dp(10));
        panel.addView(heading);

        String[] actions = {"↑", "↓", "⇧", "⇩",
                favorites.contains(channel.url) ? "★" : "☆"};
        String[] descriptions = {"Move Up", "Move Down",
                "Move to Top", "Move to Bottom",
                favorites.contains(channel.url) ? "Remove from Favorites" : "Add to Favorites"};

        for (int i = 0; i < actions.length; i++) {
            final int actionIndex = i;
            TextView button = label(actions[i], 29);
            button.setTextColor(Color.WHITE);
            button.setGravity(android.view.Gravity.CENTER);
            button.setFocusable(true);
            button.setClickable(true);
            button.setContentDescription(descriptions[i]);
            button.setPadding(dp(12), dp(5), dp(12), dp(5));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            params.setMargins(0, dp(3), 0, dp(3));
            panel.addView(button, params);

            button.setOnFocusChangeListener((v, focused) -> {
                android.graphics.drawable.GradientDrawable bg =
                        new android.graphics.drawable.GradientDrawable();
                bg.setColor(focused ? BLUE : 0xFF26364F);
                bg.setCornerRadius(dp(5));
                bg.setStroke(dp(1), focused ? Color.WHITE : 0xFF667788);
                v.setBackground(bg);
            });

            button.setOnClickListener(v -> {
                if (actionIndex == 4) {
                    boolean removing = favorites.contains(channel.url);
                    if (channel.url == null) return;

                    if (removing) {
                        favorites.remove(channel.url);
                    } else {
                        favorites.add(channel.url);
                    }

                    getPreferences(MODE_PRIVATE).edit()
                            .putStringSet(playlistPreferenceKey("favorite_urls"),
                                    new LinkedHashSet<>(favorites))
                            .apply();

                    ArrayList<EpgProgram> favoritePrograms = grouped.get("Favorites");
                    if (favoritePrograms != null) {
                        if (removing) {
                            java.util.Iterator<EpgProgram> iterator =
                                    favoritePrograms.iterator();
                            while (iterator.hasNext()) {
                                EpgProgram program = iterator.next();
                                ArrayList<Channel> matches =
                                        channelMap.get(normalizeEpgId(program.channelId));
                                if (matches != null && matches.contains(channel)) {
                                    boolean stillFavorited = false;
                                    for (Channel match : matches) {
                                        if (match != channel && match.url != null
                                                && favorites.contains(match.url)) {
                                            stillFavorited = true;
                                            break;
                                        }
                                    }
                                    if (!stillFavorited) iterator.remove();
                                }
                            }
                        } else {
                            String channelCategory = channel.group;
                            if (channelCategory == null || channelCategory.trim().isEmpty()) {
                                channelCategory = "Uncategorized";
                            }
                            ArrayList<EpgProgram> source = grouped.get(channelCategory);
                            if (source != null) {
                                for (EpgProgram program : source) {
                                    ArrayList<Channel> matches =
                                            channelMap.get(normalizeEpgId(program.channelId));
                                    if (matches != null && matches.contains(channel)
                                            && !favoritePrograms.contains(program)) {
                                        favoritePrograms.add(program);
                                    }
                                }
                            }
                        }
                    }

                    button.setText(removing ? "☆" : "★");
                    button.setContentDescription(removing
                            ? "Add to Favorites" : "Remove from Favorites");

                    if ("Favorites".equals(category)) {
                        guidePendingMoveScrollY = menuScrollY;
                        guidePendingMoveChannelKey = "";
                        renderGuideCategoryPrograms(category, grouped, channelMap, results);
                    }

                    Toast.makeText(this,
                            removing ? "Removed from Favorites" : "Added to Favorites",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                int from = channels.indexOf(channel);
                int to = from;
                if (actionIndex == 0) to--;
                else if (actionIndex == 1) to++;
                else if (actionIndex == 2) to = 0;
                else if (actionIndex == 3) to = channels.size() - 1;

                if (from < 0 || to < 0 || to >= channels.size() || to == from) {
                    return;
                }

                guidePendingMoveScrollY = menuScrollY;
                guidePendingMoveChannelKey = guideChannelOrderKey(channel);
                channels.remove(from);
                channels.add(to, channel);
                saveGuideChannelOrder(category, channels);
                renderGuideCategoryPrograms(category, grouped, channelMap, results);
                button.requestFocus();
            });
        }

        dialog.setContentView(panel);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(d -> {
            for (View cell : guideChannelNameCells) {
                if (cell.getTag() == channel) {
                    cell.requestFocus();
                    break;
                }
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(
                    android.R.color.transparent);
            dialog.getWindow().setLayout(dp(230),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }

        panel.getChildAt(1).requestFocus();
    }

    private void updateGuideCategoryItemStyle(View view) {
        boolean selected = view.isSelected();
        boolean focused = view.hasFocus();

        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(focused ? BLUE : PANEL);
        background.setCornerRadius(dp(4));
        background.setStroke(
                dp(selected ? 3 : 1),
                selected ? BLUE : 0xFF777777);
        view.setBackground(background);
    }

    private void renderGuidePrograms(List<EpgProgram> programs,
            Map<String, ArrayList<Channel>> channelMap, TextView status,
            LinearLayout categoryList, LinearLayout results) {
        categoryList.removeAllViews();
        results.removeAllViews();

        long now = System.currentTimeMillis();
        long horizon = now + 24L * 60L * 60L * 1000L;
        int matched = 0;
        Map<String, ArrayList<EpgProgram>> grouped = new java.util.LinkedHashMap<>();
        Map<String, Integer> categoryCounts = new HashMap<>();
            grouped.put("Favorites", new ArrayList<EpgProgram>());
            categoryCounts.put("Favorites", favorites.size());

        for (Channel channel : channels) {
            String category = channel.group;
            if (category == null || category.trim().isEmpty()) {
                category = "Uncategorized";
            }

            if (!grouped.containsKey(category)) {
                grouped.put(category, new ArrayList<EpgProgram>());
            }
            Integer count = categoryCounts.get(category);
            categoryCounts.put(category, count == null ? 1 : count + 1);
        }

        for (EpgProgram program : programs) {
            ArrayList<Channel> matchingChannels =
                    channelMap.get(normalizeEpgId(program.channelId));
            if (matchingChannels == null || matchingChannels.isEmpty()) continue;
            matched++;
            if (program.stopMillis > now && program.startMillis < horizon) {
                Set<String> addedCategories = new HashSet<>();
                for (Channel channel : matchingChannels) {
                    String category = channel.group;
                    if (category == null || category.trim().isEmpty()) {
                        category = "Uncategorized";
                    }
                    if (addedCategories.add(category)) {
                        if (!grouped.containsKey(category)) {
                            grouped.put(category, new ArrayList<>());
                        }
                        grouped.get(category).add(program);
                                    if (favorites.contains(channel.url)
                                            && addedCategories.add("Favorites")) {
                                        grouped.get("Favorites").add(program);
                                    }
                    }
                }
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

        java.util.List<String> categories = new ArrayList<>(grouped.keySet());
        applySavedGuideCategoryOrder(categories);

        for (String category : categories) {
            Integer savedCount = categoryCounts.get(category);
            int channelCount = savedCount == null ? 0 : savedCount;
            TextView item = label(category + " (" + channelCount + ")", 15);
            item.setPadding(dp(10), dp(14), dp(8), dp(14));
            item.setFocusable(true);
            item.setClickable(true);
            item.setLongClickable(true);
            item.setTag(category);
            item.setOnLongClickListener(v -> {
                showCategoryMoveMenu(item, categoryList, guideCategoryScrollView, "guide_category_order", 0);
                return true;
            });
            item.setSelected(false);
            item.setOnFocusChangeListener((v, focused) ->
                    updateGuideCategoryItemStyle(v));
            item.setOnClickListener(v -> {
                for (int i = 0; i < categoryList.getChildCount(); i++) {
                    android.view.View child = categoryList.getChildAt(i);
                    child.setSelected(child == item);
                    updateGuideCategoryItemStyle(child);
                }
                renderGuideCategoryPrograms(category, grouped, channelMap, results);
            });
            updateGuideCategoryItemStyle(item);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            categoryList.addView(item, params);
        }

        if (categoryList.getChildCount() > 0) {
            String preferredCategory = null;
            Channel preferredChannel = guidePreviewChannel;

            if (preferredChannel == null) {
                String lastUrl = getPreferences(MODE_PRIVATE)
                        .getString("last_channel_url", "");
                for (Channel channel : channels) {
                    if (channel.url != null && channel.url.equals(lastUrl)) {
                        preferredChannel = channel;
                        break;
                    }
                }
            }

            if (preferredChannel != null) {
                preferredCategory = preferredChannel.group;
                if (preferredCategory == null
                        || preferredCategory.trim().isEmpty()) {
                    preferredCategory = "Uncategorized";
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

        if (startupGuidePending) {
            startupGuidePending = false;
            startupPreviewSuppressed = false;
            if (!launchLastPlayedChannel()) {
                removeStartupCover();
            }
        }
    }

    private void renderGuideCategoryPrograms(String category,
            Map<String, ArrayList<EpgProgram>> grouped,
            Map<String, ArrayList<Channel>> channelMap, LinearLayout results) {
        final int renderGeneration = ++guideRenderGeneration;
        guideAppendMoreRows = null;
        results.removeAllViews();
        guideChannelNameCells.clear();



        ArrayList<EpgProgram> categoryPrograms = grouped.get(category);
if (categoryPrograms == null) {
    categoryPrograms = new ArrayList<>();
}

        final long slotMillis = 30L * 60L * 1000L;
        java.util.Calendar guideCalendar =
                java.util.Calendar.getInstance();
        int currentHour = guideCalendar.get(java.util.Calendar.HOUR_OF_DAY);
        guideCalendar.set(java.util.Calendar.HOUR_OF_DAY,
                (currentHour / 2) * 2);
        guideCalendar.set(java.util.Calendar.MINUTE, 0);
        guideCalendar.set(java.util.Calendar.SECOND, 0);
        guideCalendar.set(java.util.Calendar.MILLISECOND, 0);
        final long gridStart = guideCalendar.getTimeInMillis();
        final long gridEnd = gridStart + 24L * 60L * 60L * 1000L;
        final int pixelsPerMinute = Math.max(2,
                (getResources().getConfiguration().screenWidthDp - 144) / 120);
        final int channelWidth = dp(isPhoneLayout() ? 120 : 175);
        final int slotWidth = dp(30 * pixelsPerMinute);
        final int rowHeight = dp(isPhoneLayout() ? 54 : 50);

        SimpleDateFormat clock =
                new SimpleDateFormat("h:mm a", Locale.getDefault());

        LinearLayout channelRows = new LinearLayout(this);
        channelRows.setOrientation(LinearLayout.VERTICAL);
        channelRows.setDividerDrawable(new android.graphics.drawable.ColorDrawable(0xFF777777));
        channelRows.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout stationaryLabels = new LinearLayout(this);
        stationaryLabels.setOrientation(LinearLayout.VERTICAL);
        grid.setDividerDrawable(
                new android.graphics.drawable.ColorDrawable(0xFF777777));
        grid.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

        LinearLayout timeHeader = new LinearLayout(this);
        timeHeader.setOrientation(LinearLayout.HORIZONTAL);
        timeHeader.setDividerDrawable(
                new android.graphics.drawable.ColorDrawable(0xFF777777));
        timeHeader.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);


        SimpleDateFormat dateTimeLabel =
                new SimpleDateFormat("EEE, MMM d\nh:mm a", Locale.getDefault());
        TextView channelHeader = label(dateTimeLabel.format(new Date()), 11);
        channelHeader.setGravity(android.view.Gravity.CENTER);
        channelHeader.setPadding(dp(3), dp(3), dp(3), dp(3));
        channelHeader.setBackgroundColor(PANEL);

        for (long time = gridStart; time < gridEnd;
                time += slotMillis) {
            TextView tick = label(clock.format(new Date(time)), 11);
            tick.setGravity(android.view.Gravity.CENTER);
            tick.setPadding(dp(2), dp(4), dp(2), dp(4));
            tick.setBackgroundColor(PANEL);
            LinearLayout.LayoutParams tickParams =
                    new LinearLayout.LayoutParams(slotWidth, dp(isPhoneLayout() ? 42 : 32));
            timeHeader.addView(tick, tickParams);
        }
        HorizontalScrollView headerScroll = new HorizontalScrollView(this);
        headerScroll.setHorizontalScrollBarEnabled(false);
        headerScroll.addView(timeHeader);

        HorizontalScrollView horizontalScroll = new HorizontalScrollView(this);
        horizontalScroll.setHorizontalScrollBarEnabled(true);

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

        // Time ruler is displayed separately above the scrolling channel rows.

        ArrayList<Channel> rowChannels = new ArrayList<>();
        Map<Channel, ArrayList<EpgProgram>> byChannel = new java.util.LinkedHashMap<>();
        for (Channel channel : channels) {
            String cat = channel.group;
            if (cat == null || cat.trim().isEmpty()) cat = "Uncategorized";
            if ("Favorites".equals(category)) {
                if (channel.url == null || !favorites.contains(channel.url)) continue;
            } else if (!category.equals(cat)) {
                continue;
            }
            rowChannels.add(channel);
            byChannel.put(channel, new ArrayList<>());
        }
        Collections.sort(rowChannels, (x, y) ->
                (x.name == null ? "" : x.name).compareTo(y.name == null ? "" : y.name));
        applySavedGuideChannelOrder(category, rowChannels);
        for (EpgProgram program : categoryPrograms) {
            ArrayList<Channel> matches = channelMap.get(normalizeEpgId(program.channelId));
            if (matches == null) continue;
            for (Channel channel : matches) {
                if (byChannel.containsKey(channel)) byChannel.get(channel).add(program);
            }
        }

        Channel firstPreviewChannel = rowChannels.isEmpty() ? null : rowChannels.get(0);
        String preferredChannelUrl = getPreferences(MODE_PRIVATE)
                .getString("last_channel_url", "");
        final View[] lastWatchedRow = {null};
        final ScrollView[] rowScrollRef = new ScrollView[1];
        final boolean[] lastWatchedScrollScheduled = {false};
        final int[] nextGuideRow = {0};
        final int guideBatchSize = isTelevisionDevice()
                ? GUIDE_CHANNEL_BATCH_SIZE : Math.max(1, rowChannels.size());
        final Runnable[] appendGuideRows = new Runnable[1];
        appendGuideRows[0] = () -> {
            if (renderGeneration != guideRenderGeneration) return;
            int batchEnd = Math.min(rowChannels.size(),
                    nextGuideRow[0] + guideBatchSize);
            while (nextGuideRow[0] < batchEnd) {
                Channel rowChannel = rowChannels.get(nextGuideRow[0]++);

            ArrayList<EpgProgram> programs = byChannel.get(rowChannel);
            Collections.sort(programs, Comparator.comparingLong(
                    program -> program.startMillis));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setDividerDrawable(
                    new android.graphics.drawable.ColorDrawable(0xFF777777));
            row.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

            String rowName = rowChannel.name == null || rowChannel.name.trim().isEmpty() ? "Unnamed channel" : rowChannel.name;

        if (rowChannel.url != null
                && rowChannel.url.equals(preferredChannelUrl)) {
            lastWatchedRow[0] = row;
        }
            LinearLayout channelCell = new LinearLayout(this);
            channelCell.setOrientation(LinearLayout.HORIZONTAL);
            channelCell.setGravity(android.view.Gravity.CENTER_VERTICAL);
            channelCell.setPadding(dp(5), dp(3), dp(5), dp(3));
            channelCell.setFocusable(true);
            channelCell.setClickable(true);
            channelCell.setTag(rowChannel);
            channelCell.setOnFocusChangeListener((v, focused) ->
                    updateGuideChannelNameCell(v, rowChannel, focused));
            guideChannelNameCells.add(channelCell);
            updateGuideChannelNameCell(channelCell, rowChannel, false);
            channelCell.setOnClickListener(v -> playGuidePreview(rowChannel));
            channelCell.setLongClickable(true);
            channelCell.setOnLongClickListener(v -> {
                showGuideChannelMoveMenu(channelCell, rowChannel, category,
                        rowChannels, grouped, channelMap, results);
                return true;
            });

            android.widget.ImageView logoView =
                    new android.widget.ImageView(this);
            logoView.setScaleType(
                    android.widget.ImageView.ScaleType.FIT_CENTER);
            channelCell.addView(logoView, new LinearLayout.LayoutParams(
                    dp(34), dp(34)));

            TextView channelLabel = label(rowName, 13);
            channelLabel.setGravity(
                    android.view.Gravity.CENTER_VERTICAL);
            channelLabel.setPadding(dp(6), dp(4), dp(2), dp(4));
            channelCell.addView(channelLabel, new LinearLayout.LayoutParams(
                    0, rowHeight, 1f));

            channelRows.addView(channelCell, new LinearLayout.LayoutParams(channelWidth, rowHeight));

            if (rowChannel != null && rowChannel.logoUrl != null
                    && !rowChannel.logoUrl.trim().isEmpty()) {
                loadChannelLogo(logoView, rowChannel.logoUrl);
            }

            if (programs.isEmpty()) {
                TextView stationaryLabel = label(rowName, 12);
                stationaryLabel.setGravity(android.view.Gravity.CENTER_VERTICAL
                        | android.view.Gravity.LEFT);
                stationaryLabel.setPadding(dp(6), dp(2), dp(4), dp(2));
                stationaryLabel.setMaxLines(2);
                stationaryLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
                configureGuideNoInfoBrick(stationaryLabel, rowChannel);
                stationaryLabels.addView(stationaryLabel,
                        new LinearLayout.LayoutParams(dp(160), rowHeight));

                final long twoHours = 2L * 60L * 60L * 1000L;
                for (int blockIndex = 0; blockIndex < 12; blockIndex++) {
                    long blockStart = gridStart + blockIndex * twoHours;
                    long blockEnd = blockStart + twoHours;
                    TextView cell = label(rowName, 12);
                    cell.setGravity(android.view.Gravity.CENTER);
                    cell.setPadding(dp(4), dp(2), dp(4), dp(2));
                    cell.setMaxLines(2);
                    cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    configureGuideNoInfoBrick(cell, rowChannel);
                    cell.setOnClickListener(v -> {
                        long clickTime = System.currentTimeMillis();
                        if (clickTime < blockStart || clickTime >= blockEnd) return;
                        if (guidePreviewChannel != null
                                && rowChannel.url != null
                                && rowChannel.url.equals(guidePreviewChannel.url)) {
                            playChannel(rowChannel);
                        } else {
                            playGuidePreview(rowChannel);
                        }
                    });
                    row.addView(cell, new LinearLayout.LayoutParams(
                            dp(120 * pixelsPerMinute), rowHeight));
                }
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
                                rowName
                                        + "  " + clock.format(new Date(gapCursor)), 12);
                        gap.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.LEFT);
                        gap.setPadding(dp(4), dp(3), dp(4), dp(3));
                        android.graphics.drawable.GradientDrawable gapBackground =
                        new android.graphics.drawable.GradientDrawable();
                gapBackground.setColor(PANEL);
                gapBackground.setStroke(dp(1), 0xFF777777);
                gap.setBackground(gapBackground);
                        configureGuideNoInfoBrick(gap, rowChannel);
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
                normalBrick.setCornerRadius(dp(4));
                normalBrick.setStroke(dp(1), 0xFF777777);

                android.graphics.drawable.GradientDrawable focusedBrick =
                        new android.graphics.drawable.GradientDrawable();
                focusedBrick.setColor(BLUE);
                focusedBrick.setCornerRadius(dp(4));
                focusedBrick.setStroke(dp(1), 0xFFFFFFFF);

                brick.setBackground(normalBrick);
                brick.setOnFocusChangeListener((v, focused) -> {
                    v.setBackground(focused ? focusedBrick : normalBrick);
                    v.setSelected(focused);
                });

                brick.setOnClickListener(v -> {
                if (guidePreviewChannel != null
                        && rowChannel.url != null
                        && rowChannel.url.equals(guidePreviewChannel.url)) {
                    playChannel(rowChannel);
                } else {
                    playGuidePreview(rowChannel);
                }
            });
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
                        rowName, 12);
                gap.setGravity(android.view.Gravity.CENTER);
                gap.setPadding(dp(4), dp(3), dp(4), dp(3));
                android.graphics.drawable.GradientDrawable gapBackground =
                        new android.graphics.drawable.GradientDrawable();
                gapBackground.setColor(PANEL);
                gapBackground.setStroke(dp(1), 0xFF777777);
                gap.setBackground(gapBackground);
                configureGuideNoInfoBrick(gap, rowChannel);
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
            guideAppendMoreRows = nextGuideRow[0] < rowChannels.size()
                    ? appendGuideRows[0] : null;
            if (!isPhoneLayout() && lastWatchedRow[0] != null
                    && rowScrollRef[0] != null
                    && rowScrollRef[0].isAttachedToWindow()
                    && !lastWatchedScrollScheduled[0]
                    && guidePendingMoveChannelKey.isEmpty()) {
                lastWatchedScrollScheduled[0] = true;
                rowScrollRef[0].post(() -> rowScrollRef[0].smoothScrollTo(
                        0, lastWatchedRow[0].getTop()));
            }
        };
        guideAppendMoreRows = appendGuideRows[0];
        appendGuideRows[0].run();

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

        String savedCategory = lastChannel == null ? "" : lastChannel.group;
        if (savedCategory == null || savedCategory.trim().isEmpty()) {
            savedCategory = "Uncategorized";
        }
        final boolean findLastWatchedRow = !isPhoneLayout()
                && lastChannel != null && category.equals(savedCategory);

        if (guidePreviewChannel == null) {
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
                if (startupPreviewSuppressed) {
                    guidePreviewChannel = previewChannel;
                } else {
                    playGuidePreview(previewChannel);
                }
            }
        }
        refreshGuideChannelNameBorders();

        android.widget.FrameLayout gridFrame =
                new android.widget.FrameLayout(this);
        gridFrame.addView(grid, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        horizontalScroll.addView(gridFrame);

        final android.os.Handler clockHandler =
                new android.os.Handler(android.os.Looper.getMainLooper());

        ScrollView channelVerticalScroll = new ScrollView(this);
        channelVerticalScroll.setFillViewport(false);
        channelVerticalScroll.addView(channelRows);

        ScrollView rowVerticalScroll = new ScrollView(this);
        rowScrollRef[0] = rowVerticalScroll;
        rowVerticalScroll.setFillViewport(false);
        rowVerticalScroll.addView(horizontalScroll);

        android.widget.FrameLayout programRowsFrame =
                new android.widget.FrameLayout(this);
        programRowsFrame.addView(rowVerticalScroll,
                new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        android.view.View currentTimeMarker = new android.view.View(this);
        currentTimeMarker.setBackgroundColor(BLUE);
        currentTimeMarker.setElevation(dp(8));
        android.widget.FrameLayout.LayoutParams markerParams =
                new android.widget.FrameLayout.LayoutParams(
                        dp(3),
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        markerParams.leftMargin = 0;
        programRowsFrame.addView(currentTimeMarker, markerParams);
        horizontalScroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            if (headerScroll.getScrollX() != x) {
                headerScroll.scrollTo(x, 0);
            }
            long now = System.currentTimeMillis();
            long elapsed = Math.max(0L, now - gridStart);
            int markerX = dp((int) Math.round((elapsed / 60000.0) * pixelsPerMinute)) - x;
            markerParams.leftMargin = markerX;
            currentTimeMarker.setLayoutParams(markerParams);
        });

        final int[] lastWindowIndex = {-1};
        final long[] lastInfoMinute = {-1L};
        final long twoHoursMillis = 2L * 60L * 60L * 1000L;
        final Runnable[] timelineScroller = new Runnable[1];
        timelineScroller[0] = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                channelHeader.setText(dateTimeLabel.format(new Date(now)));
                long infoMinute = now / 60000L;
                if (infoMinute != lastInfoMinute[0]) {
                    lastInfoMinute[0] = infoMinute;
                    updateGuidePreviewProgramInfo(guidePreviewChannel);
                }

                if (now >= gridEnd) {
                    renderGuideCategoryPrograms(
                            category, grouped, channelMap, results);
                    return;
                }

                long elapsedMillis = Math.max(0L, now - gridStart);
                int windowIndex = (int) (elapsedMillis / twoHoursMillis);
                long withinWindowMillis = elapsedMillis % twoHoursMillis;

                int markerX = dp((int) Math.round((elapsedMillis / 60000.0) * pixelsPerMinute)) - horizontalScroll.getScrollX();
                markerParams.leftMargin = markerX;
                currentTimeMarker.setLayoutParams(markerParams);

                if (lastWindowIndex[0] != windowIndex
                        && horizontalScroll.getChildCount() > 0
                        && horizontalScroll.getWidth() > 0
                        && horizontalScroll.getChildAt(0).getWidth() > 0) {
                    int targetX = dp(windowIndex * 120 * pixelsPerMinute);
                    int contentWidth =
                            horizontalScroll.getChildAt(0).getWidth();
                    int maxScroll = Math.max(
                            0, contentWidth - horizontalScroll.getWidth());
                    horizontalScroll.scrollTo(
                            Math.min(targetX, maxScroll), 0);
                    lastWindowIndex[0] = windowIndex;
                }

                clockHandler.postDelayed(this, 1000L);
            }
        };

        programRowsFrame.addOnAttachStateChangeListener(
                new android.view.View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        clockHandler.removeCallbacks(timelineScroller[0]);
                        programRowsFrame.post(timelineScroller[0]);
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        clockHandler.removeCallbacks(timelineScroller[0]);
                    }
                });

        channelVerticalScroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            if (rowVerticalScroll.getScrollY() != y) {
                rowVerticalScroll.scrollTo(0, y);
            }
        });
        rowVerticalScroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            if (channelVerticalScroll.getScrollY() != y) {
                channelVerticalScroll.scrollTo(0, y);
            }
            if (guideAppendMoreRows != null
                    && y + v.getHeight() >= rowVerticalScroll.getChildAt(0).getHeight() - dp(500)) {
                guideAppendMoreRows.run();
            }
        });

        final View targetLastWatchedRow = lastWatchedRow[0];
        if (!isPhoneLayout() && targetLastWatchedRow != null
                && guidePendingMoveChannelKey.isEmpty()) {
            rowVerticalScroll.post(() ->
                    rowVerticalScroll.smoothScrollTo(0, targetLastWatchedRow.getTop()));
        }

        LinearLayout channelColumn = new LinearLayout(this);
        channelColumn.setOrientation(LinearLayout.VERTICAL);
        channelColumn.addView(channelHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(isPhoneLayout() ? 42 : 32)));
        channelColumn.addView(channelVerticalScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout programColumn = new LinearLayout(this);
        programColumn.setOrientation(LinearLayout.VERTICAL);
        programColumn.addView(headerScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(isPhoneLayout() ? 42 : 32)));
        programColumn.addView(programRowsFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout timeline = new LinearLayout(this);
        timeline.setOrientation(LinearLayout.HORIZONTAL);
        timeline.addView(channelColumn, new LinearLayout.LayoutParams(channelWidth, LinearLayout.LayoutParams.MATCH_PARENT));
        timeline.addView(programColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        results.addView(timeline, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!guidePendingMoveChannelKey.isEmpty()) {
            restoreMovedGuideChannel(rowVerticalScroll, renderGeneration);
        }
        if (findLastWatchedRow && lastWatchedRow[0] == null
                && guidePendingMoveChannelKey.isEmpty()) {
            rowVerticalScroll.post(new Runnable() {
                @Override
                public void run() {
                    if (renderGeneration != guideRenderGeneration) return;
                    if (lastWatchedRow[0] != null || guideAppendMoreRows == null) return;
                    guideAppendMoreRows.run();
                    if (lastWatchedRow[0] == null && guideAppendMoreRows != null) {
                        rowVerticalScroll.post(this);
                    }
                }
            });
        }
    }

    private void restoreMovedGuideChannel(ScrollView rowScroll, int generation) {
        if (generation != guideRenderGeneration) return;
        String targetUrl = guidePendingMoveChannelKey;
        if (targetUrl.isEmpty()) return;
        for (View cell : guideChannelNameCells) {
            if (cell.getTag() instanceof Channel
                    && targetUrl.equals(guideChannelOrderKey((Channel) cell.getTag()))) {
                int scrollY = guidePendingMoveScrollY;
                guidePendingMoveChannelKey = "";
                guidePendingMoveScrollY = -1;
                rowScroll.post(() -> {
                    if (generation != guideRenderGeneration) return;
                    rowScroll.scrollTo(0, Math.max(0, scrollY));
                    cell.requestFocus();
                });
                return;
            }
        }
        if (guideAppendMoreRows != null) {
            guideAppendMoreRows.run();
            rowScroll.post(() -> restoreMovedGuideChannel(rowScroll, generation));
        } else {
            guidePendingMoveChannelKey = "";
            guidePendingMoveScrollY = -1;
        }
    }

    private void loadChannelLogo(
            android.widget.ImageView imageView, String logoUrl) {
        loadChannelLogo(imageView, logoUrl, 300);
    }

    private void loadChannelLogo(
            android.widget.ImageView imageView, String logoUrl,
            int maxDimension) {
        loadChannelLogo(imageView, logoUrl, maxDimension, logoExecutor);
    }

    private void loadChannelLogo(
            android.widget.ImageView imageView, String logoUrl,
            int maxDimension, ThreadPoolExecutor executor) {
        imageView.setTag(logoUrl);
        executor.execute(() -> {
            java.net.HttpURLConnection connection = null;
            java.io.File tempFile = null;

            try {
                connection = (java.net.HttpURLConnection)
                        new java.net.URL(logoUrl).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                tempFile = java.io.File.createTempFile(
                        "artwork_", ".img", getCacheDir());

                long totalBytes = 0;
                byte[] buffer = new byte[8192];

                try (java.io.InputStream stream =
                             connection.getInputStream();
                     java.io.FileOutputStream output =
                             new java.io.FileOutputStream(tempFile)) {

                    int count;
                    while ((count = stream.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                        totalBytes += count;
                        if (totalBytes > 10 * 1024 * 1024) {
                            throw new java.io.IOException(
                                    "Artwork exceeds 10 MB");
                        }
                        output.write(buffer, 0, count);
                    }
                }

                if (Thread.currentThread().isInterrupted()) return;

            android.graphics.BitmapFactory.Options bounds =
                        new android.graphics.BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(
                        tempFile.getAbsolutePath(), bounds);

                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return;
                }

                int sampleSize = 1;
                while (bounds.outWidth / sampleSize > maxDimension
                        || bounds.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2;
                }

                android.graphics.BitmapFactory.Options options =
                        new android.graphics.BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                options.inPreferredConfig =
                        android.graphics.Bitmap.Config.ARGB_8888;

                android.graphics.Bitmap bitmap =
                        android.graphics.BitmapFactory.decodeFile(
                                tempFile.getAbsolutePath(), options);

                if (Thread.currentThread().isInterrupted()) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap != null) {
                    boolean posted = imageView.post(() -> {
                        if (logoUrl.equals(imageView.getTag())) {
                            imageView.setImageBitmap(bitmap);
                        } else {
                            bitmap.recycle();
                        }
                    });

                    if (!posted) {
                        bitmap.recycle();
                    }
                }

            } catch (Exception e) {
            android.util.Log.d("StreamNest",
                        "Channel logo unavailable: " + logoUrl);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                if (tempFile != null) {
                    tempFile.delete();
                }
            }
        });
    }

    private void loadMovieBackdrop(
            android.widget.ImageView imageView, String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;
        imageView.setTag(imageUrl);

        logoExecutor.execute(() -> {
            java.net.HttpURLConnection connection = null;
            java.io.File tempFile = null;

            try {
                connection = (java.net.HttpURLConnection)
                        new java.net.URL(imageUrl).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                tempFile = java.io.File.createTempFile(
                        "movie_backdrop_", ".img", getCacheDir());

                long totalBytes = 0;
                byte[] buffer = new byte[8192];

                try (java.io.InputStream stream =
                             connection.getInputStream();
                     java.io.FileOutputStream output =
                             new java.io.FileOutputStream(tempFile)) {
                    int count;
                    while ((count = stream.read(buffer)) != -1) {
                        totalBytes += count;
                        if (totalBytes > 10 * 1024 * 1024) {
                            throw new java.io.IOException(
                                    "Movie artwork exceeds 10 MB");
                        }
                        output.write(buffer, 0, count);
                    }
                }

                android.graphics.BitmapFactory.Options bounds =
                        new android.graphics.BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(
                        tempFile.getAbsolutePath(), bounds);

                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return;
                }

                int sampleSize = 1;
                while (bounds.outWidth / sampleSize > 1920
                        || bounds.outHeight / sampleSize > 1080) {
                    sampleSize *= 2;
                }

                android.graphics.BitmapFactory.Options options =
                        new android.graphics.BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                options.inPreferredConfig =
                        android.graphics.Bitmap.Config.ARGB_8888;

                android.graphics.Bitmap bitmap =
                        android.graphics.BitmapFactory.decodeFile(
                                tempFile.getAbsolutePath(), options);

                if (bitmap != null) {
                    android.util.Log.d("StreamNest",
                            "Movie artwork: source=" + bounds.outWidth
                            + "x" + bounds.outHeight
                            + ", decoded=" + bitmap.getWidth()
                            + "x" + bitmap.getHeight()
                            + ", bytes=" + totalBytes);

                    boolean posted = imageView.post(() -> {
                        if (imageUrl.equals(imageView.getTag())) {
                            imageView.setImageBitmap(bitmap);
                        } else {
                            bitmap.recycle();
                        }
                    });
                    if (!posted) bitmap.recycle();
                }
            } catch (Exception e) {
                android.util.Log.d("StreamNest",
                        "Movie backdrop unavailable: " + imageUrl, e);
            } finally {
                if (connection != null) connection.disconnect();
                if (tempFile != null) tempFile.delete();
            }
        });
    }

    private void updateGuideChannelNameCell(
            View cell, Channel channel, boolean focused) {
        if (cell == null || channel == null) return;

        boolean playing = guidePreviewChannel != null
                && guidePreviewChannel.url != null
                && guidePreviewChannel.url.equals(channel.url);

        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(focused ? BLUE : PANEL);

        if (focused) {
            background.setStroke(dp(2), Color.WHITE);
        } else if (playing) {
            background.setStroke(dp(2), BLUE);
        }
        background.setCornerRadius(dp(4));
        cell.setBackground(background);
    }

    private void refreshGuideChannelNameBorders() {
        for (View cell : guideChannelNameCells) {
            Object value = cell.getTag();
            boolean playing = guidePreviewChannel != null
                    && guidePreviewChannel.url != null
                    && value instanceof String
                    && guidePreviewChannel.url.equals(value);

            android.graphics.drawable.GradientDrawable background =
                    new android.graphics.drawable.GradientDrawable();
            background.setColor(cell.hasFocus() ? BLUE : PANEL);

            if (cell.hasFocus()) {
                background.setStroke(dp(2), Color.WHITE);
            } else if (playing) {
                background.setStroke(dp(2), BLUE);
            }
            background.setCornerRadius(dp(4));
            cell.setBackground(background);
        }
    }

    private void configureGuideNoInfoBrick(TextView brick, Channel channel) {
        android.graphics.drawable.GradientDrawable normal =
                new android.graphics.drawable.GradientDrawable();
        normal.setColor(PANEL);
        normal.setStroke(dp(1), 0xFF777777);

        android.graphics.drawable.GradientDrawable focused =
                new android.graphics.drawable.GradientDrawable();
        focused.setColor(BLUE);
        focused.setStroke(dp(1), Color.WHITE);

        brick.setBackground(normal);
        brick.setFocusable(true);
        brick.setClickable(true);
        brick.setOnFocusChangeListener((v, hasFocus) ->
                v.setBackground(hasFocus ? focused : normal));

        brick.setOnClickListener(v -> {
            if (channel == null || channel.url == null) return;

            if (guidePreviewChannel != null
                    && channel.url.equals(guidePreviewChannel.url)) {
                playChannel(channel);
            } else {
                playGuidePreview(channel);
            }
        });
    }

    private void updateGuidePreviewProgramInfo(Channel channel) {
        if (channel == null || guidePreviewProgramInfoView == null) return;

        long now = System.currentTimeMillis();
        EpgProgram current = null;
        EpgProgram next = null;
        String channelId = channel.tvgId == null
                ? "" : normalizeEpgId(channel.tvgId);

        if (!channelId.isEmpty()) {
            for (EpgProgram program : guidePrograms) {
                if (!channelId.equals(normalizeEpgId(program.channelId))) continue;
                if (program.startMillis <= now && program.stopMillis > now) {
                    current = program;
                }
                if (program.startMillis > now
                        && (next == null || program.startMillis < next.startMillis)) {
                    next = program;
                }
            }
        }

        SimpleDateFormat clock = new SimpleDateFormat("h:mm a", Locale.getDefault());
        StringBuilder info = new StringBuilder();
        info.append("Time: ").append(clock.format(new Date(now)));

        if (current != null) {
            info.append("\nNow: ").append(current.title);
            info.append("\n").append(clock.format(new Date(current.startMillis)))
                    .append(" - ").append(clock.format(new Date(current.stopMillis)));
            if (current.description != null && !current.description.trim().isEmpty()) {
                info.append("\n").append(current.description.trim());
            } else {
                info.append("\nProgramme description unavailable");
            }
            if (next != null) info.append("\nUp next: ").append(next.title);
        } else if (next != null) {
            info.append("\nNow: No programme information");
            info.append("\nUp next: ").append(next.title);
            info.append("\n").append(clock.format(new Date(next.startMillis)))
                    .append(" - ").append(clock.format(new Date(next.stopMillis)));
            if (next.description != null && !next.description.trim().isEmpty()) {
                info.append("\n").append(next.description.trim());
            }
        } else {
            info.append("\nProgramme information unavailable");
        }

        guidePreviewProgramInfoView.setText(info.toString());
    }

    private void playGuidePreview(Channel channel) {
        if (channel.url == null || channel.url.trim().isEmpty()
                || previewPlayerView == null) {
            return;
        }
        guidePreviewChannel = channel;
        getPreferences(MODE_PRIVATE).edit()
                .putString("last_channel_url", channel.url)
                .putString("last_channel_name", channel.name)
                .apply();
        refreshGuideChannelNameBorders();

        if (guidePreviewChannelNameView != null) {
            guidePreviewChannelNameView.setText(channel.name);
        }

        updateGuidePreviewProgramInfo(channel);
        if (channel.url.equals(previewChannelUrl) && previewPlayer != null) {
            return;
        }

        try {
            if (previewPlayer == null) {
                DefaultHttpDataSource.Factory httpFactory =
                        new DefaultHttpDataSource.Factory()
                                .setAllowCrossProtocolRedirects(true);
                DefaultDataSource.Factory dataSourceFactory =
                        new DefaultDataSource.Factory(this, httpFactory);
                previewPlayer = new ExoPlayer.Builder(this)
                        .setMediaSourceFactory(
                                new DefaultMediaSourceFactory(dataSourceFactory))
                        .build();
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
        if (startupCoverView != null) {
            startupPlayerLaunched = true;
        }

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("channel_name", channel.name);
        intent.putExtra("channel_logo", channel.logoUrl);
        intent.putExtra("stream_url", channel.url);
        intent.putExtra("is_live_stream", true);
        intent.putExtra("show_on_screen_clock",
                getPreferences(MODE_PRIVATE).getBoolean("show_on_screen_clock", true));
        startActivity(intent);
    }
}
