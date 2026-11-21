package com.streamnest.iptv;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {

    private static final String TAG = "StreamNest";
    private ExoPlayer player;
    private PlayerView playerView;
    private String streamUrl;
    private String channelName;
    private String channelLogo;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private String activeProgressKey = "";
    private static final long PROGRESS_SAVE_INTERVAL_MS = 5000;
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            saveCurrentVodProgress();
            progressHandler.postDelayed(this, PROGRESS_SAVE_INTERVAL_MS);
        }
    };
    private FrameLayout rootLayout;
    private View recentPanel;
    private boolean recentPanelOpen = false;
    private TextView clockView;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockRunnable = new Runnable() {
        @Override public void run() {
            if (clockView == null) return;
            clockView.setText(new SimpleDateFormat("h:mm a",
                    Locale.getDefault()).format(new Date()));
            clockHandler.postDelayed(this, 1000);
        }
    };
    private int selectedRecentIndex = 0;
    private final ArrayList<RecentChannel> recentChannels = new ArrayList<>();
    private final ThreadPoolExecutor recentLogoExecutor = new ThreadPoolExecutor(2, 2, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20), new ThreadPoolExecutor.DiscardOldestPolicy());
    private final Handler recentHandler = new Handler(Looper.getMainLooper());
    private boolean rightLongPressTriggered = false;
    private static final long RECENT_LONG_PRESS_MS = 500;
    private final Runnable openRecentRunnable = () -> {
        rightLongPressTriggered = true;
        if (getIntent().getBooleanExtra("is_live_stream", true)) openRecentPanel();
    };
    private static final int RECENT_LIMIT = 20;
    private static final int NAVY = Color.rgb(15, 20, 35);
    private static final int PANEL = Color.rgb(27, 36, 54);
    private static final int BLUE = Color.rgb(25, 115, 190);
    private static final int LIGHT_BLUE = Color.rgb(95, 190, 255);

    private static class RecentChannel {
        String name;
        String url;
        String logo;

        RecentChannel(String name, String url, String logo) {
            this.name = name;
            this.url = url;
            this.logo = logo;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        streamUrl = getIntent().getStringExtra("stream_url");
        channelName = getIntent().getStringExtra("channel_name");
        channelLogo = getIntent().getStringExtra("channel_logo");

        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            Toast.makeText(this, "Missing channel stream URL",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.i(TAG, "Opening channel: " + channelName);
        Log.i(TAG, "Stream URL scheme: " + android.net.Uri.parse(streamUrl).getScheme());

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setFocusable(true);

        rootLayout.addView(playerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        clockView = new TextView(this);
        clockView.setTextColor(Color.WHITE);
        clockView.setTextSize(22);
        clockView.setGravity(Gravity.CENTER);
        clockView.setBackgroundColor(Color.TRANSPARENT);
        clockView.setPadding(dp(8), dp(4), dp(8), dp(4));
        clockView.setClickable(false);
        clockView.setFocusable(false);
        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        clockParams.setMargins(0, dp(18), dp(24), 0);
        clockView.setVisibility(View.GONE);
        rootLayout.addView(clockView, clockParams);

        setContentView(rootLayout);
    }

    private void recordRecentChannel() {
        if (streamUrl == null || streamUrl.trim().isEmpty()) return;
        SharedPreferences prefs = getSharedPreferences("streamnest_recent", MODE_PRIVATE);
        JSONArray saved = new JSONArray();
        try { saved = new JSONArray(prefs.getString("channels", "[]")); } catch (Exception ignored) {}
        JSONArray updated = new JSONArray();
        try {
            updated.put(new JSONObject().put("name", channelName == null ? "Channel" : channelName).put("url", streamUrl).put("logo", channelLogo == null ? "" : channelLogo));
            for (int i = 0; i < saved.length() && updated.length() < RECENT_LIMIT; i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null || streamUrl.equals(item.optString("url"))) continue;
                updated.put(item);
            }
        } catch (Exception e) { Log.e(TAG, "Could not save recent channels", e); }
        prefs.edit().putString("channels", updated.toString()).apply();
    }

    private void loadRecentChannels() {
        recentChannels.clear();
        SharedPreferences prefs = getSharedPreferences("streamnest_recent", MODE_PRIVATE);
        try {
            JSONArray saved = new JSONArray(prefs.getString("channels", "[]"));
            for (int i = 0; i < saved.length() && i < RECENT_LIMIT; i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) continue;
                String url = item.optString("url", "");
                if (url.isEmpty()) continue;
                recentChannels.add(new RecentChannel(item.optString("name", "Channel"), url, item.optString("logo", "")));
            }
        } catch (Exception e) { Log.e(TAG, "Could not load recent channels", e); }
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable recentRowBackground(boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(selected ? 0xFF1769C2 : PANEL);
        d.setCornerRadius(dp(6));
        if (selected) d.setStroke(dp(2), 0xFF55A5FF);
        return d;
    }

    private GradientDrawable panelBackground(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(6));
        return d;
    }

    private void loadRecentLogo(ImageView image, String logoUrl) {
        if (logoUrl == null || logoUrl.trim().isEmpty()) return;
        image.setTag(logoUrl);
        recentLogoExecutor.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(logoUrl).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "StreamNest IPTV");
                try (InputStream input = connection.getInputStream()) {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(input, null, bounds);
                    int sample = 1;
                    while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2;
                    connection.disconnect();
                    connection = (HttpURLConnection) new URL(logoUrl).openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    try (InputStream imageInput = connection.getInputStream()) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = sample;
                        bitmap = BitmapFactory.decodeStream(imageInput, null, options);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Recent channel logo could not be loaded", e);
            } finally {
                if (connection != null) connection.disconnect();
            }
            Bitmap result = bitmap;
            if (result != null) image.post(() -> {
                if (logoUrl.equals(image.getTag())) image.setImageBitmap(result);
                else result.recycle();
            });
        });
    }

    private void addRecentRow(LinearLayout list, int index) {
        RecentChannel channel = recentChannels.get(index);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(5), dp(8), dp(5));
        row.setBackground(recentRowBackground(index == selectedRecentIndex));

        ImageView logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        imageLp.rightMargin = dp(12);
        row.addView(logo, imageLp);
        loadRecentLogo(logo, channel.logo);

        TextView name = new TextView(this);
        name.setText(channel.name == null ? "Channel" : channel.name);
        name.setTextColor(Color.WHITE);
        name.setTextSize(16);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.bottomMargin = dp(5);
        list.addView(row, lp);
    }

    private void refreshRecentSelection() {
        if (!(recentPanel instanceof LinearLayout)) return;
        LinearLayout panel = (LinearLayout) recentPanel;
        if (panel.getChildCount() < 2) return;
        ScrollView scroll = (ScrollView) panel.getChildAt(1);
        LinearLayout list = (LinearLayout) scroll.getChildAt(0);
        for (int i = 0; i < list.getChildCount(); i++) {
            list.getChildAt(i).setBackground(panelBackground(i == selectedRecentIndex ? BLUE : PANEL));
        }
        if (selectedRecentIndex >= 0 && selectedRecentIndex < list.getChildCount()) {
            View selected = list.getChildAt(selectedRecentIndex);
            scroll.post(() -> scroll.smoothScrollTo(0, selected.getTop()));
        }
    }

    private void closeRecentPanel() {
        if (!recentPanelOpen) return;
        if (recentPanel != null) rootLayout.removeView(recentPanel);
        recentPanel = null;
        recentPanelOpen = false;
        if (clockView != null
                && getIntent().getBooleanExtra("show_on_screen_clock", true)) {
            clockView.setVisibility(View.VISIBLE);
        }
        rightLongPressTriggered = false;
        if (playerView != null) playerView.requestFocus();
    }

    private void tuneRecentChannel() {
        if (selectedRecentIndex < 0 || selectedRecentIndex >= recentChannels.size()) return;
        RecentChannel channel = recentChannels.get(selectedRecentIndex);
        closeRecentPanel();
        streamUrl = channel.url;
        channelName = channel.name;
        channelLogo = channel.logo;
        getIntent().putExtra("stream_url", streamUrl);
        getIntent().putExtra("channel_name", channelName);
        getIntent().putExtra("channel_logo", channelLogo);
        if (player != null) {
            player.setMediaItem(MediaItem.fromUri(streamUrl));
            player.prepare();
            player.play();
        }
        recordRecentChannel();
    }

    private void openRecentPanel() {
        if (recentPanelOpen || rootLayout == null) return;
        loadRecentChannels();
        if (recentChannels.isEmpty()) return;
        recentPanelOpen = true;
        if (clockView != null) clockView.setVisibility(View.GONE);
        selectedRecentIndex = 0;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackground(panelBackground(NAVY));
        TextView title = new TextView(this);
        title.setText("RECENT CHANNELS");
        title.setTextColor(LIGHT_BLUE);
        title.setTextSize(18);
        panel.addView(title);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < recentChannels.size(); i++) addRecentRow(list, i);
        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        int w = Math.min(dp(340), getResources().getDisplayMetrics().widthPixels * 32 / 100);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, -1, Gravity.END);
        lp.setMargins(0, dp(24), dp(24), dp(24));
        rootLayout.addView(panel, lp);
        recentPanel = panel;
        refreshRecentSelection();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (streamUrl == null || playerView == null) {
            return;
        }

        if (getIntent().getBooleanExtra("show_on_screen_clock", true)) {
            clockView.setVisibility(View.VISIBLE);
            clockHandler.removeCallbacks(clockRunnable);
            clockRunnable.run();
        } else {
            clockView.setVisibility(View.GONE);
            clockHandler.removeCallbacks(clockRunnable);
        }

        try {
            boolean isLiveStream = getIntent().getBooleanExtra("is_live_stream", true);
            if (isLiveStream) recordRecentChannel();
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            isLiveStream ? 10000 : 20000,
                            isLiveStream ? 30000 : 60000,
                            isLiveStream ? 1500 : 2500,
                            isLiveStream ? 4000 : 8000)
                    .build();
            player = new ExoPlayer.Builder(this)
                    .setLoadControl(loadControl)
                    .build();
            playerView.setPlayer(player);

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        restoreCurrentVodProgress();
                    }
                }

                @Override
                public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                    activeProgressKey = "";
                    restoreCurrentVodProgress();
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    Log.e(TAG, "Playback error: " + error.getErrorCodeName()
                            + ", code=" + error.errorCode, error);
                    String failureDetails = "";
                    Throwable cause = error;
                    while (cause != null) {
                        if (cause instanceof HttpDataSource.InvalidResponseCodeException) {
                            HttpDataSource.InvalidResponseCodeException httpError =
                                    (HttpDataSource.InvalidResponseCodeException) cause;
                            failureDetails = "HTTP " + httpError.responseCode
                                    + ": " + httpError.responseMessage;
                            break;
                        }
                        cause = cause.getCause();
                    }
                    final String message = "Playback failed: "
                            + error.getErrorCodeName()
                            + (failureDetails.isEmpty() ? "" : "\n" + failureDetails);
                    runOnUiThread(() -> Toast.makeText(
                            PlayerActivity.this, message,
                            Toast.LENGTH_LONG).show());
                }
            });

            boolean autoPlayNext = getIntent().getBooleanExtra(
                    "auto_play_next_episode", false);
            ArrayList<String> episodeUrls =
                    getIntent().getStringArrayListExtra("series_episode_urls");
            ArrayList<String> episodeNames =
                    getIntent().getStringArrayListExtra("series_episode_names");

            if (autoPlayNext && episodeUrls != null && !episodeUrls.isEmpty()) {
                ArrayList<MediaItem> playlist = new ArrayList<>();
                for (int i = 0; i < episodeUrls.size(); i++) {
                    String url = episodeUrls.get(i);
                    if (url == null || url.trim().isEmpty()) continue;

                    String title = episodeNames != null && i < episodeNames.size()
                            ? episodeNames.get(i) : "Episode";

                    MediaMetadata metadata = new MediaMetadata.Builder()
                            .setTitle(title)
                            .build();

                    playlist.add(new MediaItem.Builder()
                            .setUri(url)
                            .setMediaMetadata(metadata)
                            .build());
                }

                if (!playlist.isEmpty()) {
                    player.setMediaItems(playlist);
                } else {
                    player.setMediaItem(MediaItem.fromUri(streamUrl));
                }
            } else {
                player.setMediaItem(MediaItem.fromUri(streamUrl));
            }

            player.prepare();
            player.play();
            if (!isLiveStream) {
                progressHandler.removeCallbacks(progressRunnable);
                progressHandler.postDelayed(progressRunnable,
                        PROGRESS_SAVE_INTERVAL_MS);
            }
            playerView.requestFocus();

        } catch (Exception e) {
            Log.e(TAG, "Player initialization failed", e);
            Toast.makeText(this, "Player error: " + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private String getProgressKey(int index) {
        String playlist = getIntent().getStringExtra("active_playlist_id");
        if (playlist == null || playlist.isEmpty()) playlist = "default";
        ArrayList<String> ids = getIntent().getStringArrayListExtra("series_episode_ids");
        String itemId = ids != null && index >= 0 && index < ids.size()
                ? ids.get(index) : getIntent().getStringExtra("vod_id");
        String seriesId = getIntent().getStringExtra("series_id");
        if (itemId == null || itemId.isEmpty()) {
            ArrayList<String> urls = getIntent().getStringArrayListExtra("series_episode_urls");
            String url = urls != null && index >= 0 && index < urls.size()
                    ? urls.get(index) : streamUrl;
            itemId = String.valueOf(url == null ? 0 : url.hashCode());
        }
        return playlist + ":" + (seriesId == null ? "" : seriesId + ":") + itemId;
    }

    private JSONObject getProgressStore() {
        try {
            String saved = getSharedPreferences("vod_progress", MODE_PRIVATE)
                    .getString("items", "{}");
            return new JSONObject(saved);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void saveCurrentVodProgress() {
        if (player == null || getIntent().getBooleanExtra("is_live_stream", true)) return;
        int index = player.getCurrentMediaItemIndex();
        String key = getProgressKey(index);
        long position = player.getCurrentPosition();
        long duration = player.getDuration();
        if (key.isEmpty() || position < 10000 || duration <= 0) return;

        JSONObject store = getProgressStore();
        if (position >= duration * 0.90) {
            store.remove(key);
        } else {
            try {
                JSONObject item = new JSONObject();
                item.put("position", position);
                item.put("duration", duration);
                item.put("title", player.getCurrentMediaItem() != null
                        && player.getCurrentMediaItem().mediaMetadata.title != null
                        ? player.getCurrentMediaItem().mediaMetadata.title.toString()
                        : channelName);
                ArrayList<String> artwork = getIntent().getStringArrayListExtra(
                        "series_episode_artwork");
                String image = artwork != null && index >= 0 && index < artwork.size()
                        ? artwork.get(index) : getIntent().getStringExtra("vod_artwork");
                item.put("artwork", image == null ? "" : image);
                item.put("updated", System.currentTimeMillis());
                boolean isEpisode = getIntent().hasExtra("series_id");
                item.put("type", isEpisode ? "episode" : "movie");
                if (isEpisode) {
                    ArrayList<String> ids = getIntent().getStringArrayListExtra(
                            "series_episode_ids");
                    String episodeId = ids != null && index >= 0 && index < ids.size()
                            ? ids.get(index) : "";
                    item.put("episode_id", episodeId);
                    item.put("series_id", getIntent().getStringExtra("series_id"));
                    item.put("series_name", getIntent().getStringExtra("series_name"));
                    item.put("series_artwork", getIntent().getStringExtra("series_artwork"));
                } else {
                    item.put("vod_id", getIntent().getStringExtra("vod_id"));
                    item.put("extension", getIntent().getStringExtra("vod_extension"));
                }
                item.put("playlist_id", getIntent().getStringExtra("active_playlist_id"));
                store.put(key, item);
            } catch (Exception e) {
                Log.e(TAG, "Could not save VOD progress", e);
                return;
            }
        }
        getSharedPreferences("vod_progress", MODE_PRIVATE).edit()
                .putString("items", store.toString()).apply();
    }

    private void restoreCurrentVodProgress() {
        if (player == null || player.getPlaybackState() != Player.STATE_READY
                || getIntent().getBooleanExtra("is_live_stream", true)) return;
        int index = player.getCurrentMediaItemIndex();
        String key = getProgressKey(index);
        if (key.equals(activeProgressKey)) return;
        activeProgressKey = key;
        JSONObject item = getProgressStore().optJSONObject(key);
        if (item == null) return;
        long position = item.optLong("position", 0);
        if (position > 0) {
            player.seekTo(position);
            Toast.makeText(this, "Resuming " + item.optString("title", channelName),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (recentPanelOpen) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (key == KeyEvent.KEYCODE_DPAD_UP) {
                    selectedRecentIndex = Math.max(0, selectedRecentIndex - 1);
                    refreshRecentSelection();
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    selectedRecentIndex = Math.min(recentChannels.size() - 1, selectedRecentIndex + 1);
                    refreshRecentSelection();
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                    tuneRecentChannel();
                    return true;
                }
                if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE || key == KeyEvent.KEYCODE_DPAD_LEFT) {
                    closeRecentPanel();
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP && (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT)) return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_RIGHT && getIntent().getBooleanExtra("is_live_stream", true)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0 && !rightLongPressTriggered) {
                    recentHandler.postDelayed(openRecentRunnable, RECENT_LONG_PRESS_MS);
                }
                if (rightLongPressTriggered) return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                recentHandler.removeCallbacks(openRecentRunnable);
                boolean handled = rightLongPressTriggered;
                rightLongPressTriggered = false;
                if (handled) return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onStop() {
        clockHandler.removeCallbacks(clockRunnable);
        progressHandler.removeCallbacks(progressRunnable);
        saveCurrentVodProgress();
        if (clockView != null) clockView.setVisibility(View.GONE);
        if (playerView != null) {
            playerView.setPlayer(null);
        }

        if (player != null) {
            player.release();
            player = null;
        }

        super.onStop();
    }
}
