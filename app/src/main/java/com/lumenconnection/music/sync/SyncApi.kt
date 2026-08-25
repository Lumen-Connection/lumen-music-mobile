package com.lumenconnection.music.sync

import com.lumenconnection.music.extractor.NewPipeDownloaderImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** O aparelho não está mais autorizado: o usuário revogou o pareamento no PC. */
class SyncUnauthorizedException : IOException("token revogado")

/** Versões de protocolo incompatíveis entre celular e desktop. */
class SyncProtocolMismatchException : IOException("protocolo incompatível")

/**
 * Cliente HTTP do sync. Fala com o servidor do desktop (`src/sync/`), que serve
 * na porta 45150 por padrão.
 */
class SyncApi(private val host: String, private val port: Int, private val token: String?) {

    private val json = Json { ignoreUnknownKeys = true }

    // Reusa o cliente OkHttp do app; o timeout de leitura é maior porque
    // serializar uma biblioteca grande leva um instante no desktop.
    private val client = NewPipeDownloaderImpl.instance.client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun url(path: String) = "http://$host:$port$path"

    private fun Request.Builder.withToken(): Request.Builder =
        if (token.isNullOrBlank()) this else header("X-Lumen-Token", token)

    private fun checkStatus(response: Response) {
        when (response.code) {
            401 -> throw SyncUnauthorizedException()
            409 -> throw SyncProtocolMismatchException()
        }
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
    }

    suspend fun ping(): PingDto = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url("/v1/ping")).build()).execute().use { response ->
            checkStatus(response)
            val dto = json.decodeFromString<PingDto>(response.body?.string().orEmpty())
            if (dto.proto != SYNC_PROTOCOL_VERSION) throw SyncProtocolMismatchException()
            dto
        }
    }

    suspend fun pair(pin: String, deviceId: String, deviceName: String): PairResponseDto =
        withContext(Dispatchers.IO) {
            val payload = json.encodeToString(PairRequestDto(pin, deviceId, deviceName))
            val request = Request.Builder()
                .url(url("/v1/pair"))
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                // 403 aqui significa PIN errado ou expirado, não falta de permissão.
                if (response.code == 403) throw IOException("bad_pin")
                checkStatus(response)
                json.decodeFromString(response.body?.string().orEmpty())
            }
        }

    suspend fun library(): SnapshotDto = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url("/v1/library")).withToken().build()
        client.newCall(request).execute().use { response ->
            checkStatus(response)
            val snapshot = json.decodeFromString<SnapshotDto>(response.body?.string().orEmpty())
            if (snapshot.proto != SYNC_PROTOCOL_VERSION) throw SyncProtocolMismatchException()
            snapshot
        }
    }

    suspend fun push(payload: PushDto): PushResponseDto = withContext(Dispatchers.IO) {
        val body = json.encodeToString(payload)
        val request = Request.Builder()
            .url(url("/v1/push"))
            .withToken()
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            checkStatus(response)
            json.decodeFromString(response.body?.string().orEmpty())
        }
    }

    /**
     * Baixa a faixa para [destination], retomando de onde parou.
     *
     * Grava num `.part` e só renomeia depois de conferir o tamanho: um arquivo
     * truncado na biblioteca seria pior que nenhum.
     */
    suspend fun downloadTrack(
        trackId: Long,
        destination: File,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Boolean = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val partial = File(destination.absolutePath + ".part")
        val already = if (partial.exists()) partial.length() else 0L

        // Já temos o arquivo inteiro de uma tentativa anterior.
        if (expectedSize > 0 && already == expectedSize) {
            partial.renameTo(destination)
            return@withContext true
        }

        val builder = Request.Builder()
            .url(url("/v1/tracks/$trackId/file"))
            .withToken()
        if (already > 0) builder.header("Range", "bytes=$already-")

        client.newCall(builder.build()).execute().use { response ->
            checkStatus(response)

            // 200 numa retomada significa que o servidor ignorou o Range e vai
            // mandar tudo de novo — o parcial tem de ir fora.
            val appending = response.code == 206
            if (!appending && already > 0) partial.delete()

            val total = expectedSize.takeIf { it > 0 }
                ?: ((response.header("X-Lumen-Size")?.toLongOrNull()) ?: 0L)

            val source = response.body?.byteStream() ?: throw IOException("resposta sem corpo")
            java.io.FileOutputStream(partial, appending).use { output ->
                val buffer = ByteArray(64 * 1024)
                var written = if (appending) already else 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    written += read
                    onProgress(written, total)
                }
            }
        }

        if (expectedSize > 0 && partial.length() != expectedSize) {
            // Download incompleto: mantém o `.part` para a próxima tentativa
            // retomar em vez de recomeçar.
            return@withContext false
        }

        if (destination.exists()) destination.delete()
        partial.renameTo(destination)
    }

    suspend fun playlistCover(playlistId: Long, destination: File): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url("/v1/playlists/$playlistId/cover"))
                .withToken()
                .build()
            client.newCall(request).execute().use { response ->
                // 404 é normal: a playlist usa gradiente, não imagem.
                if (response.code == 404) return@withContext false
                checkStatus(response)
                destination.parentFile?.mkdirs()
                response.body?.byteStream()?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext false
                true
            }
        }
}
