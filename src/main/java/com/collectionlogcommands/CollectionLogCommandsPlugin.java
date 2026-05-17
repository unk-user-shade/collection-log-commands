package com.collectionlogcommands;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedSprite;
import net.runelite.api.ItemComposition;
import net.runelite.api.MessageNode;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Collection Log Commands",
	description = "Adds !log command support for Collection Log pages",
	tags = {"collection", "log", "clog", "commands"}
)
public class CollectionLogCommandsPlugin extends Plugin
{
	private static final String COMMAND = "!log";
	private static final File CACHE_DIR = new File(RuneLite.RUNELITE_DIR, "collection-log-commands");
	private static final Gson GSON = new Gson();
	private static final Type CACHE_TYPE = new TypeToken<Map<String, CollectionLogEntry>>(){}.getType();

	@Inject private Client client;
	@Inject private ChatCommandManager chatCommandManager;
	@Inject private ItemManager itemManager;
	@Inject private ClientToolbar clientToolbar;

	private final Map<String, CollectionLogEntry> entriesByName = new HashMap<>();
	private final Map<Integer, Integer> chatSpriteIds = new HashMap<>();

	private CollectionLogCommandsPanel panel;
	private NavigationButton navButton;
	private String currentRsn;

	@Override
	protected void startUp()
	{
		panel = new CollectionLogCommandsPanel(itemManager);

		navButton = NavigationButton.builder()
			.tooltip("Collection Log Commands")
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		chatCommandManager.registerCommand(COMMAND, this::handleLogCommand);
	}

	@Override
	protected void shutDown()
	{
		chatCommandManager.unregisterCommand(COMMAND);
		clientToolbar.removeNavigation(navButton);
		entriesByName.clear();
		chatSpriteIds.clear();
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.COLLECTION_DRAW_LIST)
		{
			return;
		}

