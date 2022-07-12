import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

fun main() {
    val txt = JTextField(40)
    var lastEventTime = 0L
    val l = object: DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) {
            val now = System.currentTimeMillis()
            val ela = now - lastEventTime
            val frames = ela * 50L / 1000L
            lastEventTime = now
            println("[%4d] %s".format(frames, txt.text))
        }
        override fun removeUpdate(e: DocumentEvent?) {
        }
        override fun changedUpdate(e: DocumentEvent?) {
        }
    }
    txt.document.addDocumentListener(l)
    showInFrame("typing speed", txt)
}