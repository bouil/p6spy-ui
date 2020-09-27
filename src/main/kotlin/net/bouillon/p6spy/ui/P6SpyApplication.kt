package net.bouillon.p6spy.ui

import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationEvent
import org.springframework.context.ConfigurableApplicationContext

class P6SpyApplication : Application() {

    private lateinit var applicationContext: ConfigurableApplicationContext

    override fun init() {
        super.init()
        applicationContext = SpringApplicationBuilder(P6SpyUiConfiguration::class.java).run()
    }

    override fun start(stage: Stage) {
        applicationContext.publishEvent(StageReadyEvent(stage))
    }

    override fun stop() {
        val p6SpySocketClient = applicationContext.getBean(P6SpySocketClient::class.java)
        if (p6SpySocketClient.connected) p6SpySocketClient.disconnect()
        applicationContext.close()
        Platform.exit()
    }
}

class StageReadyEvent(stage: Stage) : ApplicationEvent(stage) {
    val stage: Stage
        get() = source as Stage
}
