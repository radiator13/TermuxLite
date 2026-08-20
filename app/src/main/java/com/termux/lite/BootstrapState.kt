package com.termux.lite

sealed class BootstrapState {
    data object Starting : BootstrapState()
    data class Downloading(val bytes: Long, val total: Long) : BootstrapState()
    data object Extracting : BootstrapState()
    data object SecondStage : BootstrapState()
    data object Ready : BootstrapState()
    data class Error(val message: String) : BootstrapState()
}
