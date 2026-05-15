package com.github.cvzakharchenko.freddie

import com.github.cvzakharchenko.freddie.mercury.MercuryClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MercuryClientParserTest {
    @Test
    fun `parses fenced message content`() {
        val completion =
            MercuryClient.parseCompletion(
                """
                {
                  "id": "request-1",
                  "choices": [
                    {
                      "message": {
                        "content": "```kotlin\nfun main() {}\n```"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )

        assertEquals("request-1", completion.requestId)
        assertEquals("fun main() {}", completion.replacementText)
    }

    @Test
    fun `preserves leading indentation and trailing newline`() {
        val completion =
            MercuryClient.parseCompletion(
                """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "    return value\n"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )

        assertEquals("    return value\n", completion.replacementText)
    }

    @Test
    fun `exact None means no suggestion`() {
        val completion =
            MercuryClient.parseCompletion(
                """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "None"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )

        assertNull(completion.replacementText)
    }
}
