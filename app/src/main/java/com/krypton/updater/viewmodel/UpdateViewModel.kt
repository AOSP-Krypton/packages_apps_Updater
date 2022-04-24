package com.krypton.updater.viewmodel

import android.net.Uri

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.krypton.updater.data.FileCopyStatus
import com.krypton.updater.data.update.UpdateRepository
import com.krypton.updater.data.update.UpdateState

import dagger.hilt.android.lifecycle.HiltViewModel

import javax.inject.Inject

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    val updateState: StateFlow<UpdateState>
        get() = updateRepository.updateState

    val showUpdateUI: Flow<Boolean>
        get() = combine(
            updateRepository.updateState,
            updateRepository.readyForUpdate,
        ) { state, readyForUpdate ->
            readyForUpdate || state !is UpdateState.Idle
        }

    val fileCopyStatus: Channel<FileCopyStatus>
        get() = updateRepository.fileCopyStatus

    val updateFailedReason = Channel<String?>(2, BufferOverflow.DROP_OLDEST)

    init {
        viewModelScope.launch {
            updateRepository.updateState.filterIsInstance<UpdateState.Failed>()
                .collect { updateFailedReason.send(it.exception.localizedMessage) }
        }
    }

    /**
     * Prepare for local upgrade.
     *
     * @param uri the [Uri] of the update zip file.
     */
    fun setupLocalUpgrade(uri: Uri) {
        viewModelScope.launch {
            updateRepository.copyOTAFile(uri)
        }
    }
}