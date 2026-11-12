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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashSet;
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

    private LinearLayout screen;
    private LinearLayout categoryPanel;
    private TextView phoneCategoryButton;
    private LinearLayout channelPanel;
    private ArrayList<Channel> channels = new ArrayList<>();
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
    private ScrollView tvSeriesMainMenuScroll;
    private LinearLayout tvSeriesCategoryList;
    private LinearLayout tvSeriesGrid;
    private TextView tvSeriesStatus;
    private String tvSeriesSelectedCategoryId = "";
    private String tvSeriesSelectedCategoryName = "";

    private ExoPlayer previewPlayer;
    private PlayerView previewPlayerView;
    private String previewChannelUrl;
    private Channel guidePreviewChannel;
    private final ArrayList<View> guideChannelNameCells = new ArrayList<>();
    private static class Channel {
        String name;
        String url;
        String group;
        String tvgId;
        String logoUrl;
        String vodId = "";
        String vodExtension = "mp4";
        String episodeArtwork = "";
        String episodeDescription = "";
        String episodeNumber = "";

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

        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(NAVY);
        setContentView(screen);

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
                boolean launchOnBoot =
                        prefs.getBoolean("launch_live_tv_on_boot", true);
                startupGuidePending = launchOnBoot;
                startupPreviewSuppressed = launchOnBoot;
                loadXtreamChannels(server, username, password, launchOnBoot);
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
                    focusFirstTvSeriesPoster();
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
                        focusFirstTvVodPoster();
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
                focusFirstTvVodPoster();
            } else if (tvVodCategoryScroll != null) {
                tvVodCategoryScroll.setVisibility(View.VISIBLE);
                focusTvVodSelectedCategory();
            }
            return;
        } else if (showingTvSeriesEpisodes) {
            showingTvSeriesEpisodes = false;
            showSeries();
            return;
        } else if (showingTvSeries) {
            if (tvSeriesCategoryScroll != null
                    && tvSeriesCategoryScroll.getVisibility() == View.VISIBLE) {
                tvSeriesCategoryScroll.setVisibility(View.GONE);
                focusFirstTvSeriesPoster();
            } else if (tvSeriesCategoryScroll != null) {
                tvSeriesCategoryScroll.setVisibility(View.VISIBLE);
                focusTvSeriesSelectedCategory();
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
        backdrop.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        hero.addView(backdrop, new android.widget.FrameLayout.LayoutParams(
                -1, -1));
        if (!tvDetails) {
            View shade = new View(this);
            shade.setBackgroundColor(0x77000000);
            hero.addView(shade,
                    new android.widget.FrameLayout.LayoutParams(-1, -1));
        }
        if (tvDetails) {
            int fadeSize = dp(100);

            View leftFade = new View(this);
            leftFade.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{NAVY, Color.TRANSPARENT}));
            android.widget.FrameLayout.LayoutParams leftParams =
                    new android.widget.FrameLayout.LayoutParams(
                            fadeSize, -1, Gravity.LEFT);
            hero.addView(leftFade, leftParams);

            View rightFade = new View(this);
            rightFade.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.TRANSPARENT, NAVY}));
            android.widget.FrameLayout.LayoutParams rightParams =
                    new android.widget.FrameLayout.LayoutParams(
                            fadeSize, -1, Gravity.RIGHT);
            hero.addView(rightFade, rightParams);

            View topFade = new View(this);
            topFade.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{NAVY, Color.TRANSPARENT}));
            android.widget.FrameLayout.LayoutParams topParams =
                    new android.widget.FrameLayout.LayoutParams(
                            -1, fadeSize, Gravity.TOP);
            hero.addView(topFade, topParams);

            View bottomFade = new View(this);
            bottomFade.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.TRANSPARENT, NAVY}));
            android.widget.FrameLayout.LayoutParams bottomParams =
                    new android.widget.FrameLayout.LayoutParams(
                            -1, fadeSize, Gravity.BOTTOM);
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

        if (tvDetails) {
            content.removeView(play);
            content.removeView(myList);
            content.removeView(back);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);

            TextView[] actionViews = {play, myList, back};
            for (TextView action : actionViews) {
                action.setGravity(Gravity.CENTER);
                action.setTextSize(14);
                action.setPadding(dp(4), dp(12), dp(4), dp(12));
                actions.addView(action,
                        new LinearLayout.LayoutParams(0, -2, 1f));
            }

            LinearLayout.LayoutParams actionsParams =
                    new LinearLayout.LayoutParams(-1, -2);
            actionsParams.setMargins(0, dp(18), 0, dp(8));
            content.addView(actions, actionsParams);
        }

        scroll.addView(content);

        if (tvDetails) {
            LinearLayout tvBody = new LinearLayout(this);
            tvBody.setOrientation(LinearLayout.HORIZONTAL);
            tvBody.setGravity(Gravity.CENTER_VERTICAL);

            tvBody.addView(scroll, new LinearLayout.LayoutParams(
                    0, -1, 0.48f));

            tvBody.addView(hero, new LinearLayout.LayoutParams(
                    0, -1, 0.52f));

            detailsScreen.addView(tvBody,
                    new LinearLayout.LayoutParams(-1, 0, 1f));
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
        startActivity(intent);
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

        ScrollView gridScroll = new ScrollView(this);
        tvSeriesGrid = new LinearLayout(this);
        tvSeriesGrid.setOrientation(LinearLayout.VERTICAL);
        tvSeriesGrid.setPadding(dp(5), dp(5), dp(5), dp(5));
        gridScroll.addView(tvSeriesGrid);

        body.addView(gridScroll, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

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
                if (error != null) {
                    tvSeriesStatus.setText("Unable to load categories: " + error);
                    return;
                }
                if (categories.isEmpty()) {
                    tvSeriesStatus.setText("No TV series categories were returned.");
                    return;
                }

                for (String[] category : categories) {
                    tvSeriesCategoryList.addView(createTvSeriesCategoryItem(
                            category[0], category[1], base, user, pass));
                }

                String[] selected = categories.get(0);
                for (String[] category : categories) {
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
        tvSeriesSelectedCategoryId = categoryId;
        tvSeriesSelectedCategoryName = categoryName;
        tvSeriesStatus.setText("Loading " + categoryName + "...");
        tvSeriesGrid.removeAllViews();

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
                int columns = Math.max(3, Math.min(7, width / 170));
                LinearLayout gridRow = null;
                int column = 0;

                for (Channel series : loaded) {
                    if (column == 0) {
                        gridRow = new LinearLayout(this);
                        gridRow.setOrientation(LinearLayout.HORIZONTAL);
                        gridRow.setGravity(Gravity.TOP);
                        tvSeriesGrid.addView(gridRow,
                                new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT));
                    }

                    LinearLayout card = createSeriesPosterCard(
                            series, base, user, pass);
                    LinearLayout.LayoutParams cardParams =
                            new LinearLayout.LayoutParams(
                                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    cardParams.setMargins(dp(4), dp(5), dp(4), dp(8));
                    gridRow.addView(card, cardParams);

                    column++;
                    if (column >= columns) column = 0;
                }
                focusFirstTvSeriesPoster();
            });
        }).start();
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
        poster.setBackgroundColor(Color.rgb(35, 45, 62));

        if (series.logoUrl != null && !series.logoUrl.isEmpty()) {
            loadChannelLogo(poster, series.logoUrl);
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

        card.setOnClickListener(v ->
                showSeriesEpisodes(series, base, user, pass));

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
        showingTvSeriesEpisodes = isTelevisionDevice();
        showingTvSeries = false;
        showingLiveTv = false;
        showingTvGuide = false;

        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(NAVY);
        screen.setPadding(dp(12), dp(8), dp(12), dp(8));

        boolean tvLayout = isTelevisionDevice();
        TextView seriesPlot = label("Loading description...", 16);
        seriesPlot.setTextColor(Color.WHITE);
        seriesPlot.setMaxLines(8);

        TextView heading = label(series.name, 22);
        heading.setTextColor(Color.rgb(95, 190, 255));
        heading.setTypeface(null, Typeface.BOLD);

        final Channel[] firstSeriesEpisode = {null};
        if (tvLayout) {
            LinearLayout heroRow = new LinearLayout(this);
            heroRow.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
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
                if (firstSeriesEpisode[0] != null) playVodItem(firstSeriesEpisode[0]);
            });

            TextView myList = label("", 16);
            myList.setTextColor(Color.WHITE);
            myList.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            myList.setFocusable(true);
            myList.setClickable(true);
            myList.setPadding(0, dp(13), 0, dp(13));
            myList.setBackgroundColor(Color.TRANSPARENT);
            Runnable updateMyListText = () -> myList.setText(
                    isSeriesSaved(series.url) ? "✓  REMOVE FROM MY LIST" : "+  ADD TO MY LIST");
            updateMyListText.run();
            myList.setOnFocusChangeListener((v, focused) ->
                    myList.setTextColor(focused ? Color.YELLOW : Color.WHITE));
            myList.setOnClickListener(v -> {
                if (isSeriesSaved(series.url)) removeSeriesFromList(series.url);
                else saveSeriesToList(series);
                updateMyListText.run();
            });

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

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            TextView[] actionViews = {play, myList, back};
            for (TextView action : actionViews) {
                action.setGravity(Gravity.CENTER);
                action.setTextSize(14);
                action.setPadding(dp(4), dp(12), dp(4), dp(12));
                actions.addView(action, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
            actionsParams.setMargins(0, dp(18), 0, dp(8));
            details.addView(actions, actionsParams);

            heroRow.addView(details, new LinearLayout.LayoutParams(
                    0, -1, 1f));

            FrameLayout artworkFrame = new FrameLayout(this);
            ImageView backdrop = new ImageView(this);
            backdrop.setScaleType(ImageView.ScaleType.FIT_CENTER);
            artworkFrame.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
            loadMovieBackdrop(backdrop, series.logoUrl);

            GradientDrawable leftFade = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{NAVY, Color.TRANSPARENT});
            View leftOverlay = new View(this);
            leftOverlay.setBackground(leftFade);
            FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(
                    dp(100), -1, Gravity.LEFT);
            artworkFrame.addView(leftOverlay, leftParams);

            GradientDrawable rightFade = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.TRANSPARENT, NAVY});
            View rightOverlay = new View(this);
            rightOverlay.setBackground(rightFade);
            artworkFrame.addView(rightOverlay, new FrameLayout.LayoutParams(
                    dp(100), -1, Gravity.RIGHT));

            GradientDrawable topFade = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{NAVY, Color.TRANSPARENT});
            View topOverlay = new View(this);
            topOverlay.setBackground(topFade);
            artworkFrame.addView(topOverlay, new FrameLayout.LayoutParams(
                    -1, dp(65), Gravity.TOP));

            GradientDrawable bottomFade = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.TRANSPARENT, NAVY});
            View bottomOverlay = new View(this);
            bottomOverlay.setBackground(bottomFade);
            artworkFrame.addView(bottomOverlay, new FrameLayout.LayoutParams(
                    -1, dp(65), Gravity.BOTTOM));

            heroRow.addView(artworkFrame, new LinearLayout.LayoutParams(
                    0, -1, 1f));
            screen.addView(heroRow, new LinearLayout.LayoutParams(
                    -1, dp(270)));
        } else {
            screen.addView(heading);
        }
        TextView status = label("Loading seasons and episodes...", 15);
        screen.addView(status);

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
            ArrayList<Channel> episodes = new ArrayList<>();
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

                String currentSeason = "";
                View firstEpisodeView = null;
                HorizontalScrollView seasonScroll = null;
                LinearLayout seasonRow = null;

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
                                dp(190), dp(270)));
                        poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        poster.setBackgroundColor(Color.rgb(35, 45, 62));
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
                            synopsis.setMaxLines(3);
                            card.addView(synopsis);
                        }

                        card.setOnFocusChangeListener((v, focused) ->
                                v.setBackgroundColor(focused ? BLUE : PANEL));
                        card.setOnClickListener(v -> playVodItem(episode));

                        LinearLayout.LayoutParams cardParams =
                                new LinearLayout.LayoutParams(
                                        dp(202), LinearLayout.LayoutParams.WRAP_CONTENT);
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
                        row.setOnClickListener(v -> playVodItem(episode));

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
        episode.episodeArtwork = artwork;
        episode.episodeDescription = description;
        episode.episodeNumber = episodeNumber;
        episodes.add(episode);
    }

    private void showHomeScreen() {
        showTvGuide();
    }

    private void showSearchDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Search channels, movies, or TV series");

        new AlertDialog.Builder(this)
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

                                    results.add(movie);
                                    types.add("MOVIE");
                                    names.add("[MOVIE] " + name);
                                    movieCount++;

                                    if (movieCount >= 100) break;
                                }
                            } catch (Exception e) {
                                errors.append("Movies: ")
                                        .append(e.getMessage()).append("\n");
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

                                new AlertDialog.Builder(this)
                                        .setTitle("Search Results")
                                        .setMessage(message)
                                        .setPositiveButton("OK", null)
                                        .show();
                                return;
                            }

                            new AlertDialog.Builder(this)
                                    .setTitle("Search Results (" + names.size() + ")")
                                    .setItems(names.toArray(new String[0]),
                                            (resultDialog, index) -> {
                                                Channel selected = results.get(index);
                                                String type = types.get(index);

                                                if ("LIVE TV".equals(type)) {
                                                    showLiveTv();
                                                    playChannel(selected);
                                                } else if ("MOVIE".equals(type)) {
                                                    playVodItem(selected);
                                                } else {
                                                    showSeriesEpisodes(
                                                            selected, base, user, pass);
                                                }
                                            })
                                    .setNegativeButton("Close", null)
                                    .show();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
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

    private void addGuideMenuItem(LinearLayout menu, String title) {
        TextView item = label(title, 16);
        item.setFocusable(true);
        item.setClickable(true);
        item.setOnFocusChangeListener((v, focused) ->
                item.setTextColor(focused ? BLUE : WHITE));
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
        showingTvVod = false;
        showingTvGuide = true;
        String epgUrl = getPreferences(MODE_PRIVATE).getString("epg_url", "");
        if (epgUrl.trim().isEmpty()) {
            startupGuidePending = false;
            startupPreviewSuppressed = false;
            Toast.makeText(this, "Open Settings to enter your XMLTV EPG URL.",
                    Toast.LENGTH_LONG).show();
            return;
        }
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

        LinearLayout guideContent = new LinearLayout(this);
        guideContent.setOrientation(LinearLayout.HORIZONTAL);

        guideMainMenuScroll = null;
        {
            guideMainMenuScroll = new ScrollView(this);
            guideMainMenuScroll.setVisibility(View.GONE);

            LinearLayout guideMainMenu = new LinearLayout(this);
            guideMainMenu.setOrientation(LinearLayout.VERTICAL);
            guideMainMenu.setPadding(dp(5), dp(5), dp(5), dp(5));
            addGuideMenuItem(guideMainMenu, "SEARCH");
            addGuideMenuItem(guideMainMenu, "TV GUIDE");
            addGuideMenuItem(guideMainMenu, "MOVIES");
            addGuideMenuItem(guideMainMenu, "TV SERIES");
            addGuideMenuItem(guideMainMenu, "CATCH-UP");
            addGuideMenuItem(guideMainMenu, "SETTINGS");

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

        for (String category : categories) {
            Integer savedCount = categoryCounts.get(category);
            int channelCount = savedCount == null ? 0 : savedCount;
            TextView item = label(category + " (" + channelCount + ")", 15);
            item.setPadding(dp(10), dp(14), dp(8), dp(14));
            item.setFocusable(true);
            item.setClickable(true);
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
            if (!category.equals(cat)) continue;
            rowChannels.add(channel);
            byChannel.put(channel, new ArrayList<>());
        }
        Collections.sort(rowChannels, (x, y) ->
                (x.name == null ? "" : x.name).compareTo(y.name == null ? "" : y.name));
        for (EpgProgram program : categoryPrograms) {
            ArrayList<Channel> matches = channelMap.get(normalizeEpgId(program.channelId));
            if (matches == null) continue;
            for (Channel channel : matches) {
                if (byChannel.containsKey(channel)) byChannel.get(channel).add(program);
            }
        }

        Channel firstPreviewChannel = null;
        String preferredChannelUrl = getPreferences(MODE_PRIVATE)
                .getString("last_channel_url", "");
        View lastWatchedRow = null;

        for (Channel rowChannel : rowChannels) {
            ArrayList<EpgProgram> programs = byChannel.get(rowChannel);
            Collections.sort(programs, Comparator.comparingLong(
                    program -> program.startMillis));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setDividerDrawable(
                    new android.graphics.drawable.ColorDrawable(0xFF777777));
            row.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);

            String rowName = rowChannel.name == null || rowChannel.name.trim().isEmpty() ? "Unnamed channel" : rowChannel.name;
        if (firstPreviewChannel == null) firstPreviewChannel = rowChannel;
        if (rowChannel.url != null
                && rowChannel.url.equals(preferredChannelUrl)) {
            lastWatchedRow = row;
        }
            LinearLayout channelCell = new LinearLayout(this);
            channelCell.setOrientation(LinearLayout.HORIZONTAL);
            channelCell.setGravity(android.view.Gravity.CENTER_VERTICAL);
            channelCell.setPadding(dp(5), dp(3), dp(5), dp(3));
            channelCell.setFocusable(true);
            channelCell.setClickable(true);
            channelCell.setTag(rowChannel.url);
            channelCell.setOnFocusChangeListener((v, focused) ->
                    updateGuideChannelNameCell(v, rowChannel, focused));
            guideChannelNameCells.add(channelCell);
            updateGuideChannelNameCell(channelCell, rowChannel, false);
            channelCell.setOnClickListener(v -> playGuidePreview(rowChannel));

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
        rowVerticalScroll.setFillViewport(false);
        rowVerticalScroll.addView(horizontalScroll);

        android.widget.FrameLayout programRowsFrame =
                new android.widget.FrameLayout(this);
        programRowsFrame.addView(rowVerticalScroll,
                new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        android.view.View currentTimeMarker = new android.view.View(this);
        currentTimeMarker.setBackgroundColor(Color.RED);
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
        final long twoHoursMillis = 2L * 60L * 60L * 1000L;
        final Runnable[] timelineScroller = new Runnable[1];
        timelineScroller[0] = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                channelHeader.setText(dateTimeLabel.format(new Date(now)));

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
        });

        final View targetLastWatchedRow = lastWatchedRow;
        if (!isPhoneLayout() && targetLastWatchedRow != null) {
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
    }

    private void loadChannelLogo(
            android.widget.ImageView imageView, String logoUrl) {
        loadChannelLogo(imageView, logoUrl, 300);
    }

    private void loadChannelLogo(
            android.widget.ImageView imageView, String logoUrl,
            int maxDimension) {
        imageView.setTag(logoUrl);

        logoExecutor.execute(() -> {
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
                        totalBytes += count;
                        if (totalBytes > 10 * 1024 * 1024) {
                            throw new java.io.IOException(
                                    "Artwork exceeds 10 MB");
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
        if (startupCoverView != null) {
            startupPlayerLaunched = true;
        }

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("channel_name", channel.name);
        intent.putExtra("stream_url", channel.url);
        startActivity(intent);
    }
}
