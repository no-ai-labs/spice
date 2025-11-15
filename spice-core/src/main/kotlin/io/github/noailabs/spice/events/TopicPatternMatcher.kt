package io.github.noailabs.spice.events

/**
 * Utility to match Spice event topics against wildcard patterns.
 *
 * Supports the same semantics across all EventBus implementations:
 * - `*` matches a single segment
 * - `**` matches zero or more segments
 */
internal object TopicPatternMatcher {

    fun matches(topic: String, pattern: String): Boolean {
        if (topic == pattern) return true

        val topicParts = topic.split(".")
        val patternParts = pattern.split(".")

        if ("**" in patternParts) {
            val beforeWildcard = patternParts.takeWhile { it != "**" }
            val afterWildcard = patternParts.dropWhile { it != "**" }.drop(1)

            if (beforeWildcard.isNotEmpty() && topicParts.take(beforeWildcard.size) != beforeWildcard) {
                return false
            }

            if (afterWildcard.isNotEmpty() && topicParts.takeLast(afterWildcard.size) != afterWildcard) {
                return false
            }

            return true
        }

        if (topicParts.size != patternParts.size) return false

        return topicParts.zip(patternParts).all { (topicPart, patternPart) ->
            patternPart == "*" || topicPart == patternPart
        }
    }
}
