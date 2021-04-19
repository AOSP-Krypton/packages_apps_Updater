/*
 * Copyright (C) 2021 AOSP-Krypton Project
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

package com.krypton.updater.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.MotionEventCompat;

import com.krypton.updater.NetworkInterface;
import com.krypton.updater.R;
import com.krypton.updater.Utils;

import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class UpdaterActivity extends AppCompatActivity {

    private static final String TAG = "UpdaterActivity";
    private static final int RETRY_INTERVAL = 1000;
    private static final int RETRY_COUNT = 5;
    private ExecutorService mExecutor;
    private Handler mHandler;
    private AnimatedVectorDrawable mAnimatedDrawable;
    private NetworkInterface mInterface;
    private GestureDetectorCompat mDetector;
    private TextView viewLatestBuild;
    private TextView latestBuildVersion;
    private TextView latestBuildTimestamp;
    private TextView latestBuildName;
    private ImageView refreshIcon;
    private Button downloadButton;
    private boolean isRunning = false;
    private boolean hasUpdatedUI = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.setTheme(getSharedPreferences(Utils.SHARED_PREFS,
            Context.MODE_PRIVATE).getInt(Utils.THEME_KEY, 2));
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.updater_app_title);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.updater_activity);
        mDetector = new GestureDetectorCompat(this, new GestureListener());
        mInterface = new NetworkInterface();
        mExecutor = Executors.newFixedThreadPool(2);
        mHandler = new Handler(Looper.getMainLooper());
        setBuildInfo();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateBuildInfo();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        mDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                float velocityX, float velocityY) {
            float diffX = event2.getRawX() - event1.getRawX();
            float diffY = event2.getRawY() - event1.getRawY();
            if (Math.abs(diffX) < 100 && diffY > 300) {
                updateBuildInfo();
            }
            return true;
        }
    }

    private void setBuildInfo() {
        ((TextView) findViewById(R.id.view_device))
            .setText(getString(R.string.device_name_text, Utils.getDevice()));
        ((TextView) findViewById(R.id.view_version))
            .setText(getString(R.string.version_text, Utils.getVersion()));
        ((TextView) findViewById(R.id.view_timestamp))
            .setText(getString(R.string.timestamp_text, Utils.getTimestamp()));
        viewLatestBuild = (TextView) findViewById(R.id.view_latest_build);
        latestBuildVersion = (TextView) findViewById(R.id.view_latest_build_version);
        latestBuildTimestamp = (TextView) findViewById(R.id.view_latest_build_timestamp);
        latestBuildName = (TextView) findViewById(R.id.view_latest_build_filename);
        refreshIcon = (ImageView) findViewById(R.id.refresh_icon);
        mAnimatedDrawable = (AnimatedVectorDrawable) refreshIcon.getDrawable();
        downloadButton = (Button) findViewById(R.id.download_button);
    }

    private void updateBuildInfo() {
        if (hasUpdatedUI) {
            resetAndShowRefreshView();
            isRunning = true;
        } else if (!isRunning) {
            isRunning = true;
        } else {
            return;
        }
        mExecutor.execute(() -> tryFetchAndShowToasts());
        mExecutor.execute(() -> {
            while (!mInterface.hasUpdatedBuildInfo() && isRunning) {
                if (!mAnimatedDrawable.isRunning()) {
                    mHandler.post(() -> mAnimatedDrawable.start());
                }
            }
            mHandler.post(() -> {
                refreshIcon.setVisibility(View.GONE);
                if (mInterface.hasUpdatedBuildInfo() && mInterface.hasFoundNewBuild()) {
                    setNewBuildInfo();
                } else if (!mInterface.hasUpdatedBuildInfo()) {
                    viewLatestBuild.setText(getString(R.string.unable_to_fetch_details));
                } else {
                    viewLatestBuild.setText(getString(R.string.latest_build_text));
                }
                hasUpdatedUI = true;
                isRunning = false;
            });
        });
    }

    private void setNewBuildInfo() {
        viewLatestBuild.setText(getString(R.string.new_build_text));

        latestBuildVersion.setText(getString(R.string.version_text, Utils.buildInfo.getVersion()));
        latestBuildVersion.setVisibility(View.VISIBLE);

        latestBuildTimestamp.setText(getString(R.string.timestamp_text, Utils.buildInfo.getTimestamp()));
        latestBuildTimestamp.setVisibility(View.VISIBLE);

        latestBuildName.setText(getString(R.string.filename_text, Utils.buildInfo.getFileName()));
        latestBuildName.setVisibility(View.VISIBLE);

        downloadButton.setVisibility(View.VISIBLE);
    }

    private void resetAndShowRefreshView() {
        hasUpdatedUI = false;
        latestBuildVersion.setVisibility(View.GONE);
        latestBuildTimestamp.setVisibility(View.GONE);
        latestBuildName.setVisibility(View.GONE);
        downloadButton.setVisibility(View.GONE);

        viewLatestBuild.setText(getString(R.string.fetching_build_status_text));
        refreshIcon.setVisibility(View.VISIBLE);
    }

    private String getString(int id, String str) {
        return getString(id) + " " + str;
    }

    private void tryFetchAndShowToasts() {
        boolean fetched = false;
        for (int i = 0; i < RETRY_COUNT; i++) {
            try {
                mInterface.fetchBuildInfo();
                fetched = true;
            } catch (UnknownHostException e) {
                sleepThread(RETRY_INTERVAL);
            } catch (Exception e) {
                Log.d(TAG, "caught exception: ", e);
                break;
            }
            if (fetched) {
                break;
            }
        }
        if (!fetched) {
            isRunning = false;
            mHandler.post(() -> Toast.makeText(this,
                    getString(R.string.check_internet), Toast.LENGTH_SHORT).show());
        }
    }

    private void sleepThread(int duration) {
        try {
            Thread.sleep(duration);
        } catch (Exception e) {}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.option_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
