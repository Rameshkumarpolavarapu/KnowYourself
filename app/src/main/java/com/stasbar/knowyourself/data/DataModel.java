package com.stasbar.knowyourself.data;
/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.os.Handler;
import android.os.Looper;


import android.app.Service;
import android.net.Uri;
import android.support.annotation.StringRes;

import com.stasbar.knowyourself.timer.TimerService;

import java.util.List;

import static com.stasbar.knowyourself.Utils.enforceMainLooper;


/**
 * All application-wide data is accessible through this singleton.
 */
public final class DataModel {

    /** Indicates the display style of clocks. */
    public enum ClockStyle {ANALOG, DIGITAL}


    public static final String ACTION_WORLD_CITIES_CHANGED =
            "com.stasbar.lifetimetracker.WORLD_CITIES_CHANGED";

    /** The single instance of this data model that exists for the life of the application. */
    private static final DataModel sDataModel = new DataModel();

    private Handler mHandler;

    private Context mContext;

    /** The model from which settings are fetched. */
    private SettingsModel mSettingsModel;


    /** The model from which alarm data are fetched. */
    private AlarmModel mAlarmModel;
    /** The model from which timer data are fetched. */
    private TimerModel mTimerModel;

    /** The model from which alarm data are fetched. */

    /** The model from which widget data are fetched. */
    private WidgetModel mWidgetModel;

    /** The model from which stopwatch data are fetched. */
    private StopwatchModel mStopwatchModel;

    /** The model from which notification data are fetched. */
    private NotificationModel mNotificationModel;

    public static DataModel getDataModel() {
        return sDataModel;
    }

    private DataModel() {}

    /**
     * The context may be set precisely once during the application life.
     */
    public void setContext(Context context) {
        if (mContext != null) {
            throw new IllegalStateException("context has already been set");
        }
        mContext = context.getApplicationContext();

        mSettingsModel = new SettingsModel(mContext);
        mNotificationModel = new NotificationModel();
        mWidgetModel = new WidgetModel(mContext);
        mAlarmModel = new AlarmModel(mContext, mSettingsModel);
        mStopwatchModel = new StopwatchModel(mContext, mNotificationModel);
        mTimerModel = new TimerModel(mContext, mSettingsModel, mNotificationModel);
    }

    /**
     * Convenience for {@code run(runnable, 0)}, i.e. waits indefinitely.
     */
    public void run(Runnable runnable) {
        try {
            run(runnable, 0 /* waitMillis */);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Updates all timers and the stopwatch after the device has shutdown and restarted.
     */
    public void updateAfterReboot() {
        enforceMainLooper();
        mTimerModel.updateTimersAfterReboot();
        mStopwatchModel.setStopwatch(getStopwatch().updateAfterReboot());
    }

    /**
     * Updates all timers and the stopwatch after the device's time has changed.
     */
    public void updateAfterTimeSet() {
        enforceMainLooper();
        mTimerModel.updateTimersAfterTimeSet();
        mStopwatchModel.setStopwatch(getStopwatch().updateAfterTimeSet());
    }

    /**
     * Posts a runnable to the main thread and blocks until the runnable executes. Used to access
     * the data model from the main thread.
     */
    public void run(Runnable runnable, long waitMillis) throws InterruptedException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
            return;
        }

        final ExecutedRunnable er = new ExecutedRunnable(runnable);
        getHandler().post(er);

        // Wait for the data to arrive, if it has not.
        synchronized (er) {
            if (!er.isExecuted()) {
                er.wait(waitMillis);
            }
        }
    }

    /**
     * @return a handler associated with the main thread
     */
    private synchronized Handler getHandler() {
        if (mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }
        return mHandler;
    }

    //
    // Application
    //

    /**
     * @param inForeground {@code true} to indicate the application is open in the foreground
     */
    public void setApplicationInForeground(boolean inForeground) {
        enforceMainLooper();

        if (mNotificationModel.isApplicationInForeground() != inForeground) {
            mNotificationModel.setApplicationInForeground(inForeground);

            // Refresh all notifications in response to a change in app open state.
            mTimerModel.updateNotification();
            mTimerModel.updateMissedNotification();
            mStopwatchModel.updateNotification();
        }
    }

    /**
     * @return {@code true} when the application is open in the foreground; {@code false} otherwise
     */
    public boolean isApplicationInForeground() {
        enforceMainLooper();
        return mNotificationModel.isApplicationInForeground();
    }

