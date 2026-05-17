package com.collectionlogcommands;

public enum CollectionLogOutputMode
{
	VERBOSE("Verbose"),
	CONDENSED("Condensed");

	private final String name;

	CollectionLogOutputMode(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
