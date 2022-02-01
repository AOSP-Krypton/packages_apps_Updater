package com.krypton.updater.viewmodel

import android.net.Uri

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.krypton.updater.data.Event
import com.krypton.updater.data.update.UpdateRepository
import com.krypton.updater.data.update.UpdateState

import dagger.hilt.android.lifecycle.HiltViewModel

import javax.inject.Inject

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    private val _updateState = MutableLiveData<UpdateState>()
    val updateState: LiveData<UpdateState>
        get() = _updateState

    private val _updateFailed = MutableLiveData<Event<String?>>()
    val updateFailed: LiveData<Event<String?>>
        get() = _updateFailed

    private val _updateProgress = MutableLiveData<Int>()
    val updateProgress: LiveData<Int>
        get() = _updateProgress

    private val _copyingFile = MutableLiveData<Boolean>()
    val copyingFile: LiveData<Boolean>
        get() = _copyingFile

    private val _copyFailed = MutableLiveData<Event<String?>>()
    val copyFailed: LiveData<Event<String?>>
        get() = _copyFailed

    private val _readyForUpdate = MutableLiveData<Boolean>()
    val readyForUpdate: LiveData<Boolean>
        get() = _readyForUpdate

    private val _isUpdating = MutableLiveData<Boolean>()
    val isUpdating: LiveData<Boolean>
        get() = _isUpdating

    init {
        viewModelScope.launch {
            for (event in updateRepository.copyingFile) {
                _copyingFile.value = event
            }
        }
        viewModelScope.launch {
            for (result in updateRepository.copyResultChannel) {
                if (result.isFailure) _copyFailed.value = Event(result.exceptionOrNull()?.message)
            }
        }
        viewModelScope.launch {
            updateRepository.updateState.collect {
                _updateState.value = it
                _isUpdating.value = !it.idle
                if (it.failed) _updateFailed.value = Event(it.exception?.message)
            }
        }
        viewModelScope.launch {
            updateRepository.updateProgress.collect {
                _updateProgress.value = it
            }
        }
        viewModelScope.launch {
            updateRepository.readyForUpdate.collect {
                _readyForUpdate.value = it
            }
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