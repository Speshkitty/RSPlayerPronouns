package com.speshkitty.playerpronouns;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PlayerNotesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PlayerPronounsPlugin.class);
		RuneLite.main(args);
	}
}