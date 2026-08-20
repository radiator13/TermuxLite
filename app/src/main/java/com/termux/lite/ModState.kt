package com.termux.lite

enum class ModState {
    Off, Once, Locked
}

fun ModState.tap(): ModState = if (this == ModState.Off) ModState.Once else ModState.Off

fun ModState.lock(): ModState = ModState.Locked

fun ModState.consume(): ModState = if (this == ModState.Once) ModState.Off else this

fun ModState.isActive(): Boolean = this != ModState.Off
