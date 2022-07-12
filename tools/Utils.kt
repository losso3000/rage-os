import java.util.prefs.Preferences
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.UIManager

fun activateNimbus() {
    UIManager.getInstalledLookAndFeels().first { "Nimbus".equals(it.name) }?.let { UIManager.setLookAndFeel(it.className) }
}

fun getBooleanPref(key: Any, defaultValue: Boolean): Boolean {
    return getPref(key, defaultValue.toString()).toBoolean()
}

fun getIntPref(key: Any, defaultValue: Int): Int {
    return getPref(key, defaultValue.toString()).toInt()
}

fun getPref(key: Any, defaultValue: String): String = Preferences.userRoot().get(toPrefKey(key), defaultValue)

fun setPref(key: Any, value: String) = Preferences.userRoot().put(toPrefKey(key), value)

fun toPrefKey(key: Any): String = "rosefiddle.$key"

fun showInFrame(title: String, comp : JComponent): JFrame {
    var frame = JFrame(title)
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.contentPane = comp
    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.isVisible = true
    return frame
}
