/* SPDX-FileCopyrightText: 2026 Paravel contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.helper;

import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.util.Rational;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.ui.MainPlayerUi;

import java.util.Optional;

/** Keeps the existing activity, decoder and SurfaceView alive during system PiP transitions. */
public final class PictureInPictureController {
    private final MainActivity activity;
    private boolean enabled;
    private boolean pipSession;
    private boolean pipLayoutApplied;
    private boolean wasFullscreen;
    private PictureInPictureParams params;
    private Rect lastBounds;
    private Rational lastAspect;
    private String requestedUrl;

    public void requestEntry(final String url) {
        requestedUrl = url;
    }

    public void cancelRequestedEntry() {
        requestedUrl = null;
    }

    public PictureInPictureController(final MainActivity activity) {
        this.activity = activity;
    }

    private Optional<MainPlayerUi> mainUi() {
        return PlayerHolder.getInstance().getPlayer()
                .flatMap(player -> player.UIs().get(MainPlayerUi.class))
                .filter(ui -> ui.getParentActivity().orElse(null) == activity);
    }

    public void update() {
        activity.applyBacklinkSeek();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !activity.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return;
        }
        final MainPlayerUi ui = mainUi().orElse(null);
        final Player player = PlayerHolder.getInstance().getPlayer().orElse(null);
        final Rect bounds = new Rect();
        final boolean canEnter = ui != null && player != null
                && !activity.isFinishing() && (player.isPlaying() || player.isLoading())
                && (requestedUrl != null || PlayerHelper.getMinimizeOnExitAction(activity)
                == PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP)
                && ui.getBinding().surfaceView.getGlobalVisibleRect(bounds)
                && bounds.width() > 0 && bounds.height() > 0;
        // The system owns PiP bounds while pinned. Preserve the full-size animation hint.
        if (activity.isInPictureInPictureMode()) {
            if (ui != null) {
                if (!pipLayoutApplied) {
                    onModeChanged(true);
                }
                ui.hideControls(0, 0);
            }
            requestedUrl = null;
            return;
        }
        final Rational aspect = canEnter ? new Rational(Math.round(Math.max(0.42f,
                Math.min(2.38f, (float) bounds.width() / bounds.height())) * 1000), 1000)
                : new Rational(16, 9);
        if (params != null && enabled == canEnter && bounds.equals(lastBounds)
                && aspect.equals(lastAspect)) {
            enterIfRequested(player);
            return;
        }
        enabled = canEnter;
        lastBounds = new Rect(bounds);
        lastAspect = aspect;
        final PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(aspect);
        if (canEnter) {
            builder.setSourceRectHint(bounds);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(canEnter).setSeamlessResizeEnabled(true);
        }
        params = builder.build();
        activity.setPictureInPictureParams(params);
        enterIfRequested(player);
    }

    private void enterIfRequested(final Player player) {
        if (enabled && requestedUrl != null && player != null
                && requestedUrl.equals(player.getVideoUrl()) && player.isPlaying()) {
            requestedUrl = null;
            activity.enterPictureInPictureMode(params);
        }
    }

    public void onUserLeaveHint() {
        update();
        if (!enabled) {
            return;
        }
        mainUi().ifPresent(ui -> ui.hideControls(0, 0));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            activity.enterPictureInPictureMode(params);
        }
    }

    public void onModeChanged(final boolean inPip) {
        mainUi().ifPresent(ui -> {
            if (inPip) {
                pipSession = true;
                if (!pipLayoutApplied) {
                    wasFullscreen = ui.isFullscreen();
                    pipLayoutApplied = true;
                }
                if (!ui.isFullscreen()) {
                    ui.toggleFullscreen();
                }
                ui.hideControls(0, 0);
            } else if (pipLayoutApplied) {
                pipLayoutApplied = false;
                if (ui.isFullscreen() != wasFullscreen) {
                    ui.toggleFullscreen();
                }
            }
        });
    }

    public void onResume() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                || !activity.isInPictureInPictureMode()) {
            onModeChanged(false);
            pipSession = false;
        }
        update();
    }

    public void onStop() {
        if (pipSession) {
            // Dismissing system PiP must not spawn a second legacy popup behind it.
            PlayerHolder.getInstance().getPlayer().ifPresent(Player::pause);
        }
    }

    public boolean suppressLegacyPopup() {
        return pipSession || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && activity.isInPictureInPictureMode());
    }
}
