package com.vasilispngs.adblock.data

sealed interface SelfUpdate {
    data object Idle : SelfUpdate
    data object Available : SelfUpdate
    data class Downloading(val progress: Float) : SelfUpdate
    data object Installing : SelfUpdate
    data object Failed : SelfUpdate
}
