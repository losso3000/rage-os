import java.io.File
import javax.imageio.ImageIO

fun main() {
    val img = ImageIO.read(File("sfx/langer.png"))
    var printed = 0
    for (x in 0 until img.width) {
        var amp = 15
        for (y in 0 until 16) {
            val rgb = img.getRGB(x, y)
            if (rgb and 0xff > 50) break
            amp--
        }
        if (amp < 0) amp = 0
        print("%x".format(amp))
        printed++
        if (printed == 4) print (':')
        if (printed == 8) {
            println();
            printed = 0
        }
    }
}