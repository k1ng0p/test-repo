import { callNativeMethodSync } from '@valenced/modules/native'

/** An icon as the name of its drawable resource, and its ARGB color. An empty list shows none. */
export type VoiceBadge = [resource: string, color: number] | []

/** Message ID to the badge native shows next to its author. */
export type VoiceBadges = Record<string, VoiceBadge>

/** Synchronous so the badges are stored before the rows that show them reach native. */
export function putVoiceBadges(badges: VoiceBadges) {
	callNativeMethodSync('k1ngop.voice.put', [JSON.stringify(badges)])
}

export function clearVoiceBadges() {
	callNativeMethodSync('k1ngop.voice.clear', [])
}

declare module '@valenced/modules/native' {
	interface NativeMethods {
		'k1ngop.voice.put': [[json: string], null]
		'k1ngop.voice.clear': [[], null]
	}
}