    /**
     * Called when the notifications may be stale or absent from the notification manager and must
     * be rebuilt. e.g. after upgrading the application
     */
    public void updateAllNotifications() {
        enforceMainLooper();
        mTimerModel.updateNotification();
        mTimerModel.updateMissedNotification();
        mStopwatchModel.updateNotification();
    }


    //
    // Timers
    //

    /**
     * @param timerListener to be notified when timers are added, updated and removed
     */
    public void addTimerListener(TimerListener timerListener) {
        enforceMainLooper();
        mTimerModel.addTimerListener(timerListener);
    }

    /**
     * @param timerListener to no longer be notified when timers are added, updated and removed
     */
    public void removeTimerListener(TimerListener timerListener) {
        enforceMainLooper();
        mTimerModel.removeTimerListener(timerListener);
    }

    /**
     * @return a list of timers for display
     */
    public List<Timer> getTimers() {
        enforceMainLooper();
        return mTimerModel.getTimers();
    }

    /**
     * @return a list of expired timers for display
     */
    public List<Timer> getExpiredTimers() {
        enforceMainLooper();
        return mTimerModel.getExpiredTimers();
    }

    /**
     * @param timerId identifies the timer to return
     * @return the timer with the given {@code timerId}
     */
    public Timer getTimer(int timerId) {
        enforceMainLooper();
        return mTimerModel.getTimer(timerId);
    }

    /**
     * @return the timer that last expired and is still expired now; {@code null} if no timers are
     *      expired
     */
    public Timer getMostRecentExpiredTimer() {
        enforceMainLooper();
        return mTimerModel.getMostRecentExpiredTimer();
    }

    /**
     * @param length the length of the timer in milliseconds
     * @param label describes the purpose of the timer
     * @param deleteAfterUse {@code true} indicates the timer should be deleted when it is reset
     * @return the newly added timer
     */
    public Timer addTimer(long length, String label, boolean deleteAfterUse) {
        enforceMainLooper();
        return mTimerModel.addTimer(length, label, deleteAfterUse);
    }

    /**
     * @param timer the timer to be removed
     */
    public void removeTimer(Timer timer) {
        enforceMainLooper();
        mTimerModel.removeTimer(timer);
    }

    /**
     * @param timer the timer to be started
     */
    public void startTimer(Timer timer) {
        enforceMainLooper();
        mTimerModel.updateTimer(timer.start());
    }
    /**
     * @param service used to start foreground notifications for expired timers
     * @param timer the timer to be started
     */
    public void startTimer(Service service, Timer timer) {
        enforceMainLooper();
        final Timer started = timer.start();
        mTimerModel.updateTimer(started);
        if (timer.getRemainingTime() <= 0) {
            if (service != null) {
                expireTimer(service, started);
            } else {
                mContext.startService(TimerService.createTimerExpiredIntent(mContext, started));
            }
        }
    }
    /**
     * @param timer the timer to be paused
     */
    public void pauseTimer(Timer timer) {
        enforceMainLooper();
        mTimerModel.updateTimer(timer.pause());
    }

    /**
     * @param service used to start foreground notifications for expired timers
     * @param timer the timer to be expired
     */
    public void expireTimer(Service service, Timer timer) {
        enforceMainLooper();
        mTimerModel.expireTimer(service, timer);
    }

    /**
     * @param timer the timer to be reset
     * @return the reset {@code timer}
     */
    public Timer resetTimer(Timer timer) {
        enforceMainLooper();
        return mTimerModel.resetTimer(timer, false /* allowDelete */, 0 /* eventLabelId */);
    }

    /**
     * If the given {@code timer} is expired and marked for deletion after use then this method
     * removes the the timer. The timer is otherwise transitioned to the reset state and continues
     * to exist.
     *
     * @param timer the timer to be reset
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     * @return the reset {@code timer} or {@code null} if the timer was deleted
     */
    public Timer resetOrDeleteTimer(Timer timer, @StringRes int eventLabelId) {
        enforceMainLooper();
        return mTimerModel.resetTimer(timer, true /* allowDelete */, eventLabelId);
    }

    /**
     * Resets all expired timers.
     *
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    public void resetExpiredTimers(@StringRes int eventLabelId) {
        enforceMainLooper();
        mTimerModel.resetExpiredTimers(eventLabelId);
    }

    /**
     * Resets all unexpired timers.
     *
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    public void resetUnexpiredTimers(@StringRes int eventLabelId) {
        enforceMainLooper();
        mTimerModel.resetUnexpiredTimers(eventLabelId);
    }

    /**
     * Resets all missed timers.
     *
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    public void resetMissedTimers(@StringRes int eventLabelId) {
        enforceMainLooper();
        mTimerModel.resetMissedTimers(eventLabelId);
    }


    /**
     * @param timer the timer to which a minute should be added to the remaining time
     */
    public void addTimerMinute(Timer timer) {
        enforceMainLooper();
        mTimerModel.updateTimer(timer.addMinute());
    }

    /**
     * @param timer the timer to which the new {@code label} belongs
     * @param label the new label to store for the {@code timer}
     */
    public void setTimerLabel(Timer timer, String label) {
        enforceMainLooper();
        mTimerModel.updateTimer(timer.setLabel(label));
    }

    /**
     * @param timer  the timer whose {@code length} to change
     * @param length the new length of the timer in milliseconds
     */
    public void setTimerLength(Timer timer, long length) {
        enforceMainLooper();
        mTimerModel.updateTimer(timer.setLength(length));
    }

    /**
     * @param timer         the timer whose {@code remainingTime} to change
     * @param remainingTime the new remaining time of the timer in milliseconds
     */
    public void setRemainingTime(Timer timer, long remainingTime) {
        enforceMainLooper();

        final Timer updated = timer.setRemainingTime(remainingTime);
        mTimerModel.updateTimer(updated);
        if (timer.isRunning() && timer.getRemainingTime() <= 0) {
            mContext.startService(TimerService.createTimerExpiredIntent(mContext, updated));
        }
    }

    /**
     * Updates the timer notifications to be current.
     */
    public void updateTimerNotification() {
        enforceMainLooper();
        mTimerModel.updateNotification();
    }

    /**
     * @return the uri of the default ringtone to play for all timers when no user selection exists
     */
    public Uri getDefaultTimerRingtoneUri() {
        enforceMainLooper();
        return mTimerModel.getDefaultTimerRingtoneUri();
    }

    /**
     * @return {@code true} iff the ringtone to play for all timers is the silent ringtone
     */
    public boolean isTimerRingtoneSilent() {
        enforceMainLooper();
        return mTimerModel.isTimerRingtoneSilent();
    }

    /**
     * @return the uri of the ringtone to play for all timers
     */
    public Uri getTimerRingtoneUri() {
        enforceMainLooper();
        return mTimerModel.getTimerRingtoneUri();
    }

    /**
     * @param uri the uri of the ringtone to play for all timers
     */
    public void setTimerRingtoneUri(Uri uri) {
        enforceMainLooper();
        mTimerModel.setTimerRingtoneUri(uri);
    }

    /**
     * @return the title of the ringtone that is played for all timers
     */
    public String getTimerRingtoneTitle() {
        enforceMainLooper();
        return mTimerModel.getTimerRingtoneTitle();
    }

    /**
     * @return whether vibrate is enabled for all timers.
     */
    public boolean getTimerVibrate() {
        enforceMainLooper();
        return mTimerModel.getTimerVibrate();
    }

    /**
     * @param enabled whether vibrate is enabled for all timers.
     */
    public void setTimerVibrate(boolean enabled) {
        enforceMainLooper();
        mTimerModel.setTimerVibrate(enabled);
    }

    //
    // Alarms
    //

    /**
     * @return the uri of the ringtone to which all new alarms default
     */
    public Uri getDefaultAlarmRingtoneUri() {
        enforceMainLooper();
        return mAlarmModel.getDefaultAlarmRingtoneUri();
    }

    /**
     * @param uri the uri of the ringtone to which future new alarms will default
     */
    public void setDefaultAlarmRingtoneUri(Uri uri) {
        enforceMainLooper();
        mAlarmModel.setDefaultAlarmRingtoneUri(uri);
    }

    /**
     * @param uri the uri of a ringtone
     * @return the title of the ringtone with the {@code uri}; {@code null} if it cannot be fetched
     */
    public String getAlarmRingtoneTitle(Uri uri) {
        enforceMainLooper();
        return mAlarmModel.getAlarmRingtoneTitle(uri);
    }

    //
    // Stopwatch
    //

    /**
     * @param stopwatchListener to be notified when stopwatch changes or laps are added
     */
    public void addStopwatchListener(StopwatchListener stopwatchListener) {
        enforceMainLooper();
        mStopwatchModel.addStopwatchListener(stopwatchListener);
    }

    /**
     * @param stopwatchListener to no longer be notified when stopwatch changes or laps are added
     */
    public void removeStopwatchListener(StopwatchListener stopwatchListener) {
        enforceMainLooper();
        mStopwatchModel.removeStopwatchListener(stopwatchListener);
    }

    /**
     * @return the current state of the stopwatch
     */
    public Stopwatch getStopwatch() {
        enforceMainLooper();
        return mStopwatchModel.getStopwatch();
    }

    /**
     * @return the stopwatch after being started
     */
    public Stopwatch startStopwatch() {
        enforceMainLooper();
        return mStopwatchModel.setStopwatch(getStopwatch().start());
    }

    /**
     * @return the stopwatch after being paused
     */
    public Stopwatch pauseStopwatch() {
        enforceMainLooper();
        return mStopwatchModel.setStopwatch(getStopwatch().pause());
    }

    /**
     * @return the stopwatch after being reset
     */
    public Stopwatch resetStopwatch() {
        enforceMainLooper();
        return mStopwatchModel.setStopwatch(getStopwatch().reset());
    }

    /**
     * @return the laps recorded for this stopwatch
     */
    public List<Lap> getLaps() {
        enforceMainLooper();
        return mStopwatchModel.getLaps();
    }

    /**
     * @return a newly recorded lap completed now; {@code null} if no more laps can be added
     */
    public Lap addLap() {
        enforceMainLooper();
        return mStopwatchModel.addLap();
    }

    /**
     * @return {@code true} iff more laps can be recorded
     */
    public boolean canAddMoreLaps() {
        enforceMainLooper();
        return mStopwatchModel.canAddMoreLaps();
    }

    /**
     * @return the longest lap time of all recorded laps and the current lap
     */
    public long getLongestLapTime() {
        enforceMainLooper();
        return mStopwatchModel.getLongestLapTime();
    }

    /**
     * @param time a point in time after the end of the last lap
     * @return the elapsed time between the given {@code time} and the end of the previous lap
     */
    public long getCurrentLapTime(long time) {
        enforceMainLooper();
        return mStopwatchModel.getCurrentLapTime(time);
    }

    //
    // Widgets
    //

    /**
     * @param widgetClass indicates the type of widget being counted
     * @param count the number of widgets of the given type
     * @param eventCategoryId identifies the category of event to send
     */
    public void updateWidgetCount(Class widgetClass, int count, @StringRes int eventCategoryId) {
        enforceMainLooper();
        mWidgetModel.updateWidgetCount(widgetClass, count);
    }

    //
    // Settings
    //

    /**
     * @return the style of clock to display in the clock application
     */
    public ClockStyle getClockStyle() {
        enforceMainLooper();
        return mSettingsModel.getClockStyle();
    }

    /**
     * @return the style of clock to display in the clock screensaver
     */
    public ClockStyle getScreensaverClockStyle() {
        enforceMainLooper();
        return mSettingsModel.getScreensaverClockStyle();
    }

    /**
     * @return {@code true} if the screen saver should be dimmed for lower contrast at night
     */
    public boolean getScreensaverNightModeOn() {
        enforceMainLooper();
        return mSettingsModel.getScreensaverNightModeOn();
    }

    /**
     * @return {@code true} if the users wants to automatically show a clock for their home timezone
     *      when they have travelled outside of that timezone
     */
    public boolean getShowHomeClock() {
        enforceMainLooper();
        return mSettingsModel.getShowHomeClock();
    }

    /**
     * Used to execute a delegate runnable and track its completion.
     */
    private static class ExecutedRunnable implements Runnable {

        private final Runnable mDelegate;
        private boolean mExecuted;

        private ExecutedRunnable(Runnable delegate) {
            this.mDelegate = delegate;
        }

        @Override
        public void run() {
            mDelegate.run();

            synchronized (this) {
                mExecuted = true;
                notifyAll();
            }
        }

        private boolean isExecuted() {
            return mExecuted;
        }
    }
}
