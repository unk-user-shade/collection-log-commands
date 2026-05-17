package com.collectionlogcommands;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.ItemComposition;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
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
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Collection Log Commands",
	description = "Adds !log command support for Collection Log pages",
	tags = {"collection", "log", "clog", "commands"}
)
public class CollectionLogCommandsPlugin extends Plugin
{
	private static final String COMMAND = "!log";
	private static final int CHAT_LIMIT = 8;

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ChatCommandManager chatCommandManager;
	@Inject private ItemManager itemManager;
	@Inject private ClientToolbar clientToolbar;

	private final Map<String, CollectionLogEntry> entriesByName = new HashMap<>();
	private final Map<Integer, Integer> chatSpriteIds = new HashMap<>();

	private CollectionLogCommandsPanel panel;
	private NavigationButton navButton;

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
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.COLLECTION)
		{
			return;
		}

		clientThread.invoke(this::cacheVisibleCollectionLogPage);
	}

	private void handleLogCommand(ChatMessage chatMessage, String message)
	{
		String input = message.length() > COMMAND.length()
			? message.substring(COMMAND.length()).trim()
			: "";

		if (input.isEmpty())
		{
			reply(chatMessage, "Usage: !log <entry name>");
			return;
		}

		List<CollectionLogEntry> matches = findMatches(input);

		if (matches.isEmpty())
		{
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

		displayEntry(chatMessage, matches.get(0));
	}

	private List<CollectionLogEntry> findMatches(String input)
	{
		String needle = normalize(input);

		return entriesByName.values().stream()
			.filter(e -> normalize(e.getName()).contains(needle) || fuzzyScore(normalize(e.getName()), needle) <= 2)
			.sorted(Comparator.comparingInt(e -> fuzzyScore(normalize(e.getName()), needle)))
			.collect(Collectors.toList());
	}

	private void displayEntry(ChatMessage chatMessage, CollectionLogEntry entry)
	{
		String header = entry.getName() + ": " + entry.obtainedCount() + "/" + entry.getItems().size() + " items obtained";
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", header, null);

		ChatMessageBuilder b = new ChatMessageBuilder();

		List<CollectionLogItem> items = entry.getItems();
		int max = Math.min(CHAT_LIMIT, items.size());

		for (int i = 0; i < max; i++)
		{
			CollectionLogItem item = items.get(i);
			b.append(item.isObtained() ? "✓ " : "✗ ");
			b.img(getChatSpriteId(item.getItemId()));
			b.append(" " + item.getName() + "  ");
		}

		if (items.size() > CHAT_LIMIT)
		{
			b.append("... opened side panel for full list");
			panel.showEntry(entry);
			clientToolbar.openPanel(navButton);
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
		Widget root = client.getWidget(InterfaceID.COLLECTION, 0);
		if (root == null)
		{
			return;
		}

		List<Widget> widgets = flatten(root);

		String entryName = widgets.stream()
			.map(Widget::getText)
			.filter(t -> t != null && !Text.removeTags(t).trim().isEmpty())
			.map(t -> Text.removeTags(t).trim())
			.filter(t -> !isKnownTabName(t))
			.findFirst()
			.orElse(null);

		if (entryName == null)
		{
			return;
		}

		List<CollectionLogItem> items = new ArrayList<>();

		for (Widget w : widgets)
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

			boolean obtained = w.getOpacity() == 0 || w.getItemQuantity() > 0;
			items.add(new CollectionLogItem(itemId, name, obtained));
		}

		if (!items.isEmpty())
		{
			entriesByName.put(normalize(entryName), new CollectionLogEntry("auto", entryName, items));
		}
	}

	private List<Widget> flatten(Widget root)
	{
		List<Widget> out = new ArrayList<>();
		out.add(root);

		if (root.getChildren() != null)
		{
			for (Widget child : root.getChildren())
			{
				if (child != null)
				{
					out.addAll(flatten(child));
				}
			}
		}

		if (root.getDynamicChildren() != null)
		{
			for (Widget child : root.getDynamicChildren())
			{
				if (child != null)
				{
					out.addAll(flatten(child));
				}
			}
		}

		return out;
	}

	private boolean isKnownTabName(String text)
	{
		String t = normalize(text);
		return t.equals("bosses")
			|| t.equals("raids")
			|| t.equals("clues")
			|| t.equals("minigames")
			|| t.equals("other");
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
