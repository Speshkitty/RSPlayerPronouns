package com.speshkitty.playerpronouns;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

import java.util.ArrayList;
import java.util.List;

public class PlayerNotesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PlayerPronounsPlugin.class);
		List<String> arguments = new ArrayList<>();

		arguments.add("--profile");
		arguments.add("test");
		arguments.add("--debug");
		arguments.add("--developer-mode");

		String[] arrayArgs = arguments.stream().toArray(String[]::new);

		System.out.println("args: " + String.join(" ", arrayArgs));


		RuneLite.main(arrayArgs);
	}
}