package ai.nunchi.mate.ui

import android.graphics.text.LineBreakConfig
import android.os.Build
import android.widget.TextView

fun TextView.breakByPhrase(): TextView {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        lineBreakWordStyle = LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
    }
    return this
}
