package dz.wave

import dz.wave.api.waveModule
import dz.wave.config.AppConfig
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

fun main() {
    val config = AppConfig.fromEnv()
    val container = AppContainer(config)
    val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    container.start(background)

    val server = embeddedServer(
        Netty,
        configure = {
            connector {
                host = "0.0.0.0"
                port = config.port
            }
            connector {
                host = "0.0.0.0"
                port = config.managementPort
            }
            // Netty event loops stay small; request handling runs on the call group.
            connectionGroupSize = 2
            workerGroupSize = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            callGroupSize = Runtime.getRuntime().availableProcessors() * 4
            shutdownGracePeriod = 5_000
            shutdownTimeout = 20_000
            requestReadTimeoutSeconds = 30
            responseWriteTimeoutSeconds = 30
            tcpKeepAlive = true
        },
    ) {
        waveModule(container)
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            background.cancel()
            container.close()
        },
    )
    server.start(wait = true)
}
