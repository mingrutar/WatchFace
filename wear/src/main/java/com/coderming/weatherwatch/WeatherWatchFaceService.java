/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.coderming.weatherwatch;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.exmple.coderming.app.waerableshared.Utilities;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class WeatherWatchFaceService extends CanvasWatchFaceService {
    private static final String LOG_TAG = WeatherWatchFaceService.class.getSimpleName();

    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    private static final long NORMAL_UPDATE_RATE_MS = 2000;     //500

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    // TODO: initial weather    private class Engine extends CanvasWatchFaceService.Engine implements
// GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
// ResultCallback<DailyTotalResult>
// for DigitalWatch for DataApi.DataListener,

    private class Engine extends CanvasWatchFaceService.Engine
            implements DataApi.DataListener, MessageApi.MessageListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        private static final float HOUR_STROKE_WIDTH = 5f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;
        private static final float CIRCLE_STOKE_WIDTH = 1f;

        private static final int TICK_LENGTH = 10;
        private static final int TICK_NUMBER = 12;
        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 6;

        static final int NORMAL_ALPHA = 255;


        private Calendar mCalendar;
        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mHandPaint;

        private boolean mMuteMode;
        long mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;

        private float mCenterX;
        private float mCenterY;
        private float mLeftCircleX;
        private float mMinCircleR;
        private float mCircleR;

        private float mSecondHandLength;
        private float mMinuteHandLength;
        private float mHourHandLength;

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mWatchHandColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;
        private int mCircleColor;

        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mTickAndCirclePaint;
        private Paint mCirclePaint;
        private Paint mTempHighPaint;
        private Paint mTempLowPaint;

        boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean mSaveMode;

        private Rect mPeekCardBounds = new Rect();

        // notification for time zone changing
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        // temperature
        private float mTextY;
        // images
        private Bitmap mBrandIcon;
        private Bitmap mGreyBrandIcon;
        private RectF mBrandIconDest;
        private Bitmap mWeatherIcon;
        private Bitmap mGreyWeatherIcon;
        private RectF mWeatherIconDest;

//        @DrawableRes int mWeatherIconResId;
        private String mHighTempStr;
        private String mLowTempStr;
        private boolean mIsMetric;
        private String mWeatherDesc;
        String[] mLastWeatherData;
        private String  mPeerId;

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
              invalidate();
                if (shouldTimerBeRunning()) {
                    long timeMs = System.currentTimeMillis();
                    long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                    mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                }

            }
        };
        // google client
        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(WeatherWatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

         // Handles time zone and locale changes.
        final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        boolean mRegisteredReceiver = false;    // register BroadcastReceiver

        int mTapCount;

         /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WeatherWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)      // ? not in sweepWF
                    .build());

            Resources resources = WeatherWatchFaceService.this.getResources();

            mBackgroundPaint = new Paint();

            /* Set defaults for colors */
            mWatchHandHighlightColor = Color.RED;
            if (Build.VERSION.SDK_INT < 23) {
                mWatchHandColor = resources.getColor(R.color.needle_default);
                mCircleColor = resources.getColor(R.color.tint);
                mWatchHandShadowColor = resources.getColor(R.color.shadow);
                mBackgroundPaint.setColor(resources.getColor(R.color.background));
            } else {
                mWatchHandColor = resources.getColor(R.color.needle_default, getTheme());
                mCircleColor = resources.getColor(R.color.tint, getTheme());
                mWatchHandShadowColor = resources.getColor(R.color.shadow, getTheme());
                mBackgroundPaint.setColor(resources.getColor(R.color.background, getTheme()));
            }
            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandHighlightColor);
            mSecondPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);
            mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(mWatchHandColor);
            mTickAndCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.STROKE);
            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mCirclePaint = new Paint();
            mCirclePaint.setColor(mCircleColor);
            mCirclePaint.setStrokeWidth(CIRCLE_STOKE_WIDTH);
            mCirclePaint.setAntiAlias(true);
            mCirclePaint.setStyle(Paint.Style.STROKE);

            mTempHighPaint = new Paint();
            mTempHighPaint.setColor(mWatchHandColor);
            mTempHighPaint.setTypeface(BOLD_TYPEFACE);
            mTempHighPaint.setTextAlign(Paint.Align.CENTER);
            mTempHighPaint.setAntiAlias(true);
            mTempHighPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTempLowPaint = new Paint();
            mTempLowPaint.setColor(mWatchHandColor);
            mTempLowPaint.setTypeface(NORMAL_TYPEFACE);
            mTempLowPaint.setTextAlign(Paint.Align.CENTER);
            mTempLowPaint.setAntiAlias(true);
            mTempLowPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mCalendar = Calendar.getInstance();

            // TODO: for test only
            prepareData();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                updateWatchHandStyle();
                updateTimer();
            }
        }
        private void updateWatchHandStyle(){
            if (mAmbient){
                @ColorInt int watchColor = getResources().getColor(R.color.white);
                @ColorInt int greyColor = getResources().getColor(R.color.grey);
                mHourPaint.setColor(watchColor);
                mMinutePaint.setColor(watchColor);
                mSecondPaint.setColor(watchColor);
                mTempLowPaint.setColor(watchColor);
                mTempHighPaint.setColor(watchColor);
                mTickAndCirclePaint.setColor(greyColor);
                mCirclePaint.setColor(greyColor);

                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);
                mTickAndCirclePaint.setAntiAlias(false);
                mTempLowPaint.setAntiAlias(false);
                mTempHighPaint.setAntiAlias(false);
                mCirclePaint.setAntiAlias(false);

                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mSecondPaint.clearShadowLayer();
                mTickAndCirclePaint.clearShadowLayer();
                mTempLowPaint.clearShadowLayer();
                mTempHighPaint.clearShadowLayer();
                mCirclePaint.clearShadowLayer();
            } else {
                mHourPaint.setColor(mWatchHandColor);
                mMinutePaint.setColor(mWatchHandColor);
                mSecondPaint.setColor(mWatchHandHighlightColor);
                mTickAndCirclePaint.setColor(mWatchHandColor);
                mTempLowPaint.setColor(mWatchHandColor);
                mTempHighPaint.setColor(mWatchHandColor);
                mCirclePaint.setColor(getResources().getColor(R.color.tint));

                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);
                mTickAndCirclePaint.setAntiAlias(true);
                mTempLowPaint.setAntiAlias(true);
                mTempHighPaint.setAntiAlias(true);
                mCirclePaint.setAntiAlias(true);

                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mTempLowPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mTempHighPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }
        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {  //?? how to verify???
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            setInteractiveUpdateRateMs(inMuteMode ? INTERACTIVE_UPDATE_RATE_MS : NORMAL_UPDATE_RATE_MS);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : NORMAL_ALPHA);
                mMinutePaint.setAlpha(inMuteMode ? 100 : NORMAL_ALPHA);
                mTempLowPaint.setAlpha(inMuteMode ? 100 : NORMAL_ALPHA);
                mTempHighPaint.setAlpha(inMuteMode ? 100 : NORMAL_ALPHA);
                mSecondPaint.setAlpha(inMuteMode ? 80 : NORMAL_ALPHA);
                mCirclePaint.setAlpha(inMuteMode ? 80 : NORMAL_ALPHA);
                invalidate();
            }
        }

        public void setInteractiveUpdateRateMs(long updateRateMs) {
            if (updateRateMs == mInteractiveUpdateRateMs) {
                return;
            }
            mInteractiveUpdateRateMs = updateRateMs;

            // Stop and restart the timer so the new update rate takes effect immediately.
            if (shouldTimerBeRunning()) {
                updateTimer();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;
            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (float) (mCenterX * 0.875);
            mMinuteHandLength = (float) (mCenterX * 0.75);
            mHourHandLength = (float) (mCenterX * 0.5);

            mMinCircleR = (float) (mCenterX * 0.2);
            mCircleR = mMinCircleR;

            mLeftCircleX = (mCenterX - TICK_LENGTH - CENTER_GAP_AND_CIRCLE_RADIUS) / 2 + TICK_LENGTH;
            float textSize =   (mCircleR - getResources().getDimension(R.dimen.padding));
            mTempHighPaint.setTextSize(textSize);
            mTempLowPaint.setTextSize(textSize);
            mTextY = mCenterY - (mTempHighPaint.descent() + mTempHighPaint.ascent())/2;

            setBitmapDestRectF(mMinCircleR * 1.5f);
            mBrandIcon  = vector2Bitmap(R.drawable.udacity_brand, R.color.tint);
            mGreyBrandIcon = vector2Bitmap(R.drawable.udacity_brand, R.color.grey);
        }
        private Bitmap getGrayScaleBitmap(Bitmap ofBitmap) {
            Bitmap bitmap = Bitmap.createBitmap(
               ofBitmap.getWidth(),
               ofBitmap.getHeight(),
               Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
             canvas.drawBitmap(ofBitmap, 0, 0, grayPaint);
            return bitmap;
        }
        private void setBitmapDestRectF(float size) {
            float wF = size;
            float hF = size;
            float leftF = mCenterX - wF/2f;
            float topF =  (mCenterY - hF)/2f;
            mBrandIconDest = new RectF(leftF, topF, leftF + wF, topF + hF);
            topF += mCenterY;
            mWeatherIconDest = new RectF(leftF, topF, leftF + wF, topF + hF);
        }
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        private Bitmap vector2Bitmap(@DrawableRes int drawableId, @ColorRes int color) {
            Drawable drawable = ContextCompat.getDrawable(WeatherWatchFaceService.this, drawableId);
            if (drawable instanceof  VectorDrawable) {
                drawable.setTint(getResources().getColor(color));
                Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
                return bitmap;
            } else {
                if (Log.isLoggable(LOG_TAG, Log.WARN)) {
                    Log.w(LOG_TAG, "unsupported drawable type");
                }
                return null;
            }
        }
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
            }

            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            float innerTickRadius = mCenterX - TICK_LENGTH ;
            float outerTickRadius = mCenterX;
            for (int tickIndex = 0; tickIndex < TICK_NUMBER; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / TICK_NUMBER);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);
            }
            if (mHighTempStr != null) {
                // draw temperature text
                canvas.drawText(mHighTempStr, mLeftCircleX+2f, mTextY, mTempHighPaint);
                canvas.drawText(mLowTempStr, mLeftCircleX+3f + mCenterX, mTextY, mTempLowPaint);
            }
            if (!(mAmbient && (mLowBitAmbient || mBurnInProtection || mSaveMode))) {
                if (mHighTempStr != null) {
                    canvas.drawCircle(mLeftCircleX, mCenterY, mCircleR, mCirclePaint);
                    canvas.drawCircle((mLeftCircleX + mCenterX), mCenterY, mCircleR, mCirclePaint);
                    // draw images
                    canvas.drawBitmap(mAmbient ? mGreyWeatherIcon : mWeatherIcon, null, mWeatherIconDest, null);
                }
                canvas.drawBitmap(mAmbient?mGreyBrandIcon:mBrandIcon, null, mBrandIconDest,  null );
            }
            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds = (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;
            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;
            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;
            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();
            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(mCenterX, mCenterY-CENTER_GAP_AND_CIRCLE_RADIUS, mCenterX, mCenterY-mHourHandLength,mHourPaint);
            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine( mCenterX, mCenterY-CENTER_GAP_AND_CIRCLE_RADIUS, mCenterX, mCenterY-mMinuteHandLength,mMinutePaint);
            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(mCenterX,mCenterY-CENTER_GAP_AND_CIRCLE_RADIUS, mCenterX,mCenterY-mSecondHandLength,mSecondPaint);
           }
            canvas.drawCircle(mCenterX, mCenterY, CENTER_GAP_AND_CIRCLE_RADIUS, mTickAndCirclePaint);
            /* Restore the canvas' original orientation. */
            canvas.restore();
            /* Draw rectangle behind peek card in ambient mode to improve readability. */
            if (mAmbient) {
                canvas.drawRect(mPeekCardBounds, mBackgroundPaint);
            }
            /* Draw every frame as long as we're visible and in interactive mode. */
            if ((isVisible()) && (!mAmbient)) {
                invalidate();
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }
            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WeatherWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WeatherWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }
        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
            mPeekCardBounds.set(rect);
        }
        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }
        //// not in SweepWatchFace
        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }
        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }
        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes a tap
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = WeatherWatchFaceService.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    if (mTapCount % 2 == 0) {     // double tap launch app
                        sendMessage(Utilities.PATH_LAUNCH_APP_MESSAGE, getString(R.string.launch_app));
                    } else {
                        mSaveMode = !mSaveMode;
                        invalidate();
                    }
                    break;
            }
        }
        private boolean updateUiPerConfigDataMap(final DataMap config) {
            boolean uiUpdated = false;
            Boolean isMetric = null;
            for (String configKey : config.keySet()) {
                if (Utilities.BG_COLOR_KEY.equals(configKey)) {
                    @ColorInt int bgColor = config.getInt(configKey);
                    mBackgroundPaint.setColor(bgColor);
                    uiUpdated = true;
                } else {
                    if (Utilities.WEATHER_KEY.equals(configKey)) {
                        String[] strs = config.getString(configKey).split(",");
                        if (strs.length == 4)  {
                            if ( mLastWeatherData != null ) {
                                for (int i = 0; (i < strs.length ) || !uiUpdated; i++) {
                                    if (!strs[i].equals(mLastWeatherData[i])) {
                                        uiUpdated = true;
                                    }
                                }
                            } else {
                                uiUpdated = true;
                            }
                            if (uiUpdated == true)
                                mLastWeatherData = strs;
                        }
                    } else if (Utilities.IS_METRIC_KEY.equals(configKey)) {
                        isMetric = config.getBoolean(configKey);
                        if ((isMetric != null) && (isMetric != mIsMetric)) {
                            mIsMetric = isMetric;
                            uiUpdated = true;
                        }
                    }
                    if (uiUpdated) {
                        prepareData();
                    }
                }
                if (!uiUpdated) {
                    if (Log.isLoggable(LOG_TAG, Log.WARN)) {
                        Log.w(LOG_TAG, "++++?? updateUiPerConfigDataMap: !uiUpdated, config=" + config);
                    }
                } else
                    invalidate();
            }
            return uiUpdated;
        }
        private void prepareData() {
            if (mLastWeatherData != null) {
                try {
                    double num = Double.parseDouble(mLastWeatherData[0]);
                    mHighTempStr = Utilities.formatTemperature(WeatherWatchFaceService.this, num, mIsMetric);
                    num = Double.parseDouble(mLastWeatherData[1]);
                    mLowTempStr = Utilities.formatTemperature(WeatherWatchFaceService.this, num, mIsMetric);
                    int weathId = Integer.parseInt(mLastWeatherData[2]);
                    int weatherIconResId = Utilities.getIconResourceForWeatherCondition(weathId);
                    mWeatherIcon = BitmapFactory.decodeResource(getResources(), weatherIconResId);
                    mGreyWeatherIcon = getGrayScaleBitmap(mWeatherIcon);
                    mWeatherDesc = mLastWeatherData[3];

                    if (mHighTempStr != null) {
                        float szHigh = mTempHighPaint.measureText(mHighTempStr);
                        float szLow = mTempLowPaint.measureText(mLowTempStr);
                        mCircleR = Math.max(mMinCircleR, ((Math.max(szHigh, szLow) / 2f) + 8f));
                    }
                } catch (Exception ex) {
                    Log.w(LOG_TAG, "++++!! prepareData: invalid data: " + mLastWeatherData.toString());
                }
            } else {
                mHighTempStr = null;
                mLowTempStr = null;
                mWeatherIcon = null;
                mWeatherDesc = null;
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    parseDataFromRemote(dataEvent.getDataItem());
                    sendMessage(Utilities.PATH_ACK_MESSAGE, getString(R.string.data_received));
                }
            }
        }
        private void parseDataFromRemote(DataItem dataItem) {
            Uri uri = dataItem.getUri();
            if (uri.getPath().equals(Utilities.PATH_WITH_FEATURE)) {
                String remote = uri.getHost();
                if (!remote.equals(mPeerId)) {
                    if (mPeerId != null)    // phone changed ?
                        Log.w(LOG_TAG, "++++ parseDataFromRemote: remote chaanged: old="+mPeerId+", new="+remote);
                    mPeerId = remote;
                }
                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap config = dataMapItem.getDataMap();
                updateUiPerConfigDataMap(config);
            }
        }
        ResultCallback<DataApi.DataItemResult> mInitResultHandler = new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
            if (dataItemResult.getStatus().isSuccess() && dataItemResult.getDataItem() != null) {
                parseDataFromRemote(dataItemResult.getDataItem());
                sendMessage(Utilities.PATH_ACK_MESSAGE, getString(R.string.data_received));
            } else {
                if (Log.isLoggable(LOG_TAG, Log.WARN)) {
                    Log.w(LOG_TAG, "ResultCallback:onResult:Failed: status="+dataItemResult.getStatus());
                }
            }
            }
        };
        private void sendMessage(String path, String msg) {
            if (mPeerId != null) {
                byte[] payload = msg.getBytes();
                Wearable.MessageApi.sendMessage(mGoogleApiClient, mPeerId, path, payload);
            }
        }
        private Collection<String> getNodes() {
            HashSet<String> results = new HashSet<>();
            NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
            for (Node node : nodes.getNodes()) {
                results.add(node.getId());
            }
            return results;
        }

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            String path = messageEvent.getPath();
            if (Utilities.PATH_PING_WATCH.equals(path)) {
                String nodeId = messageEvent.getSourceNodeId();
                if ( (mPeerId == null) || !mPeerId.equals(nodeId)) {
                    mPeerId = nodeId;
                }
                sendMessage(Utilities.PATH_PING_WATCH, "");
            }
        }

        // get initial data
        private class FindNodeTask extends AsyncTask<Void, Void, Void> {
            @Override
            protected Void doInBackground(Void... args) {
                Collection<String> nodes = getNodes();
                if (!nodes.isEmpty()) {
                    for (String node : nodes) {             // should be one only?
                        Uri.Builder builder = new Uri.Builder();
                        Uri uri = builder.scheme("wear").path(Utilities.PATH_WITH_FEATURE).authority(node).build();
                        Wearable.DataApi.getDataItem(mGoogleApiClient, uri).setResultCallback(mInitResultHandler);   //inteface ResultCallback<DataApi.DataItemResult>
                    }
                }
                return null;
            }
        }
        @Override
        public void onConnected(@Nullable Bundle connectionHint) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            Wearable.MessageApi.addListener(mGoogleApiClient, this);
            new FindNodeTask().execute();
         }

        @Override
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                Log.d(LOG_TAG, "++++onConnectionSuspended: " + cause);
            }
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            if (Log.isLoggable(LOG_TAG, Log.WARN)) {
                Log.w(LOG_TAG, "++++onConnectionFailed: " + connectionResult);
            }
       }
    }
}
