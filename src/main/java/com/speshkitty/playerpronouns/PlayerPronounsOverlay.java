package com.speshkitty.playerpronouns;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PlayerPronounsOverlay extends Overlay {
    private final PlayerPronounsPlugin playerPronounsPlugin;
    private final PlayerPronounsConfig config;
    private final Client client;
    private final TooltipManager tooltipManager;

    @Inject
    private DatabaseAPI databaseAPI;

    @Inject
    PlayerPronounsOverlay(PlayerPronounsPlugin playerPronounsPlugin, PlayerPronounsConfig config, Client client, TooltipManager tooltipManager)
    {
        this.playerPronounsPlugin = playerPronounsPlugin;
        this.config = config;

        this.client = client;
        this.tooltipManager = tooltipManager;

        setPosition(OverlayPosition.TOOLTIP);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
    }

    @Override
    public Dimension render(Graphics2D graphics) {

        if (client.isMenuOpen())
        {
            return null;
        }

        List<Player> checkedPlayers = new ArrayList<>();

        MenuEntry[] list = client.getMenuEntries();

        if(config.showInChat()) {
            for (MenuEntry entry : list) {
                switch (entry.getOption()) {

                    case "Add friend":
                    case "Message":
                    case "Remove friend":
                        String target = Text.removeTags(entry.getTarget()).replace('\u00A0', ' ').trim();

                        String heldText = databaseAPI.findUserPronouns(target);
                        if (heldText != null && !heldText.isEmpty()) {
                            tooltipManager.add(new Tooltip(heldText));
                        }
                        return null;
                    default:
                        break;

                }
            }
        }

        if(config.showInWorld()) {

            Player foundPlayer;
            for (MenuEntry entry : list) {
                if ((foundPlayer = entry.getPlayer()) == null || checkedPlayers.contains(foundPlayer)) {
                    continue;
                }
                checkedPlayers.add(foundPlayer);
            }

            int numPlayersInList = checkedPlayers.size();

            for (Player p : checkedPlayers) {
                String heldText = databaseAPI.findUserPronouns(p.getName());
                if (heldText != null && !heldText.isEmpty()) {
                    if (numPlayersInList == 1) {
                        tooltipManager.add(new Tooltip(heldText));
                    } else {
                        tooltipManager.add(new Tooltip(p.getName() + ": " + heldText));
                    }
                }
            }
        }

        return null;
    }
}
