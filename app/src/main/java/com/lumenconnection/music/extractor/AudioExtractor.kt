package com.lumenconnection.music.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Sinaliza que o NewPipe não cobre esta URL — o chamador cai para o yt-dlp. */
class ExtractionUnsupportedException(message: String) : Exception(message)

/**
 * Metadados e melhor faixa de áudio de uma URL.
 *
 * Ao contrário do Lumen Stream, aqui só interessa áudio: o Lumen Music é um
 * player de música e o desktop também baixa apenas a trilha sonora.
 */
data class ExtractedAudio(
    val title: String,
    val uploader: String?,
    val durationSec: Long?,
    val audioUrl: String?,
    val audioExt: String,
)

/**
 * Motor primário: NewPipe Extractor. Leve e rápido, mas cobre menos sites que o
 * yt-dlp e não converte formato — quando não dá conta, o chamador tenta o yt-dlp.
 */
object NewPipeEngine {

    suspend fun extract(url: String): ExtractedAudio = withContext(Dispatchers.IO) {
        val service = NewPipe.getServiceByUrl(url)
            ?: throw ExtractionUnsupportedException("Nenhum serviço NewPipe para $url")
        val info = StreamInfo.getInfo(service, url)

        val audio = info.audioStreams.maxByOrNull { it.averageBitrate }
            ?: throw ExtractionUnsupportedException("Sem faixa de áudio em $url")

        ExtractedAudio(
            title = info.name ?: "audio",
            uploader = info.uploaderName,
            durationSec = info.duration.takeIf { it > 0 },
            audioUrl = audio.content,
            audioExt = audio.format?.suffix ?: "m4a",
        )
    }
}
