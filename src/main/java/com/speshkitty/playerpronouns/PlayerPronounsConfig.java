package com.speshkitty.playerpronouns;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(PlayerPronounsConfig.GROUP)
public interface PlayerPronounsConfig extends Config
{
	String GROUP = "playerpronouns";

	@ConfigItem(
		keyName = "pronoun",
		name = "Pronoun",
		description = "Your pronoun!"
	)
	default String pronoun() { return ""; }
}
