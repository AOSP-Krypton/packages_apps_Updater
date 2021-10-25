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

package com.krypton.updater.ui.fragment;

import static android.content.Context.BIND_AUTO_CREATE;
import static android.os.UserHandle.SYSTEM;
import static android.view.HapticFeedbackConstants.KEYBOARD_PRESS;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.krypton.updater.model.data.ProgressInfo;
import com.krypton.updater.R;
import com.krypton.updater.services.UpdateInstallerService;
import com.krypton.updater.services.UpdateInstallerService.ServiceBinder;
import com.krypton.updater.ui.VisibilityControlInterface;
import com.krypton.updater.util.Utils;
import com.krypton.updater.viewmodel.UpdateViewModel;

public class UpdateProgressFragment extends Fragment implements VisibilityControlInterface {
    private static final String TAG = "UpdateProgressFragment";
    private Context context;
    private UpdateInstallerService service;
    private UpdateViewModel viewModel;
    private View rootView;
    private ProgressBar progressBar;
    private TextView updateStatus, updateStep, progressValue;
    private Button pauseButton, cancelButton;
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            if (!bound) {
                service = ((ServiceBinder) binder).getService();
                bound = true;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "binder died");
            bound = false;
        }
    };

    public UpdateProgressFragment() {
        super(R.layout.progress_fragment_layout);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
        viewModel = new ViewModelProvider(
            requireActivity()).get(UpdateViewModel.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        rootView = view;
        hideSelf(true);
        setWidgets();
        registerObservers();
    }

    @Override
    public void onStart() {
        super.onStart();
        context.bindServiceAsUser(new Intent(context, UpdateInstallerService.class),
            connection, BIND_AUTO_CREATE, SYSTEM);
    }

    @Override
    public void onStop() {
        super.onStop();
        context.unbindService(connection);
        bound = false;
    }

    private void setWidgets() {
        updateStatus = rootView.findViewById(R.id.status);
        updateStep = rootView.findViewById(R.id.extra_data);
        progressBar = rootView.findViewById(R.id.progress);
        progressValue = rootView.findViewById(R.id.progress_value);

        pauseButton = rootView.findViewById(R.id.pause);
        pauseButton.setOnClickListener(v -> {
            v.performHapticFeedback(KEYBOARD_PRESS);
            if (bound) {
                service.pauseUpdate();
            }
        });

        cancelButton = (Button) rootView.findViewById(R.id.cancel);
        cancelButton.setOnClickListener(v -> {
            v.performHapticFeedback(KEYBOARD_PRESS);
            if (bound) {
                service.cancelUpdate();
            }
        });
    }

    private void registerObservers() {
        final LifecycleOwner owner = getViewLifecycleOwner();
        viewModel.getUpdateProgress().observe(owner,
            progressInfo -> updateProgress(progressInfo));

        viewModel.pauseButtonStatus().observe(owner,
            status -> pauseButton.setText(status ? R.string.resume : R.string.pause));

        viewModel.getViewVisibility().observe(owner,
            visible -> hideSelf(!visible));

        viewModel.getControlVisibility().observe(owner,
            visible -> {
                setGroupVisibility(visible, pauseButton, cancelButton);
                rootView.invalidate();
            });
    }

    private void updateProgress(ProgressInfo progressInfo) {
        progressBar.setIndeterminate(progressInfo.getIndeterminate());
        updateStatus.setText(progressInfo.getStatus());
        progressBar.setProgress(progressInfo.getProgress());
        progressValue.setText(progressInfo.getProgress() + "%");
        updateStep.setText(progressInfo.getExtras());
    }

    private void hideSelf(boolean hide) {
        setGroupVisibility(!hide, rootView);
        rootView.invalidate();
    }
}
