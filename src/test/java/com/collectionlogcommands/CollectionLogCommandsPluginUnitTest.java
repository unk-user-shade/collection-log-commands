package com.collectionlogcommands;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

public class CollectionLogCommandsPluginUnitTest
{
	@Test
	public void resolvesBossAliases()
	{
		assertEquals("zulrah", CollectionLogCommandsPlugin.resolveAlias("zulrah"));
		assertEquals("King Black Dragon", CollectionLogCommandsPlugin.resolveAlias("kbd"));
		assertEquals("Corrupted Gauntlet", CollectionLogCommandsPlugin.resolveAlias("cg"));
		assertEquals("araxxor", CollectionLogCommandsPlugin.resolveAlias("araxxor"));
	}

	@Test
	public void resolvesRaidAliases()
	{
		assertEquals("Chambers of Xeric", CollectionLogCommandsPlugin.resolveAlias("cox"));
		assertEquals("Chambers of Xeric", CollectionLogCommandsPlugin.resolveAlias("cox solo"));
		assertEquals("Chambers of Xeric", CollectionLogCommandsPlugin.resolveAlias("cox 24+"));
		assertEquals("Chambers of Xeric: Challenge Mode", CollectionLogCommandsPlugin.resolveAlias("cox cm"));
		assertEquals("Chambers of Xeric: Challenge Mode", CollectionLogCommandsPlugin.resolveAlias("cox cm duo"));
		assertEquals("Theatre of Blood", CollectionLogCommandsPlugin.resolveAlias("tob"));
		assertEquals("Theatre of Blood", CollectionLogCommandsPlugin.resolveAlias("tob 5"));
		assertEquals("Theatre of Blood: Hard Mode", CollectionLogCommandsPlugin.resolveAlias("hmt solo"));
		assertEquals("Tombs of Amascut: Expert Mode", CollectionLogCommandsPlugin.resolveAlias("toa expert"));
		assertEquals("Tombs of Amascut: Expert Mode", CollectionLogCommandsPlugin.resolveAlias("toa expert 8"));
	}

	@Test
	public void resolvesActivityAliases()
	{
		assertEquals("Brimhaven Agility Arena", CollectionLogCommandsPlugin.resolveAlias("brimhaven"));
		assertEquals("Hallowed Sepulchre", CollectionLogCommandsPlugin.resolveAlias("hs 5"));
		assertEquals("Wintertodt", CollectionLogCommandsPlugin.resolveAlias("wt"));
		assertEquals("Guardians of the Rift", CollectionLogCommandsPlugin.resolveAlias("gotr"));
		assertEquals("Larran's big chest", CollectionLogCommandsPlugin.resolveAlias("larran chest"));
	}

	@Test
	public void formatsCacheEntryLabels()
	{
		CollectionLogEntry entry = new CollectionLogEntry("auto", "Zulrah", Arrays.asList(
			new CollectionLogItem(1, "Tanzanite fang", true, 1),
			new CollectionLogItem(2, "Magic fang", false, 1),
			new CollectionLogItem(3, "Serpentine visage", true, 1)));

		assertEquals("Zulrah (2/3)", CollectionLogCommandsPlugin.formatCacheEntryLabel(entry));
	}

	@Test
	public void formatsMissingEntryLabels()
	{
		CollectionLogEntry entry = new CollectionLogEntry("auto", "Zulrah", Arrays.asList(
			new CollectionLogItem(1, "Tanzanite fang", true, 1),
			new CollectionLogItem(2, "Magic fang", false, 1),
			new CollectionLogItem(3, "Serpentine visage", false, 1)));

		assertEquals("Zulrah (2 missing)", CollectionLogCommandsPlugin.formatMissingEntryLabel(entry));
	}
}
