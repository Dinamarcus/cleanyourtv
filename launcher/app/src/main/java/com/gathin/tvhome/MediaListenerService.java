package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Empty on purpose. Android lets an app read the active media sessions only when it holds notification
 * access, and that is granted to a NotificationListenerService component; {@link NowPlayingCard} passes this
 * one to MediaSessionManager and never reads a single notification. Grant it over adb:
 * {@code adb shell cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaListenerService}
 */
public class MediaListenerService extends NotificationListenerService {
}
