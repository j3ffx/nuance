package io.github.j3ffx.nuance.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The few camera icons the core icon set lacks, from Google's Material Icons (Apache-2.0,
 * github.com/google/material-design-icons, "materialicons" 24 px), rather than the whole extended set.
 */
object CameraIcons {
    val Pause = icon("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")
    val Play = icon("Play", "M8 5v14l11-7z")
    val FlashOn = icon("FlashOn", "M7 2v11h3v9l7-12h-4l4-8z")
    val FlashOff = icon(
        "FlashOff",
        "M3.27 3L2 4.27l5 5V13h3v9l3.58-6.14L17.73 20 19 18.73 3.27 3zM17 10h-4l4-8H7v2.18l8.46 8.46L17 10z"
    )
    val FlipCamera = icon(
        "FlipCamera",
        "M9,12c0,1.66,1.34,3,3,3s3-1.34,3-3s-1.34-3-3-3S9,10.34,9,12z",
        "M8,10V8H5.09C6.47,5.61,9.05,4,12,4c3.72,0,6.85,2.56,7.74,6h2.06c-0.93-4.56-4.96-8-9.8-8" +
            "C8.73,2,5.82,3.58,4,6.01V4H2v6 H8z",
        "M16,14v2h2.91c-1.38,2.39-3.96,4-6.91,4c-3.72,0-6.85-2.56-7.74-6H2.2c0.93,4.56,4.96,8,9.8,8" +
            "c3.27,0,6.18-1.58,8-4.01V20 h2v-6H16z"
    )
    val Photo = icon(
        "Photo",
        "M9 2L7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9z" +
            "m3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z"
    )
    val Video = icon(
        "Video",
        "M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z"
    )
    val Stop = icon("Stop", "M6 6h12v12H6z")

    /** "More settings" (Material "tune"). */
    val Tune = icon(
        "Tune",
        "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10z" +
            "m-6-4h2V7h4V5h-4V3h-2v6z"
    )

    /** "Original": an eye (Material "visibility"). */
    val Eye = icon(
        "Eye",
        "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5z" +
            "M12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 " +
            "3-1.34 3-3-1.34-3-3-3z"
    )

    /** Correction by lightness: a half-filled disc (Material "contrast"). */
    val Lightness = icon(
        "Lightness",
        "M12,22c5.52,0,10-4.48,10-10S17.52,2,12,2S2,6.48,2,12S6.48,22,12,22z M13,4.07c3.94,0.49,7,3.85,7,7.93" +
            "s-3.05,7.44-7,7.93 V4.07z"
    )

    /** Correction by hue: a palette (Material "palette"). */
    val Hues = icon(
        "Hues",
        "M12,2C6.49,2,2,6.49,2,12s4.49,10,10,10c1.38,0,2.5-1.12,2.5-2.5c0-0.61-0.23-1.2-0.64-1.67" +
            "c-0.08-0.1-0.13-0.21-0.13-0.33 c0-0.28,0.22-0.5,0.5-0.5H16c3.31,0,6-2.69,6-6C22,6.04,17.51,2,12,2z" +
            " M17.5,13c-0.83,0-1.5-0.67-1.5-1.5c0-0.83,0.67-1.5,1.5-1.5 s1.5,0.67,1.5,1.5C19,12.33,18.33,13,17.5,13z" +
            " M14.5,9C13.67,9,13,8.33,13,7.5C13,6.67,13.67,6,14.5,6S16,6.67,16,7.5 C16,8.33,15.33,9,14.5,9z" +
            " M5,11.5C5,10.67,5.67,10,6.5,10S8,10.67,8,11.5C8,12.33,7.33,13,6.5,13S5,12.33,5,11.5z" +
            " M11,7.5 C11,8.33,10.33,9,9.5,9S8,8.33,8,7.5C8,6.67,8.67,6,9.5,6S11,6.67,11,7.5z"
    )

    /** Four views: a 2 × 2 grid (drawn here). */
    val FourViews = icon("FourViews", "M3 3h8v8H3zM13 3h8v8h-8zM3 13h8v8H3zM13 13h8v8h-8z")

    /** Two views: two stacked panes (Material "view_agenda"). */
    val TwoViews = icon(
        "TwoViews",
        "M19,13H5c-1.1,0-2,0.9-2,2v4c0,1.1,0.9,2,2,2h14c1.1,0,2-0.9,2-2v-4C21,13.9,20.1,13,19,13z",
        "M19,3H5C3.9,3,3,3.9,3,5v4c0,1.1,0.9,2,2,2h14c1.1,0,2-0.9,2-2V5C21,3.9,20.1,3,19,3z"
    )

    /** A shutter: a ring around a disc (drawn here, not from Material Icons). */
    val Shutter = icon(
        "Shutter",
        "M12 2a10 10 0 1 0 0.001 0zm0 2a8 8 0 1 1 -0.001 0z",
        "M12 6a6 6 0 1 0 0.001 0z"
    )
    val Gallery = icon(
        "Gallery",
        "M22 16V4c0-1.1-.9-2-2-2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2z" +
            "m-11-4l2.03 2.71L16 11l4 5H8l3-4zM2 6v14c0 1.1.9 2 2 2h14v-2H4V6H2z"
    )

    private fun icon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths) builder.addPath(addPathNodes(d), fill = SolidColor(Color.Black))
        return builder.build()
    }
}
