/* SPDX-FileCopyrightText: 2026 Paravel contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.capture;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerService;
import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** An on-demand snapshot of visible playback for trusted screenshot clients. No history. */
public final class CaptureProvider extends ContentProvider {
    public static final String PERMISSION = "com.paravel.video.CAPTURE_POSITION";
    private static WeakReference<PlayerService> service = new WeakReference<>(null);

    public static void attach(final PlayerService value) {
        service = new WeakReference<>(value);
    }

    public static void detach(final PlayerService value) {
        if (service.get() == value) {
            service.clear();
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(final String method, final String arg, final Bundle extras) {
        getContext().enforceCallingOrSelfPermission(PERMISSION, "Capture permission required");
        if (!"capture_position".equals(method) && !"presentation".equals(method)
                && !"play_backlink".equals(method)) {
            return null;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return snapshot(method, extras);
        }
        final AtomicReference<Bundle> result = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                result.set(snapshot(method, extras));
            } finally {
                done.countDown();
            }
        });
        try {
            return done.await(700, TimeUnit.MILLISECONDS) ? result.get() : null;
        } catch (final InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static Bundle snapshot(final String method, final Bundle extras) {
        final PlayerService current = service.get();
        final Player player = current == null ? null : current.getPlayer();
        try {
            if ("play_backlink".equals(method)) {
                final Bundle result = new Bundle();
                final org.schabi.newpipe.MainActivity activity = player == null ? null
                        : player.UIs()
                        .get(org.schabi.newpipe.player.ui.MainPlayerUi.class)
                        .flatMap(org.schabi.newpipe.player.ui.MainPlayerUi::getParentActivity)
                        .filter(org.schabi.newpipe.MainActivity.class::isInstance)
                        .map(org.schabi.newpipe.MainActivity.class::cast)
                        .orElse(null);
                if (activity != null && extras != null && android.os.Build.VERSION.SDK_INT >= 24
                        && activity.isInPictureInPictureMode()) {
                    activity.openFloatingBacklink(new android.content.Intent(
                            BacklinkPlayback.ACTION)
                            .setClassName(activity, "org.schabi.newpipe.capture.FloatingVideo")
                            .putExtras(extras));
                    result.putBoolean("accepted", true);
                }
                return result;
            }
            if ("presentation".equals(method)) {
                final Bundle state = new Bundle();
                state.putBoolean("native_pip", player != null && android.os.Build.VERSION.SDK_INT
                        >= 24 && player.UIs().get(org.schabi.newpipe.player.ui.MainPlayerUi.class)
                        .flatMap(org.schabi.newpipe.player.ui.MainPlayerUi::getParentActivity)
                        .map(android.app.Activity::isInPictureInPictureMode).orElse(false));
                return state;
            }
            return player == null ? null : player.captureVisiblePosition();
        } catch (final RuntimeException error) {
            return null;
        }
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection,
                        final String[] selectionArgs, final String sortOrder) {
        return null;
    }
    @Override
    public String getType(final Uri uri) {
        return null;
    }
    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        return null;
    }
    @Override
    public int delete(final Uri uri, final String selection, final String[] args) {
        return 0;
    }
    @Override
    public int update(final Uri uri, final ContentValues values,
                      final String selection, final String[] args) {
        return 0;
    }
}
