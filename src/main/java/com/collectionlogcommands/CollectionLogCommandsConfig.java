package com.collectionlogcommands;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("collectionlogcommands")
public interface CollectionLogCommandsConfig extends Config
{
	@ConfigItem(
		keyName = "outputMode",
		name = "Output mode",
		description = "Choose whether !log replies include item names or only compact item icons.",
		position = 1
	)
	default CollectionLogOutputMode outputMode()
	{
		return CollectionLogOutputMode.VERBOSE;
	}
}
