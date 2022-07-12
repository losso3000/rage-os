import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

val iconSearch = listOf(
"1f00", "2080",
"4040", "8320",
"8320", "b020",
"b820", "bc20",
"5c40", "2080",
"1f30", "0038",
"001c", "000e",
"0007", "0002",
)

val iconHmm = listOf(
"0410","0a28",
"1144","2082",
"1004","280a",
"1414","0808",
"1004","2082",
"1144","2aaa",
"1554","0a28",
"%04x".format(1040), "0000"
)

fun main() {
    convertIcon("gfx/icon-search.png", iconSearch)
    convertIcon("gfx/icon-hmm.png", iconHmm)
    toConst("gfx/icon-porn.png")
    toConst("gfx/icon-horse.png")
    toConst("gfx/icon-furry.png")
    toConst("gfx/icon-scat.png")
}

fun toConst(filename: String) {
    val img = ImageIO.read(File(filename))
    for (y in 0 until 16 step 2) {
        println("\t%04x:%04x\t# %s line %2d".format(
            encodeIconStrip(img, y),
            encodeIconStrip(img, y+1),
            filename,
            y)
        )
    }
}

fun encodeIconStrip(img: BufferedImage, y: Int): Int {
    var s = ""
    for (x in 0 until 16) {
        val set = (img.getRGB(x,y) and 0xff) > 0x80
        s += if (set) "1" else "0"
    }
    return s.toInt(2)
}

fun convertIcon(filename: String, icon: List<String>) {
    val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    icon.forEachIndexed { index, s ->
        val value = s.toInt(16)
        val bin = "%16s".format(value.toString(2)).replace(' ', '0')
        println(bin)
        for (x in 0 until 16) {
            val col = if (bin[x] == '0') Color.BLACK else Color.WHITE
            g.color = col
            g.fillRect(x, index, 1, 1)
        }
    }
    ImageIO.write(img, "png", File(filename))
}
