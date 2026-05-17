package com.github.cvzakharchenko.freddie.mercury

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class MercuryCompletion(
    val requestId: String?,
    val replacementText: String?,
    val rawText: String? = null,
    val statusCode: Int? = null,
    val elapsedMs: Long? = null,
    val responseBody: String? = null,
)

class MercuryClient(
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build(),
) {
    fun requestNextEdit(
        prompt: String,
        apiKey: String,
    ): MercuryCompletion {
        val startedAt = System.nanoTime()
        val requestBody =
            GSON.toJson(
                MercuryEditRequest(
                    messages = listOf(MercuryMessage(role = "user", content = prompt)),
                ),
            )
        val request =
            HttpRequest
                .newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        if (response.statusCode() !in 200..299) {
            throw MercuryApiException(
                statusCode = response.statusCode(),
                message = parseErrorMessage(response.statusCode(), response.body()),
                responseBody = response.body().take(MAX_DEBUG_RESPONSE_CHARS),
            )
        }

        return parseCompletion(response.body(), response.statusCode(), elapsedMs)
    }

    companion object {
        const val ENDPOINT = "https://api.inceptionlabs.ai/v1/edit/completions"
        private const val MAX_DEBUG_RESPONSE_CHARS = 100_000
        private val GSON = Gson()

        fun parseCompletion(body: String): MercuryCompletion = parseCompletion(body, null, null)

        fun parseCompletion(
            body: String,
            statusCode: Int?,
            elapsedMs: Long?,
        ): MercuryCompletion {
            val root = JsonParser.parseString(body).asJsonObject
            val requestId = root.stringOrNull("id")
            val responseBody = body.take(MAX_DEBUG_RESPONSE_CHARS)
            val rawText =
                extractText(root)
                    ?: return MercuryCompletion(
                        requestId = requestId,
                        replacementText = null,
                        statusCode = statusCode,
                        elapsedMs = elapsedMs,
                        responseBody = responseBody,
                    )
            return MercuryCompletion(
                requestId = requestId,
                replacementText = cleanNextEditOutput(rawText),
                rawText = rawText,
                statusCode = statusCode,
                elapsedMs = elapsedMs,
                responseBody = responseBody,
            )
        }

        fun cleanNextEditOutput(raw: String): String? {
            var text = raw

            if (text.startsWith("```\n")) {
                text = text.removePrefix("```\n")
            } else if (text.startsWith("```")) {
                val newline = text.indexOf('\n')
                if (newline >= 0) text = text.substring(newline + 1)
            }

            if (text.endsWith("\n```")) {
                text = text.removeSuffix("\n```")
            } else if (text.endsWith("```")) {
                text = text.removeSuffix("```")
            }

            text = text.replace(CURSOR_TAG, "")

            if (text == "None") return null
            return text
        }

        private fun extractText(root: JsonObject): String? {
            root.stringOrNull("text")?.let { return it }

            val choices = root.getAsJsonArray("choices") ?: return null
            val first = choices.firstOrNull()?.asJsonObject ?: return null
            first.stringOrNull("text")?.let { return it }

            val message = first.getAsJsonObject("message")
            return message?.stringOrNull("content")
        }

        private fun parseErrorMessage(
            statusCode: Int,
            body: String,
        ): String {
            val parsed =
                runCatching {
                    JsonParser.parseString(body)
                        .asJsonObject
                        .getAsJsonObject("error")
                        ?.stringOrNull("message")
                }.getOrNull()
            return parsed ?: "Mercury request failed with HTTP $statusCode: ${body.take(500)}"
        }

        private fun JsonObject.stringOrNull(name: String): String? {
            val value = get(name) ?: return null
            if (value.isJsonNull) return null
            return value.asString
        }

        private const val CURSOR_TAG = "<|cursor|>"
    }
}

class MercuryApiException(
    val statusCode: Int,
    override val message: String,
    val responseBody: String? = null,
) : RuntimeException(message) {
    val shouldNotifyUser: Boolean
        get() = statusCode == 401 || statusCode == 402
}

private data class MercuryEditRequest(
    val model: String = "mercury-edit-2",
    val messages: List<MercuryMessage>,
    val max_tokens: Int = 512,
    val temperature: Double = 0.3,
    val top_p: Double = 0.8,
    val presence_penalty: Double = 1.0,
    val stream: Boolean = false,
)

private data class MercuryMessage(
    val role: String,
    val content: String,
)
