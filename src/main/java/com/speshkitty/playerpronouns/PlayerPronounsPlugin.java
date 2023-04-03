package com.speshkitty.playerpronouns;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import java.time.temporal.ChronoUnit;

@Slf4j
@PluginDescriptor(
	name = "Player Pronouns"
)
public class PlayerPronounsPlugin extends Plugin
{
	@Inject	private Client client;
	@Inject	private PlayerPronounsOverlay overlay;
	@Inject	private OverlayManager overlayManager;
	@Inject	private PlayerPronounsConfig config;
	@Inject	private TooltipManager tooltipManager;
	@Inject private ConfigManager configManager;
	@Inject	private DatabaseAPI databaseAPI;
	@Inject private	ClientThread thread;

	private String playerNameHashed = "";

	protected String getPlayerNameHashed() {
		if(playerNameHashed.isEmpty())
		{
			if(client.getLocalPlayer() == null) {
				return "";
			}
			if(client.getLocalPlayer().getName() == null){
				return "";
			}
			playerNameHashed = databaseAPI.hashString(client.getLocalPlayer().getName());
		}

		return playerNameHashed;
	}

	final int maxPronounLength = 25;

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
		if(client.getGameState() == GameState.LOGGED_IN){
			getPlayerNameHashed();
		}
		log.info("Player Pronouns started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		log.info("Player Pronouns stopped!");
	}

	@Provides
	PlayerPronounsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PlayerPronounsConfig.class);
	}

	private boolean logging_in = false;

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if(gameStateChanged.getGameState() == GameState.LOGGING_IN) {
			logging_in = true;
		}
		//our last state was logging in
		if(logging_in && gameStateChanged.getGameState() == GameState.LOGGED_IN){
			logging_in = false;
			thread.invokeLater(
				() -> {
					if(client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) {
						return false;
					}
					playerNameHashed = databaseAPI.hashString(client.getLocalPlayer().getName());
					if(playerNameHashed.isEmpty()) {
						return false;
					}
					else
					{
						if(!config.pronoun().isEmpty()) {
							databaseAPI.putPlayersPronoun(config.pronoun());
						}
					}
					return true;
				}
			);
		}
	}

	boolean shouldUpdateConfig = true;

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if(!shouldUpdateConfig) {
			shouldUpdateConfig = true;
			return;
		}
		if (configChanged.getGroup().equals(PlayerPronounsConfig.GROUP))
		{
			if(configChanged.getKey().equalsIgnoreCase("pronoun"))
			{
				if(configChanged.getNewValue().length() > maxPronounLength) {
					shouldUpdateConfig = false;
					configManager.setConfiguration(
							PlayerPronounsConfig.GROUP,
							"pronoun",
							configChanged.getOldValue());
				}
				else {
					databaseAPI.putPlayersPronoun(configChanged.getOldValue());
				}
			}
		}
	}

	@Schedule(
			period = 5,
			unit = ChronoUnit.SECONDS,
			asynchronous = true
	)
	public void lookupData(){
		databaseAPI.cleanUpData();
		databaseAPI.getData();
	}
}
