package com.audiops.app

/**
 * One speaker of a transcript with speakers: the [name] given to them (null while they are
 * "Speaker N"), the first thing they said ([sample], to tell who is who), and whether their voice
 * was long enough to measure ([hasVoice]), so it can be remembered.
 */
data class SpeakerInfo(val name: String?, val sample: String, val hasVoice: Boolean)
