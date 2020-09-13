package net.bouillon.p6spy.ui

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.jline.terminal.Terminal
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.shell.Availability
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.*
import javax.annotation.PostConstruct

@ShellComponent
open class P6SpyCommands @Autowired constructor() {

    @Autowired
    @Lazy
    private lateinit var terminal: Terminal

    @PostConstruct
    fun afterReady() {
        connect("localhost", 4564)
    }

    private var spySubscription: Disposable? = null

    private var data: MutableList<SqlLog> = mutableListOf()

    private var client: AsynchronousSocketChannel? = null

    private var readingThread: ReadingThread? = null

    private var truncate: Int? = 100

    val connected: Boolean
        get() = client?.isOpen()?:false

    val remoteHost: String
        get() = client?.remoteAddress?.toString() ?: "Not connected"

    @ShellMethod("Connect to application")
    fun connect(
        @ShellOption(defaultValue = "localhost") host: String,
        @ShellOption(defaultValue = "4564") port: Int
    ) {

        if (client != null) {
            terminal.writer().println("Already connected to ${client?.remoteAddress}")
            return
        }

        val spySubject: Subject<SqlLog> = PublishSubject.create()
        val spyObservable: Flowable<SqlLog> =
            spySubject.toFlowable(BackpressureStrategy.BUFFER).observeOn(Schedulers.io())

        val pipedOutputStream = PipedOutputStream()
        val pipedInputStream = PipedInputStream(pipedOutputStream)

        val client = AsynchronousSocketChannel.open()
        this.client = client
        client.connect(InetSocketAddress(host, port), null, object : CompletionHandler<Void?, Nothing?> {
            override fun completed(result: Void?, attachment: Nothing?) {
                terminal.writer().println("Connected to $host:$port")

                val byteBuffer = ByteBuffer.allocate(1024)
                client.read(byteBuffer, null, object : CompletionHandler<Int, Any?> {
                    override fun completed(length: Int?, attachment: Any?) {
                        try {
                            byteBuffer.flip()
                            if (length != null && length > 0) {
                                val byteArray = ByteArray(length)
                                byteBuffer.get(byteArray)
                                pipedOutputStream.write(byteArray)
                            }
                            client.read(byteBuffer, null, this)
                        } catch (e: Exception) {
                            this.failed(e, null)
                        }
                    }

                    override fun failed(exc: Throwable?, attachment: Any?) {
                        spySubject.onComplete()
                    }

                })

            }

            override fun failed(exc: Throwable, attachment: Nothing?) {
                exc.printStackTrace()
                spySubject.onComplete()
            }
        })

        readingThread = ReadingThread(pipedInputStream, spySubject).apply { Thread(this).start() }

        spySubscription = spyObservable.subscribe(
            { sqlLog ->
                if (data.size > 100000) {
                    terminal.writer().println("SQL data too big ${data.size}. Purge forced.")
                    purge()
                }
                data.add(sqlLog)
            },
            { error ->
                error.printStackTrace()
                disconnect()
            },
            {
                terminal.writer().println("End of stream")
                disconnect()
            }
        )
    }


    open fun connectAvailability(): Availability {
        return if (!connected) Availability.available() else Availability.unavailable("you are already connected to ${client?.remoteAddress}")
    }

    @ShellMethod("Truncate sql command")
    open fun truncate(@ShellOption(defaultValue = "100") length: Int) {
        this.truncate = length
    }

    @ShellMethod("Untrucate")
    open fun untruncate() {
        this.truncate = null
    }


    @ShellMethod("Disconnect to application")
    open fun disconnect() {
        readingThread?.stop = true
        this.client?.let { client ->
            client.close()
            this.client = null
        }
        spySubscription?.dispose()
        spySubscription = null
        terminal.writer().println("Disconnected")
        terminal.writer().flush()
    }

    open fun disconnectAvailability(): Availability {
        return if (connected) Availability.available() else Availability.unavailable("you are not connected")
    }

    @ShellMethod("Clear SQL data")
    open fun purge() {
        terminal.writer().println("Clear SQL data")
        data = mutableListOf()
    }

    @ShellMethod("Statistics on SQL prepared statements ")
    open fun stats(): String {
        return computeStats(data.groupBy { it -> it.prepared })
    }

    @ShellMethod("Statistics on SQL effective statements")
    open fun statsEffective(): String {
        return computeStats(data.toList().groupBy { it -> it.sql })
    }

    private fun computeStats(dataGroup: Map<String?, List<SqlLog>>): String {
        val sb = StringBuilder()
        sb.append("Count".padStart(6) + " times | ")
        sb.append("Time".padStart(6) + " ms | ")
        sb.append("Average".padStart(8) + " ms | ")
        sb.append("Max".padStart(6) + " ms | ")
        sb.append("SQL")
        sb.appendLine()
        sb.appendLine("-----------------------------------------------------------------------------------------")

        dataGroup.forEach { (sql, v) ->
            val count = v.count()
            val timeArray = v.map { it.elapsed }.toLongArray()
            sb.append(count.toString().padStart(6) + " times | ")
            sb.append(timeArray.sum().toString().padStart(6) + " ms | ")
            sb.append(timeArray.average().format(0).padStart(8) + " ms | ")
            sb.append((timeArray.maxOrNull() ?: "NaN").toString().padStart(6) + " ms | ")
            sb.append(truncate?.let { sql?.abbreviateString(it) } ?: sql)
            sb.appendLine()
        }
        sb.appendLine("-----------------------------------------------------------------------------------------")
        val timeArray = data.map { it.elapsed }.toLongArray()
        sb.append(data.count().toString().padStart(6) + " times | ")
        sb.append(timeArray.sum().toString().padStart(6) + " ms | ")
        sb.append(timeArray.average().format(0).padStart(8) + " ms | ")
        sb.append((timeArray.maxOrNull() ?: "NaN").toString().padStart(6) + " ms | ")
        sb.appendLine("${dataGroup.count()} distinct SQL statements")

        return sb.toString()
    }

    fun Double?.format(digits: Int) = "%.${digits}f".format(this)

    open fun String.abbreviateString(maxLength: Int): String? {
        return if (this.length <= maxLength) this else this.substring(0, maxLength - 1) + "\u2026"
    }

}

private class ReadingThread(val pipedInputStream: PipedInputStream, val spySubject: Subject<SqlLog>) : Runnable {

    val objectMapper = ObjectMapper()
    val objectReader: ObjectReader = objectMapper.readerFor(SqlLog::class.java)

    var stop: Boolean = false

    override fun run() {
        val scanner = Scanner(pipedInputStream)
        while (!stop && scanner.hasNextLine()) {
            val line = scanner.nextLine()
            val sqlLog = objectReader.readValue<SqlLog>(line)
            spySubject.onNext(sqlLog)
        }
    }


}