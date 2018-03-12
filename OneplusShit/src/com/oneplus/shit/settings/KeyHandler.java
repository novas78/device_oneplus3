/*
 * Copyright (C) 2015-2016 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
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

package com.oneplus.shit.settings;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.session.MediaSessionLegacyHelper;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;

import com.oneplus.shit.settings.ScreenOffGesture;

import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.gzosp.ActionConstants;
import com.android.internal.util.gzosp.Action;

import java.util.Arrays;

import com.oneplus.shit.settings.SliderControllerBase;
import com.oneplus.shit.settings.slider.NotificationController;
import com.oneplus.shit.settings.slider.FlashlightController;
import com.oneplus.shit.settings.slider.BrightnessController;
import com.oneplus.shit.settings.slider.RotationController;
import com.oneplus.shit.settings.slider.RingerController;
import com.oneplus.shit.settings.slider.NotificationRingerController;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = KeyHandler.class.getSimpleName();
    private static final int GESTURE_REQUEST = 1;
    // Supported scancodes
    private static final int GESTURE_CIRCLE_SCANCODE = 250;
    private static final int GESTURE_SWIPE_DOWN_SCANCODE = 251;
    private static final int GESTURE_V_SCANCODE = 252;
    private static final int GESTURE_LTR_SCANCODE = 253;
    private static final int GESTURE_GTR_SCANCODE = 254;
    private static final int GESTURE_V_UP_SCANCODE = 255;

    private static final int[] sSupportedGestures = new int[]{
        GESTURE_CIRCLE_SCANCODE,
        GESTURE_SWIPE_DOWN_SCANCODE,
        GESTURE_V_SCANCODE,
        GESTURE_V_UP_SCANCODE,
        GESTURE_LTR_SCANCODE,
        GESTURE_GTR_SCANCODE,

    private static final String ACTION_UPDATE_SLIDER_SETTINGS
            = "com.oneplus.shit.settings.UPDATE_SLIDER_SETTINGS";

    private static final String EXTRA_SLIDER_USAGE = "usage";
    private static final String EXTRA_SLIDER_ACTIONS = "actions";

    private final Context mContext;
    private final PowerManager mPowerManager;
    private Context mGestureContext = null;
    private EventHandler mEventHandler;
    private SensorManager mSensorManager;
    private Sensor mProximitySensor;
    private Vibrator mVibrator;
    WakeLock mProximityWakeLock;
    WakeLock mGestureWakeLock;
    private int mProximityTimeOut;
    private boolean mProximityWakeSupported;

    private final NotificationController mNotificationController;
    private final FlashlightController mFlashlightController;
    private final BrightnessController mBrightnessController;
    private final RotationController mRotationController;
    private final RingerController mRingerController;
    private final NotificationRingerController mNotificationRingerController;

    private SliderControllerBase mSliderController;

    private final BroadcastReceiver mUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int usage = intent.getIntExtra(EXTRA_SLIDER_USAGE, 0);
            int[] actions = intent.getIntArrayExtra(EXTRA_SLIDER_ACTIONS);

            Log.d(TAG, "update usage " + usage + " with actions " +
                    Arrays.toString(actions));

            if (mSliderController != null) {
                mSliderController.reset();
            }

            switch (usage) {
                case NotificationController.ID:
                    mSliderController = mNotificationController;
                    mSliderController.update(actions);
                    break;
                case FlashlightController.ID:
                    mSliderController = mFlashlightController;
                    mSliderController.update(actions);
                    break;
                case BrightnessController.ID:
                    mSliderController = mBrightnessController;
                    mSliderController.update(actions);
                    break;
                case RotationController.ID:
                    mSliderController = mRotationController;
                    mSliderController.update(actions);
                    break;
                case RingerController.ID:
                    mSliderController = mRingerController;
                    mSliderController.update(actions);
                    break;
                case NotificationRingerController.ID:
                    mSliderController = mNotificationRingerController;
                    mSliderController.update(actions);
                    break;
            }

            mSliderController.restoreState();
        }
    };

    public KeyHandler(Context context) {
        mContext = context;
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mEventHandler = new EventHandler();
        mGestureWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "GestureWakeLock");

        final Resources resources = mContext.getResources();
        mProximityTimeOut = resources.getInteger(
                com.android.internal.R.integer.config_proximityCheckTimeout);
        mProximityWakeSupported = resources.getBoolean(
                com.android.internal.R.bool.config_proximityCheckOnWake);

        if (mProximityWakeSupported) {
            mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            mProximityWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "ProximityWakeLock");
        }

        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            mVibrator = null;
        }

        try {
            mGestureContext = mContext.createPackageContext(
                    "com.oneplus.shit", Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
        }

		mNotificationController = new NotificationController(context);
        mFlashlightController = new FlashlightController(context);
        mBrightnessController = new BrightnessController(context);
        mRotationController = new RotationController(context);
        mRingerController = new RingerController(context);
        mNotificationRingerController = new NotificationRingerController(context);

        mContext.registerReceiver(mUpdateReceiver,
                new IntentFilter(ACTION_UPDATE_SLIDER_SETTINGS));
    }

    private class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            KeyEvent event = (KeyEvent) msg.obj;
            String action = null;
            switch(event.getScanCode()) {
            case GESTURE_CIRCLE_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_CIRCLE,
                        ActionConstants.ACTION_CAMERA);
                        doHapticFeedback();
                break;
            case GESTURE_SWIPE_DOWN_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_DOUBLE_SWIPE,
                        ActionConstants.ACTION_MEDIA_PLAY_PAUSE);
                        doHapticFeedback();
                break;
            case GESTURE_V_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_DOWN,
                        ActionConstants.ACTION_VIB_SILENT);
                        doHapticFeedback();
                break;
            case GESTURE_V_UP_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_UP,
                        ActionConstants.ACTION_TORCH);
                        doHapticFeedback();
                break;
            case GESTURE_LTR_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_LEFT,
                        ActionConstants.ACTION_MEDIA_PREVIOUS);
                        doHapticFeedback();
                break;
            case GESTURE_GTR_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_RIGHT,
                        ActionConstants.ACTION_MEDIA_NEXT);
                        doHapticFeedback();
                break;
            }
            if (action == null || action != null && action.equals(ActionConstants.ACTION_NULL)) {
                return;
            }
            if (action.equals(ActionConstants.ACTION_CAMERA)
                    || !action.startsWith("**")) {
                Action.processAction(mContext, ActionConstants.ACTION_WAKE_DEVICE, false);
            }
            Action.processAction(mContext, action, false);
        }

    }

    private boolean hasSetupCompleted() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
    }

    private void doHapticFeedback() {
        if (mVibrator == null || !SystemProperties.getBoolean(PROP_HAPTIC_FEEDBACK, true)) return;
        mVibrator.vibrate(50);
    }

    private SharedPreferences getGestureSharedPreferences() {
        return mGestureContext.getSharedPreferences(
                ScreenOffGesture.GESTURE_SETTINGS,
                Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
    }
		
    public KeyEvent handleKeyEvent(KeyEvent event) {
        int scanCode = event.getScanCode();
        boolean isSliderControllerSupported = mSliderController != null &&
                mSliderController.isSupported(scanCode);
        if (!isKeySupported && !isSliderControllerSupported) {
            return event;
        }

        if (!hasSetupCompleted()) {
            return event;
        }

        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
		
        if (isSliderControllerSupported) {
            mSliderController.processEvent(scanCode);
        } else if (isKeySupported && !mEventHandler.hasMessages(GESTURE_REQUEST)) {
        boolean isKeySupported = ArrayUtils.contains(sSupportedGestures, scanCode);
            Message msg = getMessageForKeyEvent(event);
            if (scanCode < mProximitySensor != null) {
                mEventHandler.sendMessageDelayed(msg, 200);
                processEvent(event);
            } else {
                mEventHandler.sendMessage(msg);
            }
        }
        return isKeySupported;
    }

    private Message getMessageForKeyEvent(int scancode) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.arg1 = scancode;
        return msg;
    }

    private void processEvent(final KeyEvent keyEvent) {
        mProximityWakeLock.acquire();
        mSensorManager.registerListener(new SensorEventListener() {

            @Override
            public void onSensorChanged(SensorEvent event) {
                mProximityWakeLock.release();
                mSensorManager.unregisterListener(this);
                if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
                    // The sensor took to long, ignoring.
                    return;
                }
                mEventHandler.removeMessages(GESTURE_REQUEST);
                if (event.values[0] == mProximitySensor.getMaximumRange()) {
                    Message msg = getMessageForKeyEvent(keyEvent);
                    mEventHandler.sendMessage(msg);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        }, mProximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
    }
}
