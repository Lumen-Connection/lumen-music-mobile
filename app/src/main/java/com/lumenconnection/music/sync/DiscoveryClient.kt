package com.lumenconnection.music.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** Um desktop encontrado na rede. */
data class DiscoveredServer(
    val serverId: String,
    val name: String,
    val host: String,
    val port: Int,
    val appVersion: String,
)

/**
 * Procura o Lumen Music na rede local.
 *
 * Não é mDNS: mandamos um broadcast e o desktop responde unicast (ver
 * `src/sync/discovery_responder.cpp`). Broadcast + resposta direta dispensa
 * `MulticastLock` no Android e evita as esquisitices de multicast no Windows.
 *
 * Redes com isolamento de clientes bloqueiam broadcast — por isso a tela de
 * sync sempre oferece digitar o IP à mão, e isso não é um caminho secundário.
 */
object DiscoveryClient {

    private const val TAG = "DiscoveryClient"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun discover(timeoutMs: Int = 2500): List<DiscoveredServer> =
        withContext(Dispatchers.IO) {
            val found = LinkedHashMap<String, DiscoveredServer>()
            val probe = """{"lumen":"discover","proto":$SYNC_PROTOCOL_VERSION}"""
                .toByteArray(Charsets.UTF_8)

            runCatching {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = 400

                    socket.send(
                        DatagramPacket(
                            probe, probe.size,
                            InetAddress.getByName("255.255.255.255"),
                            SYNC_DISCOVERY_PORT,
                        )
                    )

                    val buffer = ByteArray(1024)
                    val deadline = System.currentTimeMillis() + timeoutMs
                    while (System.currentTimeMillis() < deadline) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                        } catch (_: SocketTimeoutException) {
                            continue // segue esperando até o prazo total
                        }

                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val announce = runCatching {
                            json.decodeFromString<AnnounceDto>(text)
                        }.getOrNull() ?: continue

                        if (announce.lumen != "announce" || announce.serverId.isBlank()) continue

                        found[announce.serverId] = DiscoveredServer(
                            serverId = announce.serverId,
                            name = announce.name.ifBlank { packet.address.hostAddress.orEmpty() },
                            host = packet.address.hostAddress.orEmpty(),
                            port = announce.port,
                            appVersion = announce.appVersion,
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "falha na descoberta", it) }

            found.values.toList()
        }
}
