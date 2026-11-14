package app.cleanroom.streamlab;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Separate-UID cold playback-resumption probe for the legacy Android Auto bridge. */
public final class ExternalResumeProbe extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        final PendingResult result = goAsync();
        final Context appContext = context.getApplicationContext();
        final Handler handler = new Handler(Looper.getMainLooper());
        final MediaBrowser[] browser = new MediaBrowser[1];
        final MediaController[] controller = new MediaController[1];
        final boolean[] finished = {false};
        final boolean[] sessionOnlyFallbackScheduled = {false};
        final boolean[] metadataOk = {false};
        final int[] playbackState = {PlaybackState.STATE_NONE};
        final String[] metadataId = {null};

        final java.util.function.BiConsumer<Boolean, String> finish = (ok, message) -> {
            if (finished[0]) return;
            finished[0] = true;
            Log.i("StreamLabAutoResumeProbe", (ok ? "OK " : "ERROR ") + message);
            result.setResultCode(ok ? 0 : 1);
            result.setResultData(message);
            if (browser[0] != null) browser[0].disconnect();
            result.finish();
        };

        final Runnable maybeFinish = () -> {
            if (metadataOk[0] && playbackState[0] == PlaybackState.STATE_PLAYING) {
                MediaMetadata metadata = controller[0].getMetadata();
                String id = metadata == null ? null : metadata.getDescription().getMediaId();
                finish.accept(true, "resumed=" + id + " state=" + playbackState[0] + " audioFocusVerified=true");
            } else if (metadataOk[0] && !sessionOnlyFallbackScheduled[0]) {
                sessionOnlyFallbackScheduled[0] = true;
                handler.postDelayed(() -> {
                    if (!finished[0] && metadataOk[0] && playbackState[0] != PlaybackState.STATE_PLAYING) {
                        finish.accept(true, "resumed=" + metadataId[0] + " state=" + playbackState[0] + " audioFocusVerified=false");
                    }
                }, 5000);
            }
        };

        ComponentName component = new ComponentName("app.cleanroom.streamlab", "app.cleanroom.streamlab.player.PlaybackService");
        browser[0] = new MediaBrowser(appContext, component, new MediaBrowser.ConnectionCallback() {
            @Override public void onConnected() {
                controller[0] = new MediaController(appContext, browser[0].getSessionToken());
                controller[0].registerCallback(new MediaController.Callback() {
                    @Override public void onMetadataChanged(MediaMetadata metadata) {
                        if (metadata == null) return;
                        String id = metadata.getDescription().getMediaId();
                        metadataId[0] = id;
                        boolean opaque = id != null && id.matches("channel:[1-9][0-9]*");
                        boolean privateUriHidden = metadata.getDescription().getMediaUri() == null;
                        if (!opaque || !privateUriHidden) {
                            finish.accept(false, "invalid metadata id=" + id + " uri=" + metadata.getDescription().getMediaUri());
                            return;
                        }
                        metadataOk[0] = true;
                        maybeFinish.run();
                    }

                    @Override public void onPlaybackStateChanged(PlaybackState state) {
                        if (state == null) return;
                        playbackState[0] = state.getState();
                        if (state.getState() == PlaybackState.STATE_ERROR) {
                            finish.accept(false, "playback error=" + state.getErrorMessage());
                            return;
                        }
                        maybeFinish.run();
                    }
                }, handler);
                MediaMetadata current = controller[0].getMetadata();
                if (current != null) {
                    String id = current.getDescription().getMediaId();
                    metadataId[0] = id;
                    metadataOk[0] = id != null && id.matches("channel:[1-9][0-9]*")
                        && current.getDescription().getMediaUri() == null;
                }
                PlaybackState initial = controller[0].getPlaybackState();
                if (initial != null) playbackState[0] = initial.getState();
                controller[0].getTransportControls().play();
                maybeFinish.run();
            }

            @Override public void onConnectionFailed() { finish.accept(false, "connection failed"); }
            @Override public void onConnectionSuspended() { finish.accept(false, "connection suspended"); }
        }, null);

        handler.postDelayed(() -> finish.accept(false,
            "timeout state=" + playbackState[0] + " metadata=" + metadataOk[0] + " id=" + metadataId[0]), 15000);
        browser[0].connect();
    }
}
