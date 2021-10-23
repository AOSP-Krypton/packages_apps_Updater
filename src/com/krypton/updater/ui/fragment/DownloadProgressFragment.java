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

import static android.view.HapticFeedbackConstants.KEYBOARD_PRESS;

import android.content.Context;
import android.os.Bundle;
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
import com.krypton.updater.util.Utils;
import com.krypton.updater.viewmodel.DownloadViewModel;

public class DownloadProgressFragment extends Fragment {

    private DownloadViewModel viewModel;
    private View rootView;
    private ProgressBar progressBar;
    private TextView downloadStatus, downloadSize, progressValue;
    private Button pauseButton, cancelButton;

    public DownloadProgressFragment() {
        super(R.layout.progress_fragment_layout);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        viewModel = new ViewModelProvider(
            requireActivity()).get(DownloadViewModel.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        rootView = view;
        hideSelf(true);
        setWidgets();
        registerObservers();
    }

    private void setWidgets() {
        downloadStatus = rootView.findViewById(R.id.status);
        downloadSize = rootView.findViewById(R.id.extra_data);
        progressBar = rootView.findViewById(R.id.progress);
        progressValue = rootView.findViewById(R.id.progress_value);

        pauseButton = rootView.findViewById(R.id.pause);
        pauseButton.setOnClickListener(v -> {
            v.performHapticFeedback(KEYBOARD_PRESS);
            viewModel.pauseDownload();
        });

        cancelButton = rootView.findViewById(R.id.cancel);
        cancelButton.setOnClickListener(v -> {
            v.performHapticFeedback(KEYBOARD_PRESS);
            viewModel.cancelDownload();
        });
    }

    private void registerObservers() {
        final LifecycleOwner owner = getViewLifecycleOwner();
        viewModel.getDownloadProgress().observe(owner,
            progressInfo -> updateProgress(progressInfo));

        viewModel.pauseButtonStatus().observe(owner,
            status -> pauseButton.setText(
                status ? R.string.resume : R.string.pause));

        viewModel.getViewVisibility().observe(owner,
            visible -> hideSelf(!visible));

        viewModel.getControlVisibility().observe(owner,
            visible -> {
                Utils.setVisible(visible, pauseButton, cancelButton);
                rootView.invalidate();
            });
    }

    private void updateProgress(ProgressInfo progressInfo) {
        progressBar.setIndeterminate(progressInfo.isIndeterminate());
        downloadStatus.setText(progressInfo.getStatus());
        progressBar.setProgress(progressInfo.getProgress());
        progressValue.setText(progressInfo.getProgress() + "%");
        downloadSize.setText(progressInfo.getExtras());
    }

    private void hideSelf(boolean hide) {
        Utils.setVisible(!hide, rootView);
        rootView.invalidate();
    }
}
