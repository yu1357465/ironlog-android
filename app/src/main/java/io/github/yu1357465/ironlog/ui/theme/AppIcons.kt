package io.github.yu1357465.ironlog.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * App 图标（斜放的六角哑铃）的单色剪影，用于底部「训练」标签等处，随主题着色。
 * 几何与启动图标、ironlog-web 的 AppMark 一致。
 */
object AppIcons {

    /** 选中态：实心。 */
    val Mark: ImageVector by lazy { build("AppMark", outlined = false) }

    /** 未选中态：哑铃头部镂空。 */
    val MarkOutlined: ImageVector by lazy { build("AppMarkOutlined", outlined = true) }

    // 先按水平摆放画在 24×24 里（两个切角的哑铃头 + 握杆），整体再旋转 −45°
    private fun PathBuilder.head(x0: Float, x1: Float) {
        val c = 1.4f
        moveTo(x0 + c, 7f); lineTo(x1 - c, 7f); lineTo(x1, 8.4f); lineTo(x1, 15.6f)
        lineTo(x1 - c, 17f); lineTo(x0 + c, 17f); lineTo(x0, 15.6f); lineTo(x0, 8.4f); close()
    }

    private fun PathBuilder.hole(x0: Float) {
        moveTo(x0, 9.8f); lineTo(x0 + 2.6f, 9.8f); lineTo(x0 + 2.6f, 14.2f); lineTo(x0, 14.2f); close()
    }

    private fun build(name: String, outlined: Boolean) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        group(rotate = -45f, pivotX = 12f, pivotY = 12f) {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                head(2.5f, 7.5f)
                head(16.5f, 21.5f)
                if (outlined) {
                    hole(3.7f)
                    hole(17.7f)
                }
                // 握杆
                moveTo(7.5f, 10.8f); lineTo(16.5f, 10.8f); lineTo(16.5f, 13.2f); lineTo(7.5f, 13.2f); close()
            }
        }
    }.build()
}
