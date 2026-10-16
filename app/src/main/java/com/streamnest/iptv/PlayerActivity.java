package com.streamnest.iptv;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.graphics.Color;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
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
            player = new ExoPlayer.Builder(this).build();
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

            player.setMediaItem(MediaItem.fromUri(streamUrl));
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
