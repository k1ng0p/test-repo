import { Stores } from '@valenced/discord/flux'
import type { DiscordModules } from '@valenced/discord/types'

export type VoiceGlyph = 'speaker' | 'mute' | 'deaf' | 'lock'

// Discord's icon assets, the first one that exists is used
export const GlyphAssets: Record<VoiceGlyph, string[]> = {
	speaker: ['SpeakerIcon', 'VoiceNormalIcon'],
	mute: ['MicrophoneDenyIcon'],
	deaf: ['HeadphonesDenyIcon'],
	lock: ['VoiceLockIcon'],
}

export interface VoiceState {
	channelId?: string | null
	mute?: boolean
	deaf?: boolean
	selfMute?: boolean
	selfDeaf?: boolean
}

export type VoiceStateStore = DiscordModules.Flux.Store<{
	getVoiceStateForUser(userId: string): VoiceState | undefined
	getDiscoverableVoiceStateForUser?(userId: string): VoiceState | undefined
}>

export type ChannelStore = DiscordModules.Flux.Store<{
	getChannel(channelId: string): unknown
}>

export type PermissionStore = DiscordModules.Flux.Store<{
	can(permission: bigint, channel: unknown): boolean
}>

export const Connect = BigInt(1) << BigInt(20)

export function getVoiceGlyph(userId: string): VoiceGlyph | undefined {
	const voiceStates = Stores.VoiceStateStore as VoiceStateStore
	const state =
		voiceStates.getVoiceStateForUser(userId) ??
		voiceStates.getDiscoverableVoiceStateForUser?.(userId)

	if (!state?.channelId) return

	const channel = (Stores.ChannelStore as ChannelStore).getChannel(
		state.channelId,
	)

	if (
		!channel ||
		!(Stores.PermissionStore as PermissionStore).can(Connect, channel)
	)
		return 'lock'

	if (state.deaf || state.selfDeaf) return 'deaf'
	if (state.mute || state.selfMute) return 'mute'
	return 'speaker'
}
