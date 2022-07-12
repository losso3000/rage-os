import java.io.File

fun main() {
    var lastX = -1
    var lastY = -1
    File("demo/poem-dots.txt").forEachLine { line ->
        if (line.startsWith("#")) {
            println(line)
            println("\t10 :: wait")
            lastX = -1
            lastY = -1
            return@forEachLine
        }
        val x = (extract(line, ".*PT_X\\+(\\d+).*") ?: "0").toInt()
        val y = (extract(line, ".*PT_Y\\+(\\d+).*") ?: "0").toInt() - 16

        var dist = 0.0
        if (lastX != -1) {
            val xd = Math.abs(x-lastX)
            val yd = Math.abs(y-lastY)
            dist = Math.sqrt(1.0 * xd*xd + yd*yd)
        }
        if (dist > 1.0) {
            var moves = 0
            val buf = StringBuilder()
            val dir = Math.atan2(y.toDouble()-lastY.toDouble(), x.toDouble()-lastX.toDouble())
            val roseDir = (512.0 + dir / 2.0 / Math.PI * 256.0) % 256.0
            buf.append("\t%d :: set state.face".format(roseDir.toInt()))
            var len = 1
            while (dist > 1.0) {
                // buf.append(" :: 1 :: move :: draw")
                len++
                moves++
                dist -= 1.0
            }
            buf.append(" :: %d :: 0 :: <drawline_len_speed> :: fork(2) :: %d :: wait".format(len, 2))
            println(buf)
        }

        println("\t%3d :: set state.x :: %3d :: set state.y :: draw :: 1 :: wait".format(x.toInt(), y.toInt()))
        lastX = x
        lastY = y
    }
}