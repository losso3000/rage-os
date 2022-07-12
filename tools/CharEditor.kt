import java.awt.*
import java.awt.event.ActionEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.SwingUtilities

class CharEdPanel: JPanel(BorderLayout()) {
    var long: Long = 0L
        set(value) {
            field = value
            SwingUtilities.invokeLater {
                try {
                    listenerDisabled = true
                    textField.text = "%04x:%04x".format((field shr 16).toInt(), (field and 0xffffL).toInt())
                    textFieldUpdated()
                } finally {
                    listenerDisabled = false
                }
            }
        }
    val checkBoxes = mutableListOf<JToggleButton>()
    val textField: JTextField
    var listenerDisabled = false
    @OptIn(ExperimentalStdlibApi::class)
    fun textFieldUpdated() {
        var intValue = long.toInt()
        try {
            for (col in 0..4) {
                val colData = ((intValue shr 16) and 0b111111)
                var cy = 5
                for (bit in 5 downTo 0) {
                    val set = (colData and (1 shl bit)) != 0
                    val idx = col + cy * 5
                    checkBoxes.getOrNull(idx)?.isSelected = set
                    cy--
                }
                intValue = intValue.rotateRight(6)
            }
        } finally {
        }
    }
    fun checkboxUpdated() {
        if (listenerDisabled) return
        var scan = 0L
        if (checkBoxes[0 + 0 * 5].isSelected) scan = scan or 0b00000000_00000001_00000000_00000000L
        if (checkBoxes[0 + 1 * 5].isSelected) scan = scan or 0b00000000_00000010_00000000_00000000L
        if (checkBoxes[0 + 2 * 5].isSelected) scan = scan or 0b00000000_00000100_00000000_00000000L
        if (checkBoxes[0 + 3 * 5].isSelected) scan = scan or 0b00000000_00001000_00000000_00000000L
        if (checkBoxes[0 + 4 * 5].isSelected) scan = scan or 0b00000000_00010000_00000000_00000000L
        if (checkBoxes[0 + 5 * 5].isSelected) scan = scan or 0b00000000_00100000_00000000_00000000L
        if (checkBoxes[1 + 0 * 5].isSelected) scan = scan or 0b00000000_01000000_00000000_00000000L
        if (checkBoxes[1 + 1 * 5].isSelected) scan = scan or 0b00000000_10000000_00000000_00000000L
        if (checkBoxes[1 + 2 * 5].isSelected) scan = scan or 0b00000001_00000000_00000000_00000000L
        if (checkBoxes[1 + 3 * 5].isSelected) scan = scan or 0b00000010_00000000_00000000_00000000L
        if (checkBoxes[1 + 4 * 5].isSelected) scan = scan or 0b00000100_00000000_00000000_00000000L
        if (checkBoxes[1 + 5 * 5].isSelected) scan = scan or 0b00001000_00000000_00000000_00000000L
        if (checkBoxes[2 + 0 * 5].isSelected) scan = scan or 0b00010000_00000000_00000000_00000000L
        if (checkBoxes[2 + 1 * 5].isSelected) scan = scan or 0b00100000_00000000_00000000_00000000L
        if (checkBoxes[2 + 2 * 5].isSelected) scan = scan or 0b01000000_00000000_00000000_00000000L
        if (checkBoxes[2 + 3 * 5].isSelected) scan = scan or 0b10000000_00000000_00000000_00000000L
        if (checkBoxes[2 + 4 * 5].isSelected) scan = scan or 0b00000000_00000000_00000000_00000001L
        if (checkBoxes[2 + 5 * 5].isSelected) scan = scan or 0b00000000_00000000_00000000_00000010L
        if (checkBoxes[3 + 0 * 5].isSelected) scan = scan or 0b00000000_00000000_00000000_00000100L
        if (checkBoxes[3 + 1 * 5].isSelected) scan = scan or 0b00000000_00000000_00000000_00001000L
        if (checkBoxes[3 + 2 * 5].isSelected) scan = scan or 0b00000000_00000000_00000000_00010000L
        if (checkBoxes[3 + 3 * 5].isSelected) scan = scan or 0b00000000_00000000_00000000_00100000L
        if (checkBoxes[3 + 4 * 5].isSelected) scan = scan or 0b00000000_00000000_00000000_01000000L
        if (checkBoxes[3 + 5 * 5].isSelected) scan = scan or 0b00000000_00000000_00000000_10000000L
        if (checkBoxes[4 + 0 * 5].isSelected) scan = scan or 0b00000000_00000000_00000001_00000000L
        if (checkBoxes[4 + 1 * 5].isSelected) scan = scan or 0b00000000_00000000_00000010_00000000L
        if (checkBoxes[4 + 2 * 5].isSelected) scan = scan or 0b00000000_00000000_00000100_00000000L
        if (checkBoxes[4 + 3 * 5].isSelected) scan = scan or 0b00000000_00000000_00001000_00000000L
        if (checkBoxes[4 + 4 * 5].isSelected) scan = scan or 0b00000000_00000000_00010000_00000000L
        if (checkBoxes[4 + 5 * 5].isSelected) scan = scan or 0b00000000_00000000_00100000_00000000L
        long = scan
    }
    init {
        val top = JPanel(GridLayout(6, 5))
        for (y in 0 until 6) {
            for (x in 0 until 5) {
                val check = JToggleButton()
                check.preferredSize = Dimension(50, 50)
                check.addActionListener { e -> checkboxUpdated() }
                top.add(check)
                checkBoxes += check
            }
        }
        textField = JTextField("0", 12)
        textField.font = Font.decode("Dialog-PLAIN-30")
        val bot = JPanel()
        bot.add(textField)
        add(top, BorderLayout.NORTH)
        add(bot, BorderLayout.CENTER)
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun main() {
    activateNimbus()
    val file = "demo/build/constants.bin"
    val pixelSize = 6
    val constants = readConstants(File(file).readBytes())
    val charButtons = JPanel()
    val buttonToValue = mutableMapOf<JButton, Long>()
    val charEdPanel = CharEdPanel()
    val charSelect: (ActionEvent) -> Unit = { e ->
        val long = buttonToValue[e.source] ?: -1
        charEdPanel.long = long
    }
    constants.forEach { const ->
        val word1 = const.i
        val word2 = const.frac
        val long = (word1.toLong() shl 16) or word2.toLong()
        var value = long.toInt()

        if (value.countOneBits() <= 4 || value.countOneBits() >= 28) return@forEach

        val image = BufferedImage(5*pixelSize, 6*pixelSize, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        for (col in 0..4) {
            val colData = ((value shr 16) and 0b111111)
            var cy = pixelSize*5
            for (bit in 5 downTo 0) {
                val set = (colData and (1 shl bit)) != 0
                if (set) {
                    g2.color = Color.BLACK
                    g2.fillRect(col * pixelSize, cy, pixelSize-1, pixelSize-1)
                }
                cy -= pixelSize
            }
            value = value.rotateRight(6)
        }
        val btn = JButton(ImageIcon(image))
        charButtons.add(btn)
        btn.addActionListener(charSelect)
        buttonToValue[btn] = long
    }
    val main = JPanel(BorderLayout())
    val charScroll = JScrollPane(charButtons)
    charButtons.preferredSize = Dimension(600,6000)
    charScroll.preferredSize = Dimension(640,600)
    main.add(charScroll, BorderLayout.CENTER)
    main.add(charEdPanel, BorderLayout.EAST)
    showInFrame("chars", main)
}