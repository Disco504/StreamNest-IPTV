package com.streamnest.iptv;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {

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

        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.BLACK);

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

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                String message = "Playback failed: "
                        + error.getErrorCodeName();
                Toast.makeText(PlayerActivity.this, message,
                        Toast.LENGTH_LONG).show();
                android.util.Log.e("StreamNest", message, error);
            }
        });

        player.setMediaItem(MediaItem.fromUri(streamUrl));
        player.prepare();
        player.play();
        playerView.requestFocus();
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
