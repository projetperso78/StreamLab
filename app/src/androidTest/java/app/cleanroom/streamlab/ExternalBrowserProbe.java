package app.cleanroom.streamlab;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.browse.MediaBrowser;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Runs under the test APK's separate UID to exercise the platform browser bridge. */
public final class ExternalBrowserProbe extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        final PendingResult result = goAsync();
        final Context appContext = context.getApplicationContext();
        final Handler handler = new Handler(Looper.getMainLooper());
        final MediaBrowser[] browser = new MediaBrowser[1];
        final boolean[] finished = {false};
        final java.util.function.BiConsumer<Boolean, String> finish = (ok, message) -> {
            if (finished[0]) return;
            finished[0] = true;
            Log.i("StreamLabAutoProbe", (ok ? "OK " : "ERROR ") + message);
            result.setResultCode(ok ? 0 : 1);
            result.setResultData(message);
            if (browser[0] != null) browser[0].disconnect();
            result.finish();
        };
        ComponentName component = new ComponentName("app.cleanroom.streamlab", "app.cleanroom.streamlab.player.PlaybackService");
        browser[0] = new MediaBrowser(appContext, component,
            new MediaBrowser.ConnectionCallback() {
                @Override public void onConnected() {
                    final String root = browser[0].getRoot();
                    browser[0].subscribe(root, new MediaBrowser.SubscriptionCallback() {
                        @Override public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
                            boolean validRoot = "root".equals(parentId) && children != null && children.size() == 4
                                && "all".equals(children.get(0).getMediaId())
                                && "profiles".equals(children.get(1).getMediaId())
                                && "favorites".equals(children.get(2).getMediaId())
                                && "recent".equals(children.get(3).getMediaId());
                            if (!validRoot) {
                                finish.accept(false, "invalid root children=" + (children == null ? -1 : children.size()));
                                return;
                            }
                            final String[] branches = {"all", "profiles", "favorites", "recent"};
                            final Map<String, Integer> counts = new HashMap<>();
                            for (String branch : branches) {
                                browser[0].subscribe(branch, new MediaBrowser.SubscriptionCallback() {
                                    @Override public void onChildrenLoaded(String branchId, List<MediaBrowser.MediaItem> items) {
                                        if (finished[0] || counts.containsKey(branchId)) return;
                                        int count = items == null ? 0 : items.size();
                                        counts.put(branchId, count);
                                        if (count <= 0) {
                                            finish.accept(false, "empty branch=" + branchId);
                                            return;
                                        }
                                        MediaBrowser.MediaItem first = items.get(0);
                                        boolean validItem;
                                        if ("profiles".equals(branchId)) {
                                            validItem = first.getMediaId() != null && first.getMediaId().startsWith("profile:")
                                                && (first.getFlags() & MediaBrowser.MediaItem.FLAG_BROWSABLE) != 0;
                                        } else {
                                            validItem = first.getMediaId() != null && first.getMediaId().matches("channel:[1-9][0-9]*")
                                                && (first.getFlags() & MediaBrowser.MediaItem.FLAG_PLAYABLE) != 0
                                                && first.getDescription().getMediaUri() == null;
                                        }
                                        if (!validItem) {
                                            finish.accept(false, "invalid item branch=" + branchId + " id=" + first.getMediaId());
                                            return;
                                        }
                                        if (counts.size() == branches.length) {
                                            finish.accept(true, "root=4 all=" + counts.get("all")
                                                + " profiles=" + counts.get("profiles")
                                                + " favorites=" + counts.get("favorites")
                                                + " recent=" + counts.get("recent"));
                                        }
                                    }
                                    @Override public void onError(String branchId) {
                                        finish.accept(false, "children error branch=" + branchId);
                                    }
                                });
                            }
                        }
                        @Override public void onError(String parentId) {
                            finish.accept(false, "root children error");
                        }
                    });
                }
                @Override public void onConnectionFailed() {
                    finish.accept(false, "connection failed");
                }
                @Override public void onConnectionSuspended() {
                    finish.accept(false, "connection suspended");
                }
            }, null);
        handler.postDelayed(() -> finish.accept(false, "timeout"), 15000);
        browser[0].connect();
    }
}
