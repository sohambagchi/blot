package app.blot.markdown

import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.parser.Parser
import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.InlineParserState
import org.commonmark.parser.beta.ParsedInline
import org.commonmark.parser.beta.Scanner
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.AbstractBlockParserFactory
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState

/** Inline maths: `$…$`, or `$$…$$` within a paragraph, which shows on its own line ([display]). */
class MathInline(val latex: String, val display: Boolean) : CustomNode()

/** Display maths: a block that starts with `$$` and ends with `$$`, on one line or several. */
class MathBlock : CustomBlock() {
    /** The LaTeX between the `$$`s, trimmed; complete once the block is parsed. */
    var latex: String = ""
        internal set
}

/**
 * TeX maths in markdown, as Pandoc's `tex_math_dollars` reads it: `$x$` when the opening `$`
 * isn't followed by whitespace, the closing `$` isn't preceded by whitespace or followed by a
 * digit (so "costs $5 and $6" stays text), and `$$…$$` for display maths. A `\$` is a dollar
 * sign, outside maths and in it. Code spans and code blocks are parsed first and keep their dollars.
 */
class MathExtension private constructor() : Parser.ParserExtension {
    override fun extend(builder: Parser.Builder) {
        builder.customInlineContentParserFactory(InlineFactory)
        builder.customBlockParserFactory(BlockFactory())
    }

    private object InlineFactory : InlineContentParserFactory {
        override fun getTriggerCharacters(): Set<Char> = setOf('$')
        override fun create(): InlineContentParser = InlineParser
    }

    private object InlineParser : InlineContentParser {
        override fun tryParse(state: InlineParserState): ParsedInline? {
            val s = state.scanner()
            return when (s.matchMultiple('$')) {
                1 -> single(s)
                2 -> double(s)
                else -> null
            } ?: ParsedInline.none()
        }

        private fun single(s: Scanner): ParsedInline? {
            val first = s.peek()
            if (first == Scanner.END || first.isWhitespace()) return null
            val start = s.position()
            while (s.find('$') >= 0) {
                val before = s.peekPreviousCodePoint()
                val end = s.position()
                s.next()
                if (before == '\\'.code || Character.isWhitespace(before)) continue
                if (s.peek() == '$') {
                    s.matchMultiple('$')
                    continue
                }
                if (s.peek() in '0'..'9') continue
                val latex = s.getSource(start, end).content
                return ParsedInline.of(MathInline(latex, display = false), s.position())
            }
            return null
        }

        private fun double(s: Scanner): ParsedInline? {
            val start = s.position()
            while (s.find('$') >= 0) {
                val before = s.peekPreviousCodePoint()
                val end = s.position()
                if (before == '\\'.code) {
                    s.next()
                    continue
                }
                if (!s.next("$$")) {
                    s.next()
                    continue
                }
                val latex = s.getSource(start, end).content.trim()
                if (latex.isEmpty()) return null
                return ParsedInline.of(MathInline(latex, display = true), s.position())
            }
            return null
        }
    }

    private class BlockFactory : AbstractBlockParserFactory() {
        override fun tryStart(state: ParserState, matched: MatchedBlockParser): BlockStart? {
            if (state.indent >= 4) return BlockStart.none()
            val line = state.line.content
            val i = state.nextNonSpaceIndex
            if (!line.startsWith("$$", i)) return BlockStart.none()
            val rest = line.subSequence(i + 2, line.length).toString()
            val close = closing(rest)
            val parser = if (close >= 0) {
                // A one-line block must end at the closing `$$`; otherwise it is inline maths in a paragraph.
                if (rest.substring(close + 2).isNotBlank()) return BlockStart.none()
                val latex = rest.substring(0, close)
                if (latex.isBlank()) return BlockStart.none()
                MathBlockParser(latex, closed = true)
            } else {
                MathBlockParser(rest, closed = false)
            }
            return BlockStart.of(parser).atIndex(line.length)
        }
    }

    private class MathBlockParser(first: String, private var closed: Boolean) : AbstractBlockParser() {
        private val lines = StringBuilder(first)
        private val block = MathBlock()

        override fun getBlock(): MathBlock = block

        override fun tryContinue(state: ParserState): BlockContinue? {
            if (closed) return BlockContinue.none()
            val line = state.line.content
            val content = line.toString()
            val close = closing(content)
            if (close >= 0) {
                lines.append('\n').append(content, 0, close)
                closed = true
                return BlockContinue.finished()
            }
            lines.append('\n').append(content)
            return BlockContinue.atIndex(line.length)
        }

        override fun closeBlock() {
            block.latex = lines.toString().trim()
        }
    }

    companion object {
        fun create(): MathExtension = MathExtension()

        /** Index of the first unescaped `$$` in [s], or -1. */
        private fun closing(s: String): Int {
            var i = s.indexOf("$$")
            while (i > 0 && s[i - 1] == '\\') i = s.indexOf("$$", i + 1)
            return i
        }
    }
}
