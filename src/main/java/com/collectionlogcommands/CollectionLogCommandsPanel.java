package com.collectionlogcommands;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;

public class CollectionLogCommandsPanel extends PluginPanel
{
	private final ItemManager itemManager;
	private final JPanel content = new JPanel(new BorderLayout(0, 8));
	private final JPanel list = new JPanel(new GridLayout(0, 1, 0, 4));
	private final JLabel cacheNote = new JLabel("<html>Open Collection Log pages once to cache them for ::log commands.</html>");

	public CollectionLogCommandsPanel(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		setLayout(new BorderLayout());
		content.add(cacheNote, BorderLayout.NORTH);
		content.add(list, BorderLayout.CENTER);
		add(content, BorderLayout.NORTH);
	}

	public void showEntry(CollectionLogEntry entry)
	{
		list.removeAll();

		list.add(new JLabel(entry.getName() + ": " + entry.obtainedCount() + "/" + entry.getItems().size()));

		for (CollectionLogItem item : entry.getItems())
		{
			JLabel label = new JLabel((item.isObtained() ? "✓ " : "✗ ") + item.getName());
			itemManager.getImage(item.getItemId()).addTo(label);
			list.add(label);
		}

		revalidate();
		repaint();
	}
}
