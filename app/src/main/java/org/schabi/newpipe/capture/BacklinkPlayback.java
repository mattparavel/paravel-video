/* SPDX-FileCopyrightText: 2026 Paravel contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.capture;

import android.content.Intent;
import android.widget.Toast;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.player.ui.MainPlayerUi;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.SerialDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Explicit, signature-protected entry point used by the canvas's video backlinks. */
public final class BacklinkPlayback {
    public static final String ACTION = "com.paravel.video.PLAY_IN_PIP";
    private final MainActivity activity;
    private final SerialDisposable loading = new SerialDisposable();
    private String pendingUrl;
    private long pendingSeconds;

    public BacklinkPlayback(final MainActivity activity) {
        this.activity = activity;
    }

    public void open(final Intent intent) {
        if (intent.getComponent() == null || !"org.schabi.newpipe.capture.FloatingVideo"
                .equals(intent.getComponent().getClassName())) {
            return;
        }
        final String id = intent.getStringExtra("video_id");
        final long seconds = intent.getLongExtra("start_seconds", -1);
        if (id == null || !id.matches("[A-Za-z0-9_-]{11}")
                || seconds < -1 || seconds > 99999999) {
            return;
        }
        loading.set(null);
        final String url = "https://www.youtube.com/watch?v=" + id;
        pendingUrl = url;
        pendingSeconds = seconds;
        activity.getPictureInPictureController().requestEntry(url);
        final Player current = PlayerHolder.getInstance().getPlayer().orElse(null);
        if (current != null && !current.exoPlayerIsNull() && url.equals(current.getVideoUrl())
                && current.UIs().get(MainPlayerUi.class)
                .flatMap(MainPlayerUi::getParentActivity).orElse(null) == activity) {
            applyPendingSeek();
            current.play();
            activity.getPictureInPictureController().update();
            return;
        }
        loading.set(ExtractorHelper.getStreamInfo(ServiceList.YouTube.getServiceId(), url, false)
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> NavigationHelper.openVideoDetailFragment(activity,
                        activity.getSupportFragmentManager(), info.getServiceId(), url,
                        info.getName(), seconds < 0 ? new SinglePlayQueue(info)
                                : new SinglePlayQueue(info, seconds * 1000), false, true),
                        error -> {
                            pendingUrl = null;
                            activity.getPictureInPictureController().cancelRequestedEntry();
                            Toast.makeText(activity, "Could not load this YouTube video.",
                                    Toast.LENGTH_LONG).show();
                        }));
    }

    public void applyPendingSeek() {
        final Player player = PlayerHolder.getInstance().getPlayer().orElse(null);
        if (pendingUrl != null && player != null && !player.exoPlayerIsNull()
                && pendingUrl.equals(player.getVideoUrl())
                && player.UIs().get(MainPlayerUi.class)
                .flatMap(MainPlayerUi::getParentActivity).orElse(null) == activity
                && player.getExoPlayer().getPlaybackState()
                == com.google.android.exoplayer2.Player.STATE_READY
                && !player.getExoPlayer().getCurrentTimeline().isEmpty()) {
            // Initial UI attachment can reload the queue. Apply the explicit time after that
            // first timeline is ready, before treating this backlink as fulfilled.
            pendingUrl = null;
            if (pendingSeconds < 0) {
                player.getExoPlayer().seekToDefaultPosition();
            } else {
                player.getExoPlayer().seekTo(pendingSeconds * 1000);
            }
            player.play();
        }
    }

    public void destroy() {
        loading.dispose();
    }
}
