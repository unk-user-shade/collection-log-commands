package com.collectionlogcommands;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CollectionLogCommandsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CollectionLogCommandsPlugin.class);
		RuneLite.main(args);
	}
}
