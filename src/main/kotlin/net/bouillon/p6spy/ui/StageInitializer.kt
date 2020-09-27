package net.bouillon.p6spy.ui

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.util.Callback
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationListener
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component

@Component
class StageInitializer : ApplicationListener<StageReadyEvent> {

    @Value("classpath:p6spy.fxml")
    private lateinit var p6spyResource: Resource

    @Value("\${spring.application.title}")
    private lateinit var applicationTitle: String

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var p6SpyController: P6SpyController

    override fun onApplicationEvent(event: StageReadyEvent) {
        val fxmlLoader = FXMLLoader(p6spyResource.url)
        fxmlLoader.controllerFactory = Callback { aClass -> applicationContext.getBean(aClass) }
        val parent: Parent = fxmlLoader.load()

        val stage = event.stage
        stage.title = applicationTitle
        stage.scene = Scene(parent, 1200.0, 800.0)
        stage.show()
    }


}