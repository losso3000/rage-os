import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

data class ColorSet(val tint: Int, val color: Color)

fun main() {
    var h = 0
    File("demo/horse.constants.txt").forEachLine { line ->
        // ffd7		# wait 41
        // 0333		# 0:333
        if (line.matches(".*# wait (\\d+)".toRegex())) {
            extract(line, ".*wait (\\d+)")?.let { h += it.toInt() }
        }
    }
    val colorW = 10
    val colors = arrayOf(
        Color.BLACK, // 0
        Color.BLACK, // 1
        Color.BLACK, // 2
        Color.BLACK, // 3
        Color.BLACK, // 4
        Color.BLACK, // 5
        Color.BLACK, // 6
        Color.BLACK, // 7
    )
    println("color script len: $h")
    val img = BufferedImage(10 * colors.size, h, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    var y = 0
    File("demo/horse.constants.txt").forEachLine { line ->
        // ffd7		# wait 41
        // 0333		# 0:333
        if (line.matches(".*# wait (\\d+)".toRegex())) {
            extract(line, ".*wait (\\d+)")?.let { w ->
                val to = y + w.toInt()
                while (y < to) {
                    drawColor(g, y, colors, colorW)
                    y++
                }
            }
        } else if (line.matches(".*# (\\d):([0-9a-f]{3})".toRegex())) {
            val tint = extract(line, ".*# (\\d):.*")?.toInt() ?: throw IllegalArgumentException("tint? $line")
            val rgb = extract(line, ".*# \\d:([0-9a-f]{3})") ?: throw IllegalArgumentException("rgb? $line")
            val hexString = arrayOf('#', rgb[0], rgb[0], rgb[1], rgb[1], rgb[2], rgb[2]).joinToString("")
            colors[tint] = Color.decode(hexString)
        }
    }
    ImageIO.write(img, "png", File("demo/horse.colorscript.png"))
}

fun drawColor(g: Graphics2D, y: Int, colors: Array<Color>, colorW: Int) {
    colors.forEachIndexed { index, color ->
        g.color = color
        g.drawLine(index * colorW, y, index * colorW + colorW, y)
    }
}
