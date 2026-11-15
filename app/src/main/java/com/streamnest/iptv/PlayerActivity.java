package com.streamnest.iptv;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.graphics.Color;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import java.util.ArrayList;
import androidx.media3.common.PlaybackException;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        streamUrl = getIntent().getStringExtra("stream_url");
        channelName = getIntent().getStringExtra("channel_name");

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

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setFocusable(true);

        root.addView(playerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (streamUrl == null || playerView == null) {
            return;
        }

        try {
            boolean isLiveStream = getIntent().getBooleanExtra("is_live_stream", true);
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
                public void onPlayerError(PlaybackException error) {
                    Log.e(TAG, "Playback error: " + error.getErrorCodeName()
                            + ", code=" + error.errorCode, error);

                    runOnUiThread(() -> Toast.makeText(
                            PlayerActivity.this,
                            "Playback failed: " + error.getErrorCodeName(),
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
            playerView.requestFocus();

        } catch (Exception e) {
            Log.e(TAG, "Player initialization failed", e);
            Toast.makeText(this, "Player error: " + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStop() {
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
