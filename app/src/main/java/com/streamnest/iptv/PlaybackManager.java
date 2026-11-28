package com.streamnest.iptv;

import android.content.Context;

import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

public final class PlaybackManager {
    private static PlaybackManager instance;

    private final Context appContext;
    private ExoPlayer player;
    private PlayerView attachedView;

    private PlaybackManager(Context context) {
        appContext = context.getApplicationContext();
    }

    public static synchronized PlaybackManager get(Context context) {
        if (instance == null) {
            instance = new PlaybackManager(context);
        }
        return instance;
    }

    public synchronized ExoPlayer getOrCreatePlayer(boolean isLiveStream) {
        if (player == null) {
            DefaultLoadControl loadControl =
                    new DefaultLoadControl.Builder()
                            .setBufferDurationsMs(
                                    isLiveStream ? 10000 : 20000,
                                    isLiveStream ? 30000 : 60000,
                                    isLiveStream ? 1500 : 2500,
                                    isLiveStream ? 4000 : 8000)
                            .build();

            DefaultHttpDataSource.Factory httpFactory =
                    new DefaultHttpDataSource.Factory()
                            .setAllowCrossProtocolRedirects(true);

            DefaultDataSource.Factory dataSourceFactory =
                    new DefaultDataSource.Factory(appContext, httpFactory);

            player = new ExoPlayer.Builder(appContext)
                    .setMediaSourceFactory(
                            new DefaultMediaSourceFactory(dataSourceFactory))
                    .setLoadControl(loadControl)
                    .build();
        }
        return player;
    }

    public synchronized ExoPlayer getPlayer() {
        return player;
    }

    public synchronized void attach(PlayerView view) {
        if (view == null || player == null) return;

        if (attachedView != null && attachedView != view) {
            attachedView.setPlayer(null);
        }

        view.setPlayer(player);
        attachedView = view;
    }

    public synchronized void detach(PlayerView view) {
        if (view == null) return;

        view.setPlayer(null);

        if (attachedView == view) {
            attachedView = null;
        }
    }

    public synchronized void release() {
        if (attachedView != null) {
            attachedView.setPlayer(null);
            attachedView = null;
        }

        if (player != null) {
            player.release();
            player = null;
        }
    }
}
