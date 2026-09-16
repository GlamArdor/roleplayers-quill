package com.glamardor.roleplayersquill.book;

import com.glamardor.roleplayersquill.RoleplayersQuill;
import com.glamardor.roleplayersquill.config.QuillConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gets a finished book from the client to the server.
 *
 * <p>Two roads. The ordinary one is {@code BookUpdateC2SPacket}, which every player has and every
 * server accepts: a list of strings, optionally with a title, which signs the book. The other is
 * the creative inventory packet, which hands the server a whole item – and a whole item can carry
 * pages that are text components, with links and tooltips and colours a {@code §} code cannot name.
 *
 * <p>The creative road needs creative mode and nothing more; the server's own check is
 * {@code isInCreativeMode()}, not a permission. Where that is not available, the ordinary road is
 * taken and the page still says everything it can.
 */
public final class BookSender {
	private BookSender() {
	}

	/** Where the held book sits in the inventory, as {@code BookUpdateC2SPacket} counts slots. */
	public static int inventorySlot(ClientPlayerEntity player, Hand hand) {
		return hand == Hand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40;
	}

	/**
	 * The same slot as the player's own inventory screen numbers it, which is what the creative
	 * packet wants: nine hotbar slots from 36, and the off hand at 45.
	 */
	public static int screenSlot(ClientPlayerEntity player, Hand hand) {
		return hand == Hand.MAIN_HAND ? 36 + player.getInventory().getSelectedSlot() : 45;
	}

	/** Whether a page may be written as a component rather than a string, right now. */
	public static boolean canWriteRich() {
		if (QuillConfig.get().richMode == QuillConfig.RichMode.OFF) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.interactionManager == null) {
			return false;
		}
		return client.interactionManager.getCurrentGameMode().isCreative();
	}

	/**
	 * Whether to say so out loud when a book is about to be signed without the links it carries.
	 *
	 * <p>There is no third road: outside creative the pages have to be strings, and a link written
	 * into one is simply lost. Silently is the sensible default; saying so is for anyone who writes
	 * linked books often enough to be caught out by it.
	 */
	public static boolean warnWhenDegrading() {
		return QuillConfig.get().richMode == QuillConfig.RichMode.ALWAYS;
	}

	/** Saves the draft back into the book without signing it: the vanilla "Done" button. */
	public static void saveDraft(ItemStack stack, Hand hand, List<String> pages) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.getNetworkHandler() == null) {
			return;
		}
		List<String> trimmed = withoutTrailingBlanks(pages);
		stack.set(DataComponentTypes.WRITABLE_BOOK_CONTENT,
				new WritableBookContentComponent(trimmed.stream().map(RawFilteredPair::of).toList()));
		client.getNetworkHandler().sendPacket(
				new BookUpdateC2SPacket(inventorySlot(client.player, hand), trimmed, Optional.empty()));
	}

	/** Signs the book the ordinary way: the server builds the components from these strings. */
	public static void sign(ItemStack stack, Hand hand, List<String> pages, String title) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.getNetworkHandler() == null) {
			return;
		}
		List<String> trimmed = withoutTrailingBlanks(pages);
		stack.set(DataComponentTypes.WRITABLE_BOOK_CONTENT,
				new WritableBookContentComponent(trimmed.stream().map(RawFilteredPair::of).toList()));
		client.getNetworkHandler().sendPacket(
				new BookUpdateC2SPacket(inventorySlot(client.player, hand), trimmed, Optional.of(title)));
	}

	/**
	 * Signs the book as a finished item, pages and all.
	 *
	 * <p>Only in creative, and the caller is expected to have asked {@link #canWriteRich()} first.
	 * The book arrives already written rather than being assembled server-side, which is the only
	 * way a page can carry a link.
	 */
	public static boolean signRich(Hand hand, List<Text> pages, String title, int generation) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.getNetworkHandler() == null || !canWriteRich()) {
			return false;
		}
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(
				RawFilteredPair.of(title),
				client.player.getGameProfile().getName(),
				generation,
				pages.stream().map(RawFilteredPair::of).toList(),
				true));

		int slot = screenSlot(client.player, hand);
		client.player.getInventory().setStack(
				hand == Hand.MAIN_HAND ? client.player.getInventory().getSelectedSlot() : 40, book);
		client.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(slot, book));
		RoleplayersQuill.LOGGER.debug("Sent a component-written book into slot {}", slot);
		return true;
	}

	/**
	 * Replaces the held item outright, for the anvil's sake: a real name with a real colour, which
	 * the anvil itself cannot give because the server strips section signs out of rename packets.
	 */
	public static boolean setHeldItem(ItemStack stack, Hand hand) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.getNetworkHandler() == null || !canWriteRich()) {
			return false;
		}
		int slot = screenSlot(client.player, hand);
		client.player.getInventory().setStack(
				hand == Hand.MAIN_HAND ? client.player.getInventory().getSelectedSlot() : 40, stack);
		client.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(slot, stack));
		return true;
	}

	/** Vanilla drops the empty pages off the end before it saves; a book should not grow blanks. */
	private static List<String> withoutTrailingBlanks(List<String> pages) {
		List<String> copy = new ArrayList<>(pages);
		while (copy.size() > 1 && copy.get(copy.size() - 1).isEmpty()) {
			copy.remove(copy.size() - 1);
		}
		if (copy.isEmpty()) {
			copy.add("");
		}
		return copy;
	}
}
