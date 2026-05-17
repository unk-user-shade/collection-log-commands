package com.collectionlogcommands;

import java.util.List;
import lombok.Value;

@Value
public class CollectionLogEntry
{
	String tabName;
	String name;
	List<CollectionLogItem> items;

	public long obtainedCount()
	{
		return items.stream().filter(CollectionLogItem::isObtained).count();
	}
}
