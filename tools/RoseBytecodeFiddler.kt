import java.awt.*
import java.io.*
import java.util.*
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter

fun main() {
    activateNimbus()
    val ui = FiddleUI()
    showInFrame("Rose bytecode fiddler", ui)
}

enum class Pref {
    CURRENT_DIR,
    AUTO_LOAD,
    SAVE_DIR,
    FROM_SCRIPT,
}

data class Spot(val x: Double, val y: Double, val col: Color = Color.BLACK)
data class Line(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

const val TURT_SCALE = 4.0

class TurtleCanvas(
    val proc: Proc,
    var minX: Double = -1.0,
    var minY: Double = -1.0,
    var maxX: Double = 1.0,
    var maxY: Double = 1.0) {
    val lines = mutableListOf<Line>()
    val spots = mutableListOf<Spot>()
    val positions = mutableListOf<Spot>()
    fun visit(x: Double, y: Double) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    fun width() = maxX - minX
    fun height() = maxY - minY
    fun addLine(x1: Double, y1: Double, x2: Double, y2: Double) {
        visit(x1*TURT_SCALE, y1*TURT_SCALE)
        visit(x2*TURT_SCALE, y2*TURT_SCALE)
        lines += Line(x1*TURT_SCALE,y1*TURT_SCALE,x2*TURT_SCALE,y2*TURT_SCALE)
    }
    fun addSpot(x: Double, y: Double, col: Color) {
        visit(x*TURT_SCALE,y*TURT_SCALE)
        spots += Spot(x*TURT_SCALE,y*TURT_SCALE, col)
    }
    fun addPosition(x: Double, y: Double) {
        visit(x*TURT_SCALE,y*TURT_SCALE)
        positions += Spot(x*TURT_SCALE,y*TURT_SCALE)
    }
}

const val SPOT_R = 2

class PreviewTurtle: JPanel() {
    var canvas: TurtleCanvas? = null
    val cols = listOf(Color.RED, Color.GREEN, Color.CYAN, Color.MAGENTA, Color.ORANGE, Color.DARK_GRAY, Color.BLACK, Color.BLUE)
    val bigConsts = mutableListOf<Long>()
    var proc: Proc? = null
        set(value) {
            field = value
            value?.let {
                val turtle = TurtleCanvas(value)
                canvas = turtle
                var dir = 0.0
                var x = 0.0
                var y = 0.0
                var col = cols[0]
                value.lines.forEach { line ->
                    if (line.contains("plot") || line.contains("draw")) {
                        turtle.addSpot(x, y, col)
                    } else {
                        getCommand(line, "x")?.let { x = it }
                        getCommand(line, "y")?.let { y = it }
                        getCommand(line, "face")?.let { dir = it }
                        getCommand(line, "tint")?.let { col = cols[it.toInt() % cols.size] }
                        getCommand(line, "move")?.let { amount ->
                            val rad = dir / 256.0 * Math.PI * 2.0 + Math.PI * 0.5
                            x += Math.sin(rad)*amount
                            y += Math.cos(rad)*amount
                            turtle.addPosition(x, y)
                        }
                    }
                }
                bigConsts.clear()
                value.byteCodes.filter { it.argument is RoseConst }.map { it.argument as RoseConst }.forEach { roseConst ->
                    val word1 = roseConst.i
                    val word2 = roseConst.frac
                    val result = (word1.toLong() shl 16) or word2.toLong()
                    bigConsts += result
                }
            }
            repaint()
        }

    fun getCommand(line: String, s: String): Double? {
        val re = "\\s*$s\\s+(\\-?\\d+(\\.\\d+)?).*".toRegex()
        return re.matchEntire(line)?.let { m -> m.groupValues[1].toDouble() }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        canvas?.let { turtle ->
            val smallestOuter = Math.min(width, height)
            val largestInner = Math.max(turtle.width()*1.1, turtle.height()*1.1)
            var scale = smallestOuter / largestInner
            // g2.drawString("fit ${turtle.width()}*${turtle.height()} in $width*$height -> scale $scale", 10, 10)
            val olTransform = g2.transform
            g2.scale(scale, scale)
            g2.translate(-turtle.minX, -turtle.minY)

            g2.color = Color.GRAY
            turtle.positions.forEach { g2.drawRect(it.x.toInt()-1, it.y.toInt()-1, 2, 2) }

            turtle.spots.forEach { g2.color = it.col; g2.fillArc((it.x-SPOT_R).toInt(), (it.y-SPOT_R).toInt(), (SPOT_R*2).toInt(), (SPOT_R*2).toInt(), 0, 360) }

            g2.transform = olTransform
            g2.color = Color.BLACK
            var constDumpX = 2
            var constDumpY = 2
            val pixelSize = 8
            bigConsts.forEach { long ->
                // 5*6 character, rotated (5 columns of width 6)
                var value = long.toInt()
                var cx = constDumpX
                for (col in 0..4) {
                    val colData = ((value shr 16) and 0b111111)
                    var cy = constDumpY + pixelSize*6
                    for (bit in 5 downTo 0) {
                        val set = (colData and (1 shl bit)) != 0
                        g2.color = if (set) Color.BLACK else Color.WHITE
                        g2.fillRect(cx, cy, pixelSize-1, pixelSize-1)
                        cy -= pixelSize
                    }
                    cx += pixelSize
                    value = value.rotateRight(6)
                }
                constDumpX += pixelSize*6
                if (constDumpX + pixelSize*7 > width) {
                    constDumpX = 2
                    constDumpY += pixelSize*7
                }
            }
        }
    }
}

class FiddleUI : JPanel(BorderLayout()) {
    val procTable: JTable
    val procTableModel: DefaultTableModel
    val constTable: JTable
    val constTableModel: DefaultTableModel
    val directoryName: JTextField
    val outDirectoryName: JTextField
    val scriptName: JTextField
    val procDecompileInfo: JTextArea
    var procDecompileProc: Proc? = null
    var decompileState: DecompileState? = null
    var previewTurtle: PreviewTurtle
    init {
        procTableModel = object : DefaultTableModel() {
            override fun getColumnClass(columnIndex: Int): Class<*> {
                return when (columnIndex) {
                    1 -> Int.javaClass
                    else -> String.javaClass
                }
            }
        }
        procTableModel.addColumn("#")
        procTableModel.addColumn("proc")
        procTableModel.addColumn("size")
        procTableModel.addColumn("draws")
        procTableModel.addColumn("plots")
        procTableModel.addColumn("moves")
        procTable = JTable(procTableModel)
        procTable.getColumn("#").preferredWidth = 40
        procTable.getColumn("proc").preferredWidth = 280
        procTable.getColumn("size").preferredWidth = 60
        val sorter = TableRowSorter(procTableModel)
        sorter.setComparator(0, Comparator<Int> { a, b -> a - b })
        sorter.setComparator(2, Comparator<Int> { a, b -> a - b })
        sorter.setComparator(3, Comparator<Int> { a, b -> a - b })
        sorter.setComparator(4, Comparator<Int> { a, b -> a - b })
        sorter.setComparator(5, Comparator<Int> { a, b -> a - b })
        procTable.rowSorter = sorter

        constTableModel = object : DefaultTableModel() {
            override fun getColumnClass(columnIndex: Int): Class<*> {
                return when (columnIndex) {
                    0 -> Int.javaClass
                    else -> String.javaClass
                }
            }
        }
        constTableModel.addColumn("#")
        constTableModel.addColumn("bytes")
        constTableModel.addColumn("value")
        constTable = JTable(constTableModel)
        constTable.getColumn("#").preferredWidth = 40
        constTable.getColumn("bytes").preferredWidth = 100
        constTable.getColumn("value").preferredWidth = 100
        val csorter = TableRowSorter(constTableModel)
        csorter.setComparator(0, Comparator<Int> { a, b -> a - b })
        constTable.rowSorter = csorter

        directoryName = JTextField(getPref(Pref.CURRENT_DIR, "dumps"), 20)
        val loadButton = JButton("load")
        val loadFromVisButton = JButton("load visualizer output")
        val autoLoad = JCheckBox("auto-load", getBooleanPref(Pref.AUTO_LOAD, false))
        outDirectoryName = JTextField(getPref(Pref.SAVE_DIR, "demo/build"), 20)
        val saveButton = JButton("save")

        scriptName = JTextField(getPref(Pref.FROM_SCRIPT, "dumps/test-script.txt"), 20)
        val readFromScript = JButton("read from script")
        val writeToScript = JButton("write to script")

        val filePanel = JPanel()
        filePanel.add(directoryName)
        filePanel.add(loadButton)
        filePanel.add(loadFromVisButton)
        filePanel.add(autoLoad)
        filePanel.add(JSeparator(JSeparator.HORIZONTAL))
        filePanel.add(JLabel("output dir:"))
        filePanel.add(outDirectoryName)
        filePanel.add(saveButton)
        filePanel.add(JSeparator(JSeparator.HORIZONTAL))
        filePanel.add(scriptName)
        filePanel.add(readFromScript)
        filePanel.add(writeToScript)

        procDecompileInfo = JTextArea(40, 80)
        procDecompileInfo.font = Font(Font.MONOSPACED, Font.PLAIN, 12)

        add(filePanel, BorderLayout.NORTH)

        val splitR = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(procTable), JScrollPane(procDecompileInfo))
        val splitL = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(constTable), splitR)

        add(splitL, BorderLayout.CENTER)

        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // Preview

        previewTurtle = PreviewTurtle()
        previewTurtle.preferredSize = Dimension(500, 500)
        showInFrame("preview turtle", previewTurtle).setLocation(0, 0)

        // Actions

        loadFromVisButton.addActionListener { loadFrom("Rose/visualizer") }
        loadButton.addActionListener { loadFrom(directoryName.text) }
        saveButton.addActionListener {
            Thread {
                try {
                    val state = decompileState
                    require(state != null) { "no decompiled script in memory!" }
                    val path = File(outDirectoryName.text)
                    val fileCols = File("$path/colorscript.bin")
                    val fileBytes = File("$path/bytecodes.bin")
                    val fileConsts = File("$path/constants.bin")
                    FileOutputStream(fileCols).use { state.writeColorscript(it) }
                    FileOutputStream(fileBytes).use { state.writeBytecodes(it) }
                    FileOutputStream(fileConsts).use { state.writeConstants(it) }
                    JOptionPane.showMessageDialog(
                        this,
                        "Wrote\n$fileCols\n$fileBytes\n$fileConsts",
                        "Saved",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                    JOptionPane.showMessageDialog(
                        this,
                        "Error saving files in ${outDirectoryName.text}: $e",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }.start()
        }
        writeToScript.addActionListener {
            val filename = scriptName.text
            var constsFilename = filename.replace(".txt", ".constants.txt")
            if (constsFilename == filename) constsFilename = filename + ".constants.txt"
            val state = decompileState
            require(state != null) { "no decompiled script in memory!" }
            try {
                PrintWriter(filename).use { w ->
                    PrintWriter(constsFilename).use { constW ->
                        writeScript(state, w, constW)
                    }
                }
                JOptionPane.showMessageDialog(this, "Wrote $filename\n$constsFilename", "Saved", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                JOptionPane.showMessageDialog(
                    this,
                    "Error saving script to $filename: $e",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
        readFromScript.addActionListener {
            val filename = scriptName.text
            val constantsFilename = filename.replace(".txt", ".constants.txt")
            try {
                val colorBytes = mutableListOf<Int>()
                val constBytes = mutableListOf<Int>()
                val scriptBytes = mutableListOf<Int>()
                val procNameMapping = mutableMapOf<String, String>()
                val lines = mutableListOf<String>()
                if (File(constantsFilename).exists()) BufferedReader(InputStreamReader(FileInputStream(constantsFilename))).useLines{ lines.addAll(it) }
                BufferedReader(InputStreamReader(FileInputStream(filename))).useLines{ lines.addAll(it) }
                loadScript(
                    lines,
                    colorBytes,
                    constBytes,
                    scriptBytes,
                    procNameMapping
                )
                val renamer: (String) -> String = { s ->
                    var repl = s
                    procNameMapping.forEach { k, v -> repl = repl.replace(k, v) }
                    repl
                }
                loadFrom(
                    colorBytes.map { it.toByte() }.toByteArray(),
                    scriptBytes.map { it.toByte() }.toByteArray(),
                    constBytes.map { it.toByte() }.toByteArray(),
                    renamer
                )
                setPref(Pref.FROM_SCRIPT, filename)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                JOptionPane.showMessageDialog(
                    this,
                    "Error loading script $filename: $e",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
        procTable.selectionModel.addListSelectionListener { e ->
            val state = decompileState
            if (state == null) {
                procDecompileInfo.text = ""
            } else {
                val sel = procTable.selectionModel.minSelectionIndex
                if (sel >= 0) {
                    val rowNum = procTable.rowSorter.convertRowIndexToModel(sel)
                    val index = procTableModel.getValueAt(rowNum, 0) as Int
                    procDecompileProc = state.procs[index]
                    procDecompileInfo.text = secondPassDecompile(state.procs[index]).map { it.asSourceCode() }.joinToString("\n")
                    // procDecompileInfo.text = state.procs[index].byteCodes.map { format(it) }.joinToString("\n")
                    procDecompileInfo.caretPosition = 0
                    previewTurtle.proc = procDecompileProc
                }
            }
        }
    }
    private fun loadFrom(path: String) {
        val colors       = File("$path/colorscript.bin").readBytes()
        val bytecodes    = File("$path/bytecodes.bin").readBytes()
        val constantsBin = File("$path/constants.bin").readBytes()
        val mappingFile  = File("$path/bytecodes.mapping.txt")
        val procNameMapping =  mutableMapOf<String, String>()
        if (mappingFile.exists()) {
            val props = Properties()
            FileInputStream(mappingFile).use { props.load(it) }
            props.forEach { k, v -> procNameMapping[k.toString()] = v.toString() }
        }
        val renamer: (String) -> String = { s ->
            var repl = s
            procNameMapping.forEach { k, v -> repl = repl.replace(k, v) }
            repl
        }
        procNameMapping.forEach { k, v -> println("rename [$k] to [$v]") }
        loadFrom(colors, bytecodes, constantsBin, renamer)
    }
    private fun loadFrom(colors: ByteArray, bytecodes: ByteArray, constantsBin: ByteArray, renamer: (String) -> String = { it }) {
        Thread {
            try {
                SwingUtilities.invokeAndWait {
                    procTable.isEnabled = false
                    constTable.isEnabled = false
                    procTableModel.setNumRows(0)
                    constTableModel.setNumRows(0)
                }
                val constants = readConstants(constantsBin)
                val colorscript = decompileColorscript(colors)
                val state = DecompileState(bytecodes, constants, colorscript, renamer)
                decompileBytecode(state)
                decompileColorscript(colors)
                updateProcTable(state)
                updateConstTable(state)
                decompileState = state
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                JOptionPane.showMessageDialog(this, "Error loading ${directoryName.text}: $e", "Error", JOptionPane.ERROR_MESSAGE)
            } finally {
                SwingUtilities.invokeAndWait {
                    procTable.isEnabled = true
                    constTable.isEnabled = true
                }
            }
        }.start()
    }

    private fun updateProcTable(state: DecompileState) {
        procTableModel.setNumRows(0)
        state.procs.forEachIndexed { index, proc ->
            procTableModel.addRow(arrayOf(index, proc.name, proc.byteCodes.size, proc.drawCount(), proc.plotCount(), proc.moveCount()))
        }
    }

    private fun updateConstTable(state: DecompileState) {
        constTableModel.setNumRows(0)
        state.constants.forEachIndexed() { index, const ->
            constTableModel.addRow(arrayOf(index, "%04x.%04x".format(const.i, const.frac), const.representation))
        }
    }

    private fun toBin(const: RoseConst): String {
        val value = "%16s".format(const.i.toLong().toString(2)) + "%16s".format(const.frac.toLong().toString(2))
        require(value.length == 32) { "wat ${value.length} <$value>" }
        return buildString {
            value.forEach { append(if (it == '1') '■' else '□') }
        }
    }
}
