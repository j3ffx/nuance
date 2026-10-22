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
