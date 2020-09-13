package net.bouillon.p6spy.ui

import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.shell.jline.PromptProvider
import org.springframework.stereotype.Component

@Component
class CustomPromptProvider : PromptProvider {

    @Autowired
    private lateinit var p6SpyCommands: P6SpyCommands


    override fun getPrompt(): AttributedString {
        return if (p6SpyCommands.connected) {
            coloredText(
                p6SpyCommands.remoteHost+ ":> ",
                AttributedStyle.GREEN
            )
        } else {
            coloredText(
                "not-connected:> ",
                AttributedStyle.RED
            )
        }
    }

    /**
     * @param text: text to print
     * @param color: AttributedStyle.*
     */
    fun coloredText(text: String, color: Int) =
        AttributedString(text, AttributedStyle.DEFAULT.foreground(color))

}