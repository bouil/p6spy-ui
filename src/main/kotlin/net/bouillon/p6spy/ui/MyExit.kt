package net.bouillon.p6spy.ui

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.shell.ExitRequest
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.commands.Quit

@ShellComponent
open class MyExit : Quit.Command {

    @Autowired
    private lateinit var p6SpyCommands: P6SpyCommands

    @ShellMethod(value = "Exit the shell.", key = ["quit", "exit"])
    open fun quit() {
        if (p6SpyCommands.disconnectAvailability().isAvailable) p6SpyCommands.disconnect()
        System.exit(0);
    }

}