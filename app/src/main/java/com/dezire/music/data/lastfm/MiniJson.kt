package com.dezire.music.data.lastfm

/*
 * Minimal, dependency-free JSON reader used for Last.fm responses.
 *
 * The Android platform ships org.json, but relying on it would make the
 * parsing logic impossible to unit test on the JVM (the android.jar stubs
 * throw). Keeping a tiny, self-contained parser lets every mapping and
 * failure path be covered by plain JUnit tests and avoids adding a network
 * or JSON library to the build.
 *
 * Supported value types: Map<String, Any?>, List<Any?>, String, Double,
 * Boolean and null.
 */
class JsonParseException(message: String) : Exception(message)

object MiniJson {

    fun parse(text: String): Any? {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.isAtEnd()) {
            throw JsonParseException("Unexpected trailing content at position ${parser.position}")
        }
        return value
    }

    private class Parser(private val text: String) {
        var position = 0

        fun isAtEnd(): Boolean = position >= text.length

        fun skipWhitespace() {
            while (!isAtEnd() && text[position].isWhitespace()) position++
        }

        fun parseValue(): Any? {
            if (isAtEnd()) throw JsonParseException("Unexpected end of JSON input")
            return when (val c = text[position]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> {
                    if (c == '-' || c in '0'..'9') parseNumber()
                    else throw JsonParseException("Unexpected character '$c' at position $position")
                }
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return result
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') {
                    throw JsonParseException("Expected object key at position $position")
                }
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                result[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return result
                    }
                    else -> throw JsonParseException("Expected ',' or '}' at position $position")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return result
            }
            while (true) {
                skipWhitespace()
                result.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return result
                    }
                    else -> throw JsonParseException("Expected ',' or ']' at position $position")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                if (isAtEnd()) throw JsonParseException("Unterminated string")
                when (val c = text[position++]) {
                    '"' -> return builder.toString()
                    '\\' -> builder.append(parseEscape())
                    else -> {
                        if (c.code < 0x20) {
                            throw JsonParseException("Unescaped control character at position $position")
                        }
                        builder.append(c)
                    }
                }
            }
        }

        private fun parseEscape(): Char {
            if (isAtEnd()) throw JsonParseException("Unterminated escape sequence")
            return when (val c = text[position++]) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> parseUnicodeEscape()
                else -> throw JsonParseException("Invalid escape '\\$c'")
            }
        }

        private fun parseUnicodeEscape(): Char {
            if (position + 4 > text.length) {
                throw JsonParseException("Truncated unicode escape")
            }
            val hex = text.substring(position, position + 4)
            position += 4
            return hex.toIntOrNull(16)?.toChar()
                ?: throw JsonParseException("Invalid unicode escape '\\u$hex'")
        }

        private fun parseNumber(): Double {
            val start = position
            if (peek() == '-') position++
            while (!isAtEnd() && (text[position] in '0'..'9')) position++
            if (peek() == '.') {
                position++
                while (!isAtEnd() && (text[position] in '0'..'9')) position++
            }
            if (peek() == 'e' || peek() == 'E') {
                position++
                if (peek() == '+' || peek() == '-') position++
                while (!isAtEnd() && (text[position] in '0'..'9')) position++
            }
            val raw = text.substring(start, position)
            return raw.toDoubleOrNull()
                ?: throw JsonParseException("Invalid number '$raw' at position $start")
        }

        private fun parseLiteral(literal: String, value: Any?): Any? {
            if (position + literal.length > text.length ||
                text.substring(position, position + literal.length) != literal
            ) {
                throw JsonParseException("Invalid literal at position $position")
            }
            position += literal.length
            return value
        }

        private fun expect(expected: Char) {
            if (isAtEnd() || text[position] != expected) {
                throw JsonParseException("Expected '$expected' at position $position")
            }
            position++
        }

        private fun peek(): Char? = if (isAtEnd()) null else text[position]
    }
}

/*
 * Safe accessors for the untyped tree returned by [MiniJson.parse].
 */
@Suppress("UNCHECKED_CAST")
fun Any?.asJsonObject(): Map<String, Any?> =
    this as? Map<String, Any?> ?: emptyMap()

@Suppress("UNCHECKED_CAST")
fun Any?.asJsonArray(): List<Any?> =
    this as? List<Any?> ?: emptyList()

fun Any?.asJsonString(): String? = when (this) {
    is String -> this
    is Number -> if (this is Double && isNaN()) null else toString()
    is Boolean -> toString()
    else -> null
}

fun Any?.asJsonLong(): Long? = when (this) {
    is Number -> toLong()
    is String -> trim().toLongOrNull()
    else -> null
}

fun Any?.asJsonInt(): Int? = asJsonLong()?.toInt()

fun Any?.asJsonBoolean(): Boolean? = when (this) {
    is Boolean -> this
    is String -> when (trim().lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
    else -> null
}