		cacheVisibleCollectionLogPage();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			ensureCacheLoadedForCurrentPlayer();
		}
	}

	private void handleLogCommand(ChatMessage chatMessage, String message)
	{
		ensureCacheLoadedForCurrentPlayer();

		String input = message.length() > COMMAND.length()
			? message.substring(COMMAND.length()).trim()
			: "";

		if (input.isEmpty())
		{
			reply(chatMessage, "Usage: !log <entry name> [missing]");
			return;
		}

		boolean showMissing = false;
		int lastSpace = input.lastIndexOf(' ');
		if (lastSpace > 0 && input.substring(lastSpace + 1).equalsIgnoreCase("missing"))
		{
			showMissing = true;
			input = input.substring(0, lastSpace).trim();
		}

		List<CollectionLogEntry> matches = findMatches(input);

		if (matches.isEmpty())
		{
			log.debug("clog !log no match for '{}' (needle='{}'), cached keys: {}", input, normalize(input), entriesByName.keySet());
			reply(chatMessage, "No collection log entry found for \"" + input + "\".");
			return;
		}

		if (matches.size() > 1)
		{
			String names = matches.stream()
				.limit(5)
				.map(CollectionLogEntry::getName)
				.collect(Collectors.joining(", "));

			reply(chatMessage, "Did you mean: " + names + "?");
			return;
		}

		displayEntry(chatMessage, matches.get(0), showMissing);
	}

	private List<CollectionLogEntry> findMatches(String input)
	{
		String needle = normalize(input);

		return entriesByName.values().stream()
			.filter(e -> normalize(e.getName()).contains(needle) || fuzzyScore(normalize(e.getName()), needle) <= 2)
			.sorted(Comparator.comparingInt(e -> fuzzyScore(normalize(e.getName()), needle)))
			.collect(Collectors.toList());
	}

	private void displayEntry(ChatMessage chatMessage, CollectionLogEntry entry, boolean showMissing)
	{
		int total = entry.getItems().size();
		long shownCount = showMissing ? total - entry.obtainedCount() : entry.obtainedCount();
		String label = showMissing ? " missing  " : " collected  ";

		ChatMessageBuilder b = new ChatMessageBuilder()
			.append(entry.getName() + ": " + shownCount + "/" + total + label);

		boolean any = false;
		for (CollectionLogItem item : entry.getItems())
		{
			if (showMissing == item.isObtained())
			{
				continue;
			}
			b.img(getChatSpriteId(item.getItemId()));
			b.append(" " + item.getName() + "  ");
			any = true;
		}

		if (!any)
		{
			b.append(showMissing ? "complete!" : "nothing yet");
		}

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", b.build(), null);
	}

	private int getChatSpriteId(int itemId)
	{
		return chatSpriteIds.computeIfAbsent(itemId, id ->
		{
			IndexedSprite[] old = client.getModIcons();
			IndexedSprite[] next = java.util.Arrays.copyOf(old, old.length + 1);
			int index = old.length;
			client.setModIcons(next);

			AsyncBufferedImage image = itemManager.getImage(id);
			image.onLoaded(() ->
			{
				BufferedImage resized = ImageUtil.resizeImage(image, 18, 16);
				client.getModIcons()[index] = ImageUtil.getImageIndexedSprite(resized, client);
			});

			return index;
		});
	}

	private void cacheVisibleCollectionLogPage()
	{
		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		Widget itemsContents = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
		log.debug("clog draw fired, header='{}', items widget children={}",
			header != null && header.getChild(0) != null ? header.getChild(0).getText() : "null",
			itemsContents != null && itemsContents.getChildren() != null ? itemsContents.getChildren().length : -1);

		if (header == null || header.getChild(0) == null)
		{
			return;
		}

		String entryName = header.getChild(0).getText();
		if (entryName == null || entryName.isEmpty())
		{
			return;
		}

		if (itemsContents == null || itemsContents.getChildren() == null)
		{
			return;
		}

		List<CollectionLogItem> items = new ArrayList<>();

		for (Widget w : itemsContents.getChildren())
		{
			int itemId = w.getItemId();

			if (itemId <= 0)
			{
				continue;
			}

			ItemComposition comp = itemManager.getItemComposition(itemId);
			String name = comp.getName();

			if (name == null || name.equalsIgnoreCase("null"))
			{
				continue;
			}

			boolean obtained = w.getOpacity() == 0;
			items.add(new CollectionLogItem(itemId, name, obtained));
		}

		if (!items.isEmpty())
		{
			String key = normalize(entryName);
			CollectionLogEntry updated = new CollectionLogEntry("auto", entryName, items);
			if (!updated.equals(entriesByName.get(key)))
			{
				entriesByName.put(key, updated);
				log.debug("clog cached entry '{}' with {} items, keys now: {}", entryName, items.size(), entriesByName.keySet());
				saveCache();
			}
		}
	}

	private void ensureCacheLoadedForCurrentPlayer()
	{
		if (client.getLocalPlayer() == null)
		{
			return;
		}
		String rsn = client.getLocalPlayer().getName();
		if (rsn == null || rsn.isEmpty() || rsn.equals(currentRsn))
		{
			return;
		}

		currentRsn = rsn;
		entriesByName.clear();

		File cacheFile = cacheFileFor(rsn);
		if (!cacheFile.exists())
		{
			log.debug("clog no cache file yet for {}", rsn);
			return;
		}
		try (Reader r = new FileReader(cacheFile))
		{
			Map<String, CollectionLogEntry> loaded = GSON.fromJson(r, CACHE_TYPE);
			if (loaded != null)
			{
				entriesByName.putAll(loaded);
			}
			log.debug("clog loaded {} entries for {} from {}", entriesByName.size(), rsn, cacheFile.getName());
		}
		catch (Exception e)
		{
			log.warn("clog failed to load cache for {}: {}", rsn, e.toString());
		}
	}

	private void saveCache()
	{
		if (currentRsn == null)
		{
			return;
		}
		File cacheFile = cacheFileFor(currentRsn);
		cacheFile.getParentFile().mkdirs();
		File tmp = new File(cacheFile.getPath() + ".tmp");
		try (Writer w = new FileWriter(tmp))
		{
			GSON.toJson(entriesByName, w);
		}
		catch (IOException e)
		{
			log.warn("clog failed to write cache tmp file: {}", e.toString());
			return;
		}
		try
		{
			Files.move(tmp.toPath(), cacheFile.toPath(),
				StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			if (!tmp.renameTo(cacheFile))
			{
				log.warn("clog failed to commit cache file: {}", e.toString());
			}
		}
	}

	private static File cacheFileFor(String rsn)
	{
		String safe = rsn.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
		return new File(CACHE_DIR, "cache_" + safe + ".json");
	}

	private void reply(ChatMessage chatMessage, String response)
	{
		MessageNode node = chatMessage.getMessageNode();
		node.setRuneLiteFormatMessage(response);
		client.refreshChat();
	}

	private static String normalize(String s)
	{
		return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private static int fuzzyScore(String a, String b)
	{
		if (a.contains(b))
		{
			return 0;
		}

		int[][] dp = new int[a.length() + 1][b.length() + 1];

		for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
		for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

		for (int i = 1; i <= a.length(); i++)
		{
			for (int j = 1; j <= b.length(); j++)
			{
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				dp[i][j] = Math.min(
					Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
					dp[i - 1][j - 1] + cost
				);
			}
		}

		return dp[a.length()][b.length()];
	}
}
