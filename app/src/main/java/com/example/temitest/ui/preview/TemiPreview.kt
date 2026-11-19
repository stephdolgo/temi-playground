package com.example.temitest.ui.preview

import androidx.compose.ui.tooling.preview.Preview

/**
 * Previews a composable at temi's screen size: 13.3" 1920x1080, landscape.
 *
 * The dpi is an estimate (1920 px across a 293.76 mm wide panel is about 166 ppi,
 * so the nearest Android bucket is mdpi/160). Change it here if a real temi
 * reports a different density.
 */
@Preview(
    name = "temi 13.3\" 1920x1080",
    device = "spec:width=1920px,height=1080px,dpi=160",
    showBackground = true,
)
annotation class TemiPreview
