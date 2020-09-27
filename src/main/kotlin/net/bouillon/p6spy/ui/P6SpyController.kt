package net.bouillon.p6spy.ui

import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.Disposable
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

@Component
class P6SpyController {

    companion object {
        val log = LoggerFactory.getLogger(P6SpyController::class.java)
    }

    @Autowired
    lateinit var p6SpySocketClient: P6SpySocketClient

    @FXML
    lateinit var host: TextField

    @FXML
    lateinit var port: TextField

    @FXML
    lateinit var connectButton: Button

    @FXML
    lateinit var disconnectButton: Button

    @FXML
    lateinit var sqlPrepared: TextArea

    @FXML
    lateinit var sqlEffective: TextArea

    @FXML
    lateinit var tableViewPrepared: TableView<SqlLogDataModel>

    @FXML
    lateinit var tableViewEffective: TableView<SqlLog>

    @FXML
    lateinit var globalCount: Label

    @FXML
    lateinit var globalCountDistinct: Label

    @FXML
    lateinit var globalAverage: Label

    @FXML
    lateinit var globalDuration: Label

    @FXML
    lateinit var globalMax: Label

    private var spySubscription: Disposable? = null

    private var data: MutableList<SqlLog> = mutableListOf()

    @FXML
    fun initialize() {
        initButtons()
        tableViewPrepared.selectionModel.selectedItemProperty().addListener { observable, oldValue, newValue ->
            if (newValue != null) {
                sqlPrepared.text = newValue.sql
            } else {
                sqlPrepared.text = ""
            }
        }
        tableViewEffective.selectionModel.selectedItemProperty().addListener { observable, oldValue, newValue ->
            if (newValue != null) {
                sqlEffective.text = newValue.sql
            } else {
                sqlEffective.text = ""
            }
        }

    }

    fun initButtons() {
        connectButton.isDisable = p6SpySocketClient.connected;
        disconnectButton.isDisable = !p6SpySocketClient.connected
    }

    fun onDisconnectButtonAction(event: ActionEvent) {
        disconnect()
    }

    fun onPurgeButtonAction(event: ActionEvent) {
        purge()
    }

    fun onConnectButtonAction(event: ActionEvent) {
        initButtons()
        val spyObservable = getSpyObservable() ?: return
        spySubscription = spyObservable
            .filter { it.category == "statement" }
            .subscribe(
                { sqlLog ->
                    data.add(sqlLog)
                    addEffectiveItem(sqlLog)
                    recalculateStats()
                },
                { error ->
                    error.printStackTrace()
                    disconnect()
                },
                {
                    log.error("End of stream")
                    disconnect()
                }
            )

    }

    private fun getSpyObservable(): Flowable<SqlLog>? {
        try {
            connectButton.isDisable = true;
            disconnectButton.isDisable = true
            return if (!host.text.isNullOrEmpty() && !port.text.isNullOrEmpty()) {
                val host = host.text
                val port = port.text
                p6SpySocketClient.connect(host, port.toInt())
            } else {
                p6SpySocketClient.connect()
            }
        } finally {
            initButtons()
        }
    }

    private fun disconnect() {
        spySubscription?.dispose()
        p6SpySocketClient.disconnect()
        initButtons()
    }

    /** Clear SQL data */
    private fun purge() {
        log.info("Clear SQL data")
        data = mutableListOf()
        recalculateStats()
        Platform.runLater {
            tableViewEffective.items.clear()
        }
    }

    fun addEffectiveItem(sqlLog: SqlLog) {
        // https://www.youtube.com/watch?v=n3K5D_Kk9FU
        Platform.runLater {
            tableViewEffective.items.add(sqlLog)
        }
    }

    fun recalculateStats() {

        val dataGroup = data.groupBy { it -> it.prepared }
        val sqlLogDataModels = dataGroup.map { (sql, v) ->
            val count = v.count()
            val timeArray = v.map { it.elapsed }.toLongArray()
            val totalTime = timeArray.sum()
            val averageTime = timeArray.average()
            val maxTime = timeArray.maxOrNull() ?: 0

            val sqlLogDataModel = SqlLogDataModel(
                count,
                totalTime,
                averageTime.roundToLong(),
                maxTime,
                sql ?: ""
            )
            sqlLogDataModel
        }

        Platform.runLater {
            tableViewPrepared.items.clear()
            tableViewPrepared.items.addAll(sqlLogDataModels)

            val timeArray = data.map { it.elapsed }.toLongArray()
            globalCount.text = "${data.count()} SQL queries"
            globalCountDistinct.text = "${dataGroup.size} distinct SQL prepared statements"
            globalDuration.text = "${timeArray.sum()} ms"
            globalAverage.text = "${timeArray.average().toInt()} ms"
            globalMax.text = (timeArray.maxOrNull()?.toInt()?.toString() ?: "-") + " ms"

        }
    }


}

data class SqlLogDataModel(
    val count: Int,
    val time: Long,
    val average: Long,
    val max: Long,
    val sql: String
)