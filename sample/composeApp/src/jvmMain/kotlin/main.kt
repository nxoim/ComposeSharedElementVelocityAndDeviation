
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nxoim.sewithvelocity.App
import com.nxoim.sewithvelocity.ui.theme.SampleTheme
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState()

    Window(
        title = "sample",
        state = windowState,
        onCloseRequest = ::exitApplication,
    ) {
        window.minimumSize = Dimension(350, 600)

        SampleTheme {
            Surface {
                App()
            }
        }
    }
}