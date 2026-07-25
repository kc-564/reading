package com.example.reader.feature.clickzone

import com.example.reader.ui.theme.ClickZoneAction
import com.example.reader.ui.theme.ClickZoneConfig

/**
 * Resolves reader tap zones to actions (E01).
 *
 * The reader surface is divided into five zones (top / bottom / left / right / center).
 * The active action for each zone is configured by [ClickZoneConfig].
 */
object ClickZoneHandler {

    /** The five tap zones on the reader surface. */
    enum class TapZone { TOP, BOTTOM, LEFT, RIGHT, CENTER }

    fun resolve(zone: TapZone, config: ClickZoneConfig): ClickZoneAction = when (zone) {
        TapZone.TOP -> config.top
        TapZone.BOTTOM -> config.bottom
        TapZone.LEFT -> config.left
        TapZone.RIGHT -> config.right
        TapZone.CENTER -> config.center
    }

    /**
     * Computes the tap zone from normalized tap coordinates (0..1 within the surface).
     *
     * Layout: top 15% / bottom 15% bands; the middle band is split into left 33% / center
     * 34% / right 33%.
     */
    fun zoneFromOffset(xFrac: Float, yFrac: Float): TapZone {
        return when {
            yFrac < 0.15f -> TapZone.TOP
            yFrac > 0.85f -> TapZone.BOTTOM
            xFrac < 0.33f -> TapZone.LEFT
            xFrac > 0.67f -> TapZone.RIGHT
            else -> TapZone.CENTER
        }
    }
}
