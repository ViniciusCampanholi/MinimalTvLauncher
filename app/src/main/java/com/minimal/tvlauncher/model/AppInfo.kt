package com.minimal.tvlauncher.model

import androidx.compose.ui.graphics.ImageBitmap

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val banner: ImageBitmap?,
    val isSelected: Boolean
)
