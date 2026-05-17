package com.collectionlogcommands;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("collectionlogcommands")
public interface CollectionLogCommandsConfig extends Config
{
	String GROUP = "collectionlogcommands";
	String CACHE_HINT_SHOWN_KEY = "cacheHintShown";

	@ConfigSection(
		name = "<html><body width='190'>[!] Open each Collection Log page once to cache it for ::log commands.</body></html>",
		description = "Collection Log Commands cache reminder.",
		position = 0
	)
	String cacheNoteSection = "cacheNote";

	@ConfigItem(
		keyName = "outputMode",
		name = "Output mode",
		description = "Choose whether ::log replies include item names or only compact item icons.",
		position = 1,
		section = cacheNoteSection
	)
	default CollectionLogOutputMode outputMode()
	{
		return CollectionLogOutputMode.VERBOSE;
	}

	@ConfigItem(
		keyName = CACHE_HINT_SHOWN_KEY,
		name = "Cache hint shown",
		description = "Tracks whether the one-time cache hint has been shown.",
		hidden = true
	)
	default boolean cacheHintShown()
	{
		return false;
	}
}
