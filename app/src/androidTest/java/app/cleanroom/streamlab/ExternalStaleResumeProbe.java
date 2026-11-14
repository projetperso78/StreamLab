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
import java.util.List;

/** Separate-UID probe for an Android Auto host replaying a stale cached opaque media ID. */
public final class ExternalStaleResumeProbe extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        final PendingResult result = goAsync();
        final Context appContext = context.getApplicationContext();
        final Handler handler = new Handler(Looper.getMainLooper());
        final MediaBrowser[] browser = new MediaBrowser[1];
        final MediaController[] controller = new MediaController[1];
        final boolean[] finished = {false};
        final boolean[] sessionOnlyFallbackScheduled = {false};
        final boolean[] metadataOk = {false};
        final int[] stateCode = {PlaybackState.STATE_NONE};
        final String[] metadataId = {null};
        final String[] expectedId = {null};
        final String staleId = intent.getStringExtra("stale_media_id") == null
            ? "channel:9223372036854775807"
            : intent.getStringExtra("stale_media_id");

        final java.util.function.BiConsumer<Boolean, String> finish = (ok, message) -> {
            if (finished[0]) return;
            finished[0] = true;
            Log.i("StreamLabAutoStaleProbe", (ok ? "OK " : "ERROR ") + message);
            result.setResultCode(ok ? 0 : 1);
            result.setResultData(message);
            if (browser[0] != null) browser[0].disconnect();
            result.finish();
        };

        final Runnable maybeFinish = () -> {
            if (metadataOk[0] && stateCode[0] == PlaybackState.STATE_PLAYING) {
                finish.accept(true, "stale=" + staleId + " resumed=" + expectedId[0] + " state=" + stateCode[0] + " audioFocusVerified=true");
            } else if (metadataOk[0] && !sessionOnlyFallbackScheduled[0]) {
                sessionOnlyFallbackScheduled[0] = true;
                handler.postDelayed(() -> {
                    if (!finished[0] && metadataOk[0] && stateCode[0] != PlaybackState.STATE_PLAYING) {
                        finish.accept(true, "stale=" + staleId + " resumed=" + expectedId[0]
                            + " state=" + stateCode[0] + " audioFocusVerified=false");
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
                        if (metadata == null || expectedId[0] == null) return;
                        String id = metadata.getDescription().getMediaId();
                        metadataId[0] = id;
                        if (staleId.equals(id)) {
                            finish.accept(false, "stale metadata surfaced=" + id);
                            return;
                        }
                        if (!expectedId[0].equals(id) || metadata.getDescription().getMediaUri() != null) {
                            finish.accept(false, "unexpected metadata id=" + id + " uri=" + metadata.getDescription().getMediaUri());
                            return;
                        }
                        metadataOk[0] = true;
                        maybeFinish.run();
                    }
                    @Override public void onPlaybackStateChanged(PlaybackState state) {
                        if (state == null) return;
                        stateCode[0] = state.getState();
                        if (stateCode[0] == PlaybackState.STATE_ERROR) {
                            finish.accept(false, "playback error=" + state.getErrorMessage());
                            return;
                        }
                        maybeFinish.run();
                    }
                }, handler);
                browser[0].subscribe("recent", new MediaBrowser.SubscriptionCallback() {
                    @Override public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> items) {
                        if (items == null || items.isEmpty()) {
                            finish.accept(false, "recent empty");
                            return;
                        }
                        String id = items.get(0).getMediaId();
                        if (id == null || !id.matches("channel:[1-9][0-9]*") || staleId.equals(id)) {
                            finish.accept(false, "invalid current recent id=" + id);
                            return;
                        }
                        expectedId[0] = id;
                        controller[0].getTransportControls().playFromMediaId(staleId, null);
                    }
                    @Override public void onError(String parentId) { finish.accept(false, "recent browse error"); }
                });
            }
            @Override public void onConnectionFailed() { finish.accept(false, "connection failed"); }
            @Override public void onConnectionSuspended() { finish.accept(false, "connection suspended"); }
        }, null);

        handler.postDelayed(() -> finish.accept(false, "timeout stale=" + staleId + " expected=" + expectedId[0]
            + " state=" + stateCode[0] + " metadata=" + metadataOk[0] + " id=" + metadataId[0]), 15000);
        browser[0].connect();
    }
}
