package com.collectionlogcommands;

import com.google.inject.Provides;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
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
	private static final Type CACHE_TYPE = new TypeToken<Map<String, CollectionLogEntry>>(){}.getType();
	private static final Map<String, String> ENTRY_ALIASES = buildEntryAliases();
	private static final Color HEADER_COLOR = new Color(128, 160, 255);
	private static final Color ITEM_COLOR = Color.WHITE;
	private static final int VERBOSE_CHAT_ICON_WIDTH = 16;
	private static final int VERBOSE_CHAT_ICON_HEIGHT = 16;
	private static final int CONDENSED_CHAT_ICON_WIDTH = 16;
	private static final int CONDENSED_CHAT_ICON_HEIGHT = 16;
	private static final int CHAT_REWRITE_DELAY_MILLIS = 350;

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ChatCommandManager chatCommandManager;
	@Inject private ItemManager itemManager;
	@Inject private ClientToolbar clientToolbar;
	@Inject private Gson gson;
	@Inject private ScheduledExecutorService scheduledExecutorService;
	@Inject private CollectionLogCommandsConfig config;

	private final Map<String, CollectionLogEntry> entriesByName = new ConcurrentHashMap<>();
	private final Map<String, Integer> chatSpriteIds = new HashMap<>();

	private CollectionLogCommandsPanel panel;
	private NavigationButton navButton;
	private volatile String currentRsn;
	private ExecutorService ioExecutor;

	@Provides
	CollectionLogCommandsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CollectionLogCommandsConfig.class);
	}

	@Override
	protected void startUp()
	{
		ioExecutor = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "collection-log-commands-io");
			t.setDaemon(true);
			return t;
		});

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
		currentRsn = null;
		if (ioExecutor != null)
		{
			ioExecutor.shutdownNow();
			ioExecutor = null;
		}
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

		ExecutorService executor = ioExecutor;
		if (executor == null)
		{
			return;
		}
		// Defer execution behind the IO queue so any in-flight load completes first (FIFO).
		scheduledExecutorService.schedule(
			() -> executor.execute(() -> clientThread.invoke(() -> processLogCommand(chatMessage, message))),
			CHAT_REWRITE_DELAY_MILLIS,
			TimeUnit.MILLISECONDS);
	}

	private void processLogCommand(ChatMessage chatMessage, String message)
	{
		String input = message.length() > COMMAND.length()
			? message.substring(COMMAND.length()).trim()
			: "";

		if (input.isEmpty())
		{
			reply(chatMessage, formatHelp());
			return;
		}

		if (input.equalsIgnoreCase("help"))
		{
			reply(chatMessage, formatHelp());
			return;
		}

		if (input.equalsIgnoreCase("aliases"))
		{
			reply(chatMessage, formatAliases());
			return;
		}

		if (input.equalsIgnoreCase("summary"))
		{
			reply(chatMessage, formatSummary());
			return;
		}

		boolean showMissing = false;
		if (input.regionMatches(true, 0, "missing ", 0, 8))
		{
			showMissing = true;
			input = input.substring(8).trim();
		}

		int lastSpace = input.lastIndexOf(' ');
		if (lastSpace > 0 && input.substring(lastSpace + 1).equalsIgnoreCase("missing"))
		{
			showMissing = true;
			input = input.substring(0, lastSpace).trim();
		}

		if (input.isEmpty())
		{
			reply(chatMessage, "Usage: !log missing <entry>");
			return;
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
		String needle = normalize(resolveAlias(input));

		return entriesByName.values().stream()
			.filter(e -> normalize(e.getName()).contains(needle) || fuzzyScore(normalize(e.getName()), needle) <= 2)
			.sorted(Comparator.comparingInt(e -> fuzzyScore(normalize(e.getName()), needle)))
			.collect(Collectors.toList());
	}

	private String formatEntry(CollectionLogEntry entry, boolean showMissing)
	{
		if (config.outputMode() == CollectionLogOutputMode.CONDENSED)
		{
			return formatCondensedEntry(entry, showMissing);
		}

		return formatVerboseEntry(entry, showMissing);
	}

	private String formatVerboseEntry(CollectionLogEntry entry, boolean showMissing)
	{
		int total = entry.getItems().size();
		long shownCount = showMissing ? total - entry.obtainedCount() : entry.obtainedCount();
		String label = showMissing ? " missing  " : " collected  ";

		ChatMessageBuilder b = new ChatMessageBuilder()
			.append(HEADER_COLOR, entry.getName() + ": " + shownCount + "/" + total + label);

		boolean any = false;
		for (CollectionLogItem item : entry.getItems())
		{
			if (showMissing == item.isObtained())
			{
				continue;
			}
			b.img(getChatSpriteId(item.getItemId(), VERBOSE_CHAT_ICON_WIDTH, VERBOSE_CHAT_ICON_HEIGHT));
			b.append(ITEM_COLOR, " " + item.getName() + "  ");
			any = true;
		}

		if (!any)
		{
			b.append(ITEM_COLOR, showMissing ? "complete!" : "nothing yet");
		}

		return b.build();
	}

	private String formatCondensedEntry(CollectionLogEntry entry, boolean showMissing)
	{
		int total = entry.getItems().size();
		long shownCount = showMissing ? total - entry.obtainedCount() : entry.obtainedCount();
		String suffix = showMissing ? " missing" : "";

		ChatMessageBuilder b = new ChatMessageBuilder()
			.append(HEADER_COLOR, entry.getName() + suffix + " (" + shownCount + "/" + total + "): ");

		boolean any = false;
		for (CollectionLogItem item : entry.getItems())
		{
			if (showMissing == item.isObtained())
			{
				continue;
			}

			b.img(getChatSpriteId(item.getItemId(), CONDENSED_CHAT_ICON_WIDTH, CONDENSED_CHAT_ICON_HEIGHT));
			if (item.getQuantity() > 1)
			{
				b.append(HEADER_COLOR, " x" + item.getQuantity());
			}
			b.append(" ");
			any = true;
		}

		if (!any)
		{
			b.append(ITEM_COLOR, showMissing ? "complete!" : "nothing yet");
		}

		return b.build();
	}

	private String formatHelp()
	{
		return new ChatMessageBuilder()
			.append(HEADER_COLOR, "Collection Log Commands: ")
			.append(ITEM_COLOR, "!log <entry>, !log <entry> missing, !log missing <entry>, !log aliases, !log summary")
			.build();
	}

	private String formatAliases()
	{
		return new ChatMessageBuilder()
			.append(HEADER_COLOR, "Common !log aliases: ")
			.append(ITEM_COLOR, "cg, cox, cox cm, tob, tob hm, toa, toa expert, kbd, kq, corp, wt, gotr, pnm, jad, zuk")
			.build();
	}

	private String formatSummary()
	{
		int entries = entriesByName.size();
		int totalItems = entriesByName.values().stream()
			.mapToInt(e -> e.getItems().size())
			.sum();
		long obtainedItems = entriesByName.values().stream()
			.mapToLong(CollectionLogEntry::obtainedCount)
			.sum();

		return new ChatMessageBuilder()
			.append(HEADER_COLOR, "Collection log cache: ")
			.append(ITEM_COLOR, obtainedItems + "/" + totalItems + " items collected across " + entries + " cached entries")
			.build();
	}

	private void displayEntry(ChatMessage chatMessage, CollectionLogEntry entry, boolean showMissing)
	{
		reply(chatMessage, formatEntry(entry, showMissing));
	}

	private int getChatSpriteId(int itemId, int width, int height)
	{
		String key = itemId + ":" + width + "x" + height;
		return chatSpriteIds.computeIfAbsent(key, ignored ->
		{
			IndexedSprite[] old = client.getModIcons();
			if (old == null)
			{
				old = new IndexedSprite[0];
			}
			IndexedSprite[] next = Arrays.copyOf(old, old.length + 1);
			int index = old.length;
			client.setModIcons(next);

			AsyncBufferedImage image = itemManager.getImage(itemId);
			image.onLoaded(() ->
			{
				BufferedImage resized = ImageUtil.resizeImage(image, width, height);
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
			int quantity = Math.max(1, w.getItemQuantity());
			items.add(new CollectionLogItem(itemId, name, obtained, quantity));
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
		ioExecutor.submit(() -> loadFromDisk(rsn, cacheFile));
	}

	private void loadFromDisk(String rsn, File cacheFile)
	{
		try (Reader r = new FileReader(cacheFile))
		{
			Map<String, CollectionLogEntry> loaded = gson.fromJson(r, CACHE_TYPE);
			if (loaded == null || !rsn.equals(currentRsn))
			{
				return;
			}
			entriesByName.putAll(loaded);
			log.debug("clog loaded {} entries for {} from {}", loaded.size(), rsn, cacheFile.getName());
		}
		catch (Exception e)
		{
			log.warn("clog failed to load cache for {}: {}", rsn, e.toString());
		}
	}

	private void saveCache()
	{
		String rsn = currentRsn;
		if (rsn == null || ioExecutor == null)
		{
			return;
		}
		Map<String, CollectionLogEntry> snapshot = new HashMap<>(entriesByName);
		ioExecutor.submit(() -> writeToDisk(rsn, snapshot));
	}

	private void writeToDisk(String rsn, Map<String, CollectionLogEntry> snapshot)
	{
		File cacheFile = cacheFileFor(rsn);
		cacheFile.getParentFile().mkdirs();
		File tmp = new File(cacheFile.getPath() + ".tmp");
		try (Writer w = new FileWriter(tmp))
		{
			gson.toJson(snapshot, w);
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

	static String resolveAlias(String input)
	{
		return ENTRY_ALIASES.getOrDefault(normalize(input), input);
	}

	private static Map<String, String> buildEntryAliases()
	{
		Map<String, String> aliases = new HashMap<>();

		putAliases(aliases, "Grotesque Guardians", "dusk", "dawn", "gargs", "ggs", "gg");
		putAliases(aliases, "Abyssal Sire", "sire");
		putAliases(aliases, "Cerberus", "cerb");
		putAliases(aliases, "Thermonuclear Smoke Devil", "smoke devil", "thermy");
		putAliases(aliases, "Alchemical Hydra", "hydra");
		putAliases(aliases, "Amoxliatl", "amox");
		putAliases(aliases, "Hueycoatl", "huey", "the hueycoatl");
		putAliases(aliases, "Deranged Archaeologist", "deranged arch");
		putAliases(aliases, "Crazy Archaeologist", "crazy arch");
		putAliases(aliases, "Chaos Elemental", "chaos ele");
		putAliases(aliases, "Vet'ion", "vetion");
		putAliases(aliases, "Calvar'ion", "calv", "calvarion");
		putAliases(aliases, "Venenatis", "vene");
		putAliases(aliases, "King Black Dragon", "kbd");
		putAliases(aliases, "Corporeal Beast", "corp");
		putAliases(aliases, "Kalphite Queen", "kq");
		putAliases(aliases, "Giant Mole", "mole");
		putAliases(aliases, "Vorkath", "vork");
		putAliases(aliases, "Phantom Muspah", "phantom", "muspah", "pm");
		putAliases(aliases, "Nightmare", "nm", "tnm", "nmare", "the nightmare");
		putAliases(aliases, "Phosani's Nightmare", "pnm", "phosani", "phosanis", "phosani nm", "phosani nightmare", "phosanis nightmare");
		putAliases(aliases, "Commander Zilyana", "sara", "saradomin", "zilyana", "zily");
		putAliases(aliases, "K'ril Tsutsaroth", "zammy", "zamorak", "kril", "kril trutsaroth");
		putAliases(aliases, "Kree'arra", "arma", "kree", "kreearra", "armadyl");
		putAliases(aliases, "General Graardor", "bando", "bandos", "graardor");
		putAliases(aliases, "Dagannoth Supreme", "supreme");
		putAliases(aliases, "Dagannoth Rex", "rex");
		putAliases(aliases, "Dagannoth Prime", "prime");
		putAliases(aliases, "Duke Sucellus", "duke");
		putAliases(aliases, "Duke Sucellus (awakened)", "duke awakened", "duke sucellus awakened");
		putAliases(aliases, "Leviathan", "levi", "the leviathan");
		putAliases(aliases, "Leviathan (awakened)", "levi awakened", "leviathan awakened", "the leviathan awakened");
		putAliases(aliases, "Vardorvis", "vard");
		putAliases(aliases, "Vardorvis (awakened)", "vard awakened", "vardorvis awakened");
		putAliases(aliases, "Whisperer", "wisp", "whisp", "the whisperer");
		putAliases(aliases, "Whisperer (awakened)", "wisp awakened", "whisp awakened", "whisperer awakened");
		putAliases(aliases, "Barrows Chests", "barrows");
		putAliases(aliases, "Lunar Chest", "lunar chests", "moons of peril", "perilous moon", "perilous moons");
		putAliases(aliases, "Gauntlet", "gaunt", "the gauntlet");
		putAliases(aliases, "Corrupted Gauntlet", "cg", "cgaunt", "cgauntlet", "the corrupted gauntlet");
		putAliases(aliases, "TzTok-Jad", "jad", "tzhaar fight cave");
		putAliases(aliases, "TzHaar-Ket-Rak's Challenges", "jad 1", "jad 2", "jad 3", "jad 4", "jad 5", "jad 6");
		putAliases(aliases, "TzKal-Zuk", "zuk", "inferno");
		putAliases(aliases, "Sol Heredit", "sol", "colo", "colosseum", "fortis colosseum");
		putAliases(aliases, "Chambers of Xeric", "cox", "xeric", "chambers", "olm", "raids");
		putAliases(aliases, "Chambers of Xeric: Challenge Mode", "cox cm", "xeric cm", "chambers cm", "olm cm", "raids cm", "chambers of xeric - challenge mode");
		putAliases(aliases, "Theatre of Blood: Entry Mode", "tob sm", "tob story mode", "tob story", "tob entry mode", "tob em", "tob entry");
		putAliases(aliases, "Theatre of Blood", "tob", "theatre", "verzik", "verzik vitur", "raids 2");
		putAliases(aliases, "Theatre of Blood: Hard Mode", "tob cm", "tob hm", "tob hard mode", "tob hard", "hmt");
		putAliases(aliases, "Tombs of Amascut: Entry Mode", "toa entry", "toa entry mode", "tombs of amascut - entry");
		putAliases(aliases, "Tombs of Amascut", "toa", "tombs", "amascut", "warden", "wardens", "raids 3");
		putAliases(aliases, "Tombs of Amascut: Expert Mode", "toa expert", "toa expert mode", "tombs of amascut - expert");
		putRaidTeamAliases(aliases);
		putAliases(aliases, "Brimhaven Agility Arena", "agility arena", "brimhaven", "brimhavan agility");
		putAliases(aliases, "Hallowed Sepulchre", "hs", "hs1", "hs 1", "hs2", "hs 2", "hs3", "hs 3", "hs4", "hs 4", "hs5", "hs 5", "ghc", "sepulchre");
		putAliases(aliases, "Wintertodt", "wt");
		putAliases(aliases, "Tempoross", "fishingtodt", "fishtodt");
		putAliases(aliases, "Guardians of the Rift", "gotr", "runetodt", "rifts closed");
		putAliases(aliases, "Hunter Rumours", "hunterrumour", "hunter contract", "hunter contracts", "hunter tasks", "hunter task", "rumours", "rumour");
		putAliases(aliases, "Herbiboar", "herbi");
		putAliases(aliases, "Bird's egg sacrifices", "bird egg", "bird eggs", "bird's egg", "bird's eggs");
		putAliases(aliases, "Larran's big chest", "larran chest", "larran's chest", "larran big chest", "larran's big chest");
		putAliases(aliases, "Larran's small chest", "larran small chest", "larran's small chest");
		putAliases(aliases, "Brimstone chest", "brimstone chest");
		putAliases(aliases, "Crystal chest", "crystal chest");

		return aliases;
	}

	private static void putRaidTeamAliases(Map<String, String> aliases)
	{
		putAliases(aliases, "Chambers of Xeric", "cox solo", "cox duo", "cox 24+");
		putAliases(aliases, "Chambers of Xeric: Challenge Mode", "cox cm solo", "cox cm duo", "cox cm 24+");
		for (int i = 1; i <= 24; i++)
		{
			putAliases(aliases, "Chambers of Xeric", "cox " + i);
			putAliases(aliases, "Chambers of Xeric: Challenge Mode", "cox cm " + i);
		}

		putAliases(aliases, "Theatre of Blood", "tob solo", "tob duo");
		putAliases(aliases, "Theatre of Blood: Hard Mode", "hmt solo", "hmt duo");
		for (int i = 1; i <= 5; i++)
		{
			putAliases(aliases, "Theatre of Blood", "tob " + i);
			putAliases(aliases, "Theatre of Blood: Hard Mode", "hmt " + i);
		}

		putAliases(aliases, "Tombs of Amascut: Entry Mode", "toa entry solo", "toa entry duo");
		putAliases(aliases, "Tombs of Amascut", "toa solo", "toa duo");
		putAliases(aliases, "Tombs of Amascut: Expert Mode", "toa expert solo", "toa expert duo");
		for (int i = 1; i <= 8; i++)
		{
			putAliases(aliases, "Tombs of Amascut: Entry Mode", "toa entry " + i);
			putAliases(aliases, "Tombs of Amascut", "toa " + i);
			putAliases(aliases, "Tombs of Amascut: Expert Mode", "toa expert " + i);
		}
	}

	private static void putAliases(Map<String, String> aliases, String entryName, String... values)
	{
		for (String value : values)
		{
			aliases.put(normalize(value), entryName);
		}
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
