/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.systemui;

import android.database.ContentObserver;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup.LayoutParams;


public class BatteryPercentView extends View implements DemoMode {
    public static final String TAG = BatteryPercentView.class.getSimpleName();
    public static final String ACTION_LEVEL_TEST = "com.android.systemui.BATTERY_LEVEL_TEST";

    final static String QuickSettings = "quicksettings";
    final static String StatusBar = "statusbar";
  
    public static final int FULL = 96;
    public static final int EMPTY = 4;

    public static final float SUBPIXEL = 0.4f;  // inset rects for softer edges

    int[] mColors;

    int mBatteryStyle = 0;
    
    Paint mTextPaint;
    int mButtonHeight;
    private final int mChargeColor;
    private float mTextHeight;
    private int mHeight;
    private int mWidth;

    private String mBatteryView;

    private Resources mResources;
    private boolean isBold = false;

    private class BatteryTracker extends BroadcastReceiver {
        public static final int UNKNOWN_LEVEL = -1;

        // current battery status
        int level = UNKNOWN_LEVEL;
        String percentStr;
        int plugType;
        boolean plugged;
        int health;
        int status;
        String technology;
        int voltage;
        int temperature;
        boolean testmode = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                if (testmode && ! intent.getBooleanExtra("testmode", false)) return;

                level = (int)(100f
                        * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                        / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));

                plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                plugged = plugType != 0;
                health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH,
                        BatteryManager.BATTERY_HEALTH_UNKNOWN);
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN);
                technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
                voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);

                setContentDescription(
                        context.getString(R.string.accessibility_battery_level, level));
                postInvalidate();
            } else if (action.equals(ACTION_LEVEL_TEST)) {
                testmode = true;
                post(new Runnable() {
                    int curLevel = 0;
                    int incr = 1;
                    int saveLevel = level;
                    int savePlugged = plugType;
                    Intent dummy = new Intent(Intent.ACTION_BATTERY_CHANGED);
                    @Override
                    public void run() {
                        if (curLevel < 0) {
                            testmode = false;
                            dummy.putExtra("level", saveLevel);
                            dummy.putExtra("plugged", savePlugged);
                            dummy.putExtra("testmode", false);
                        } else {
                            dummy.putExtra("level", curLevel);
                            dummy.putExtra("plugged", incr > 0 ? BatteryManager.BATTERY_PLUGGED_AC : 0);
                            dummy.putExtra("testmode", true);
                        }
                        getContext().sendBroadcast(dummy);

                        if (!testmode) return;

                        curLevel += incr;
                        if (curLevel == 100) {
                            incr *= -1;
                        }
                        postDelayed(this, 200);
                    }
                });
            }
        }
    }

    BatteryTracker mTracker = new BatteryTracker();

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(ACTION_LEVEL_TEST);
        final Intent sticky = getContext().registerReceiver(mTracker, filter);
        if (sticky != null) {
            // preload the battery level
            mTracker.onReceive(getContext(), sticky);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        getContext().unregisterReceiver(mTracker);
    }

    public BatteryPercentView(Context context) {
        this(context, null, 0);
    }

    public BatteryPercentView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryPercentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mResources = context.getResources();
        mBatteryView = StatusBar;
 
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Typeface font = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        mTextPaint.setTypeface(font);
        isBold=false;
        mTextPaint.setTextAlign(Paint.Align.RIGHT);
        mChargeColor = mResources.getColor(R.color.status_bar_clock_color);
        mTextPaint.setColor(mChargeColor);

        updateSettings();
    }
    
    public void setViewType(String Type) {
        mBatteryView = Type;
        updateSettings();
    }    
    

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mHeight = h;
        mWidth = w;
    }

    public void updateSettings(){
            mBatteryStyle = Settings.System.getInt(getContext().getContentResolver(),
                                    Settings.System.STATUS_BAR_BATTERY_STYLE, 0);
        boolean show = (mBatteryStyle == 4);

        setVisibility(show ? View.VISIBLE : View.GONE);
        postInvalidate();
    }
    
    public void hide() {
        setVisibility(View.GONE);    
    }

    @Override
    public void draw(Canvas c) {
        if (mBatteryStyle != 4) return;
        BatteryTracker tracker = mDemoMode ? mDemoTracker : mTracker;
        final int level = tracker.level;

        if (level == BatteryTracker.UNKNOWN_LEVEL) return;

        float drawFrac = (float) level / 100f;
        final int pt = getPaddingTop();
        final int pl = getPaddingLeft();
        final int pr = getPaddingRight();
        final int pb = getPaddingBottom();
        int height = mHeight - pt - pb;
        int width = mWidth -pr;
        if (level>EMPTY && isBold) {
            Typeface font = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
            mTextPaint.setTypeface(font);
            isBold=false;
        } else if (level<=EMPTY && !isBold) {
            Typeface font = Typeface.create("sans-serif-condensed", Typeface.BOLD);
            mTextPaint.setTypeface(font);
            isBold=true;
        }        
        mTextPaint.setTextSize(height * (tracker.level == 100 ? 0.82f : 0.98f));
        mTextHeight = -mTextPaint.getFontMetrics().ascent;
        final String str = String.valueOf(level) +"%";
        final float x = mWidth;
        final float y = (mHeight + mTextHeight) * 0.46f;
        c.drawText(str,
                x,
                y,
                mTextPaint);
    }

    private boolean mDemoMode;
    private BatteryTracker mDemoTracker = new BatteryTracker();

    @Override
    public void dispatchDemoCommand(String command, Bundle args) {
        if (!mDemoMode && command.equals(COMMAND_ENTER)) {
            mDemoMode = true;
            mDemoTracker.level = mTracker.level;
            mDemoTracker.plugged = mTracker.plugged;
        } else if (mDemoMode && command.equals(COMMAND_EXIT)) {
            mDemoMode = false;
            postInvalidate();
        } else if (mDemoMode && command.equals(COMMAND_BATTERY)) {
           String level = args.getString("level");
           String plugged = args.getString("plugged");
           if (level != null) {
               mDemoTracker.level = Math.min(Math.max(Integer.parseInt(level), 0), 100);
           }
           if (plugged != null) {
               mDemoTracker.plugged = Boolean.parseBoolean(plugged);
           }
           postInvalidate();
        }
    }
}

