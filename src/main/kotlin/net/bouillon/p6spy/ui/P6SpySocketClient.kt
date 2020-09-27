package net.bouillon.p6spy.ui

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.*
import javax.annotation.PostConstruct

@Service
open class P6SpySocketClient @Autowired constructor() {

    companion object {
        val log = LoggerFactory.getLogger(P6SpySocketClient::class.java)
    }

//    @PostConstruct
//    fun afterReady() {
//        connect("localhost", 4564)
//    }

    private var spySubscription: Disposable? = null

    private var client: AsynchronousSocketChannel? = null

    private var readingRunnable: ReadingRunnable? = null
    private var readingThread: Thread? = null

    val connected: Boolean
        get() = client?.isOpen() ?: false

    val remoteHost: String
        get() = client?.remoteAddress?.toString() ?: "Not connected"

    fun connect(host: String = "localhost", port: Int = 4564): Flowable<SqlLog>? {
        if (client != null) {
            log.error("Already connected to {}", client?.remoteAddress)
            return null
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
                log.info("Connected to {}:{}", host, port)

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

        readingRunnable = ReadingRunnable(pipedInputStream, spySubject)
        readingThread = Thread(readingRunnable).apply { start() }

        return spyObservable
    }


    val connectable: Boolean
        get() = !connected

    open fun disconnect() {
        readingRunnable?.stop = true
        readingRunnable?.pipedInputStream?.close()
        this.client?.let { client ->
            client.close()
            this.client = null
        }
        spySubscription?.dispose()
        spySubscription = null
        log.info("Disconnected")

        readingThread?.stop()
    }


}

private class ReadingRunnable(val pipedInputStream: PipedInputStream, val spySubject: Subject<SqlLog>) : Runnable {

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