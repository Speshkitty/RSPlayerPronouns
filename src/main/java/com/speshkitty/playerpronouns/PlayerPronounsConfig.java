package com.speshkitty.playerpronouns;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(PlayerPronounsConfig.GROUP)
public interface PlayerPronounsConfig extends Config
{
	String GROUP = "playerpronouns";

	@ConfigItem(
			position = 1,
			keyName = "presetPronoun",
			name = "Pronoun",
			description = "Your pronoun!"
	)
	default Pronoun presetPronoun() {
		return Pronoun.ASK;
	}

	@ConfigItem(
			position = 2,
			keyName = "showChat",
			name = "Show In UI",
			description = "Whether to show people's pronouns when mousing over their name in UI menus."
	)
	default boolean showInChat() {
		return true;
	}

	@ConfigItem(
			position = 3,
			keyName = "showWorld",
			name = "Show In World",
			description = "Whether to show people's pronouns when mousing over them in the world."
	)
	default boolean showInWorld() {
		return true;
	}

	@ConfigItem(
			keyName = "pronoun",
			name = "Custom Pronoun",
			description = "Custom entered pronoun. Please be aware this is manually approved.",
			hidden=true
	)
	default String pronoun() { return ""; }
}
