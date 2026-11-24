package com.glamardor.roleplayersquill.gui;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.speech.Microphone;
import com.glamardor.roleplayersquill.speech.SpeechModels;
import com.glamardor.roleplayersquill.text.Alignment;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The settings screen for players without Cloth Config: the same options, plain vanilla widgets,
 * one scrolling list with the sections named as it goes.
 *
 * <p>Nothing is staged. A slider writes its value the moment it moves and {@code close} writes the
 * file, which is why there is no save button to forget to press.
 */
public class FallbackConfigScreen extends Screen {
	private static final int ROW_WIDTH = 310;

	@Nullable
	private final Screen parent;
	private final QuillConfig config = QuillConfig.get();
	private OptionList list;

	public FallbackConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("roleplayersquill.config.title"));
		this.parent = parent;
	}

	/**
	 * Leaves the world visible behind the settings.
	 *
	 * <p>Three different things darken a screen and this is the one a mixin cannot reach: the
	 * gradient a screen opened in the world lays over the whole window. It is drawn from here, so
	 * it is skipped from here.
	 */
	@Override
	public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		// Deliberately not calling super: no gradient, no darkening. The blur is handled by the mixin.
	}

	@Override
	protected void init() {
		list = new OptionList(this.minecraft, this.width, this.height - 96, 40, 25);

		list.addHeader(Component.translatable("roleplayersquill.category.editor"));
		list.addWidget(toggle("replace_editor", () -> config.replaceBookEditor, v -> config.replaceBookEditor = v));
		list.addWidget(slider("editor_scale", config.editorScale, 1.0f, 2.5f,
				v -> config.editorScale = v, v -> Component.literal(Math.round(v * 100) + "%")));
		list.addWidget(toggle("show_counter", () -> config.showCounter, v -> config.showCounter = v));
		list.addWidget(toggle("show_guides", () -> config.showGuides, v -> config.showGuides = v));
		list.addWidget(toggle("carry_formatting", () -> config.carryFormatting, v -> config.carryFormatting = v));
		list.addWidget(cycle("default_alignment", () -> config.defaultAlignment.label(),
				() -> config.defaultAlignment = config.defaultAlignment.next()));

		list.addHeader(Component.translatable("roleplayersquill.category.layout"));
		list.addWidget(toggle("hyphenate", () -> config.hyphenate, v -> config.hyphenate = v));
		list.addWidget(slider("hyphen_min_before", config.hyphenMinBefore, 1.0f, 5.0f,
				v -> config.hyphenMinBefore = Math.round(v), v -> Component.literal(String.valueOf(Math.round(v)))));
		list.addWidget(slider("hyphen_min_after", config.hyphenMinAfter, 1.0f, 5.0f,
				v -> config.hyphenMinAfter = Math.round(v), v -> Component.literal(String.valueOf(Math.round(v)))));
		list.addWidget(toggle("page_break_hyphen", () -> config.hyphenateAtPageBreak,
				v -> config.hyphenateAtPageBreak = v));
		list.addWidget(toggle("justify_last_line", () -> config.justifyLastLine, v -> config.justifyLastLine = v));

		list.addHeader(Component.translatable("roleplayersquill.category.spelling"));
		list.addWidget(toggle("spell_check", () -> config.spellCheck, v -> config.spellCheck = v));
		list.addWidget(toggle("spell_skip_capitals", () -> config.spellSkipCapitals,
				v -> config.spellSkipCapitals = v));
		list.addWidget(toggle("spell_russian", () -> config.spellRussian, v -> config.spellRussian = v));
		list.addWidget(toggle("spell_english", () -> config.spellEnglish, v -> config.spellEnglish = v));
		list.addWidget(toggle("spell_elsewhere", () -> config.spellElsewhere, v -> config.spellElsewhere = v));
		list.addWidget(toggle("spell_auto_download", () -> config.spellAutoDownload,
				v -> config.spellAutoDownload = v));

		list.addHeader(Component.translatable("roleplayersquill.category.paste"));
		list.addWidget(toggle("auto_paste_pages", () -> config.autoPasteMultiPage, v -> config.autoPasteMultiPage = v));
		list.addWidget(toggle("confirm_big_paste", () -> config.confirmBigPaste, v -> config.confirmBigPaste = v));
		list.addWidget(toggle("import_join_lines", () -> config.importJoinLines, v -> config.importJoinLines = v));
		list.addWidget(textField("export_folder", config.exportFolder, v -> config.exportFolder = v));

		list.addHeader(Component.translatable("roleplayersquill.category.links"));
		list.addWidget(cycle("rich_mode", () -> config.richMode.label(), () -> config.richMode = config.richMode.next()));
		list.addWidget(toggle("highlight_links", () -> config.highlightLinks, v -> config.highlightLinks = v));
		list.addWidget(toggle("reader_links", () -> config.readerLinks, v -> config.readerLinks = v));
		list.addWidget(toggle("reader_links_no_scheme", () -> config.readerLinksWithoutScheme,
				v -> config.readerLinksWithoutScheme = v));

		list.addHeader(Component.translatable("roleplayersquill.category.reading"));
		list.addWidget(toggle("reader_tools", () -> config.readerTools, v -> config.readerTools = v));
		list.addWidget(toggle("reader_header", () -> config.readerHeader, v -> config.readerHeader = v));
		list.addWidget(toggle("keep_opened_books", () -> config.keepOpenedBooks,
				v -> config.keepOpenedBooks = v));

		list.addHeader(Component.translatable("roleplayersquill.category.bookmark"));
		list.addWidget(toggle("open_at_bookmark", () -> config.openAtBookmark, v -> config.openAtBookmark = v));
		list.addWidget(cycle("bookmark_colour", () -> config.bookmarkColour.label(),
				() -> config.bookmarkColour = config.bookmarkColour.next()));
		list.addWidget(cycle("bookmark_style", () -> config.bookmarkStyle.label(),
				() -> config.bookmarkStyle = config.bookmarkStyle.next()));

		list.addHeader(Component.translatable("roleplayersquill.category.elsewhere"));
		list.addWidget(toggle("sign_editor", () -> config.signEditor, v -> config.signEditor = v));
		list.addWidget(toggle("anvil_editor", () -> config.anvilEditor, v -> config.anvilEditor = v));
		list.addWidget(toggle("chat_symbols", () -> config.chatSymbols, v -> config.chatSymbols = v));
		list.addWidget(toggle("chat_formatting", () -> config.chatFormatting, v -> config.chatFormatting = v));
		list.addWidget(textField("chat_prefix", config.chatCodePrefix, v -> config.chatCodePrefix = v));

		list.addHeader(Component.translatable("roleplayersquill.category.voice"));
		list.addWidget(toggle("voice_enabled", () -> config.voiceEnabled, v -> config.voiceEnabled = v));
		list.addWidget(cycle("voice_model", () -> modelLabel(config.voiceModel),
				() -> config.voiceModel = nextModel(config.voiceModel)));
		list.addWidget(toggle("voice_auto_download", () -> config.voiceAutoDownload, v -> config.voiceAutoDownload = v));
		list.addWidget(cycle("voice_device", () -> deviceLabel(config.voiceDevice),
				() -> config.voiceDevice = nextDevice(config.voiceDevice)));
		list.addWidget(toggle("voice_tidy", () -> config.voiceTidyUp, v -> config.voiceTidyUp = v));

		addRenderableWidget(list);

		Button reset = Button.builder(Component.translatable("roleplayersquill.config.reset"), button -> {
			config.resetToDefaults();
			rebuildWidgets();
		}).bounds(this.width / 2 - 154, this.height - 30, 150, 20).build();
		reset.setTooltip(Tooltip.create(Component.translatable("roleplayersquill.config.reset.tooltip")));
		addRenderableWidget(reset);

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(this.width / 2 + 4, this.height - 30, 150, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(this.font, this.title, this.width / 2, 18, 0xFFFFFFFF);
		context.centeredText(this.font,
				Component.translatable("roleplayersquill.config.no_cloth"), this.width / 2, this.height - 44, 0xFF9A9A9A);
	}

	@Override
	public void onClose() {
		config.save();
		Minecraft.getInstance().gui.setScreen(parent);
	}

	// ---- the voice lists ---------------------------------------------------------------------------

	private static Component modelLabel(String id) {
		SpeechModels.Entry entry = SpeechModels.byId(id);
		if (entry == null) {
			return Component.literal(id);
		}
		return Component.literal(entry.language() + " · " + entry.megabytes() + " MB");
	}

	private static String nextModel(String id) {
		List<String> ids = new ArrayList<>(SpeechModels.catalogue().keySet());
		int at = ids.indexOf(id);
		return ids.get((at + 1 + ids.size()) % ids.size());
	}

	private static Component deviceLabel(String name) {
		return name == null || name.isBlank()
				? Component.translatable("roleplayersquill.option.voice_device.default")
				: Component.literal(name);
	}

	private static String nextDevice(String current) {
		List<String> names = new ArrayList<>();
		names.add("");
		names.addAll(Microphone.devices());
		int at = names.indexOf(current == null ? "" : current);
		return names.get((at + 1 + names.size()) % names.size());
	}

	// ---- widget shorthands ---------------------------------------------------------------------------

	private AbstractWidget toggle(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return cycle(key, () -> getter.get() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF, () -> setter.accept(!getter.get()));
	}

	private AbstractWidget cycle(String key, Supplier<Component> value, Runnable onClick) {
		Button button = Button.builder(label(key, value.get()), b -> {
			onClick.run();
			b.setMessage(label(key, value.get()));
		}).bounds(0, 0, ROW_WIDTH, 20).build();
		button.setTooltip(Tooltip.create(Component.translatable("roleplayersquill.option." + key + ".tooltip")));
		return button;
	}

	private AbstractWidget textField(String key, String current, Consumer<String> setter) {
		EditBox field = new EditBox(this.font, 0, 0, ROW_WIDTH, 20,
				Component.translatable("roleplayersquill.option." + key));
		field.setMaxLength(128);
		field.setValue(current);
		// Written on every keystroke, like everything else here: a field that only committed on
		// enter would silently lose what was typed.
		field.setResponder(setter);
		field.setTooltip(Tooltip.create(Component.translatable("roleplayersquill.option." + key + ".tooltip")));
		return field;
	}

	private AbstractWidget slider(String key, float current, float min, float max, Consumer<Float> setter,
			Function<Float, Component> display) {
		return new OptionSlider(key, current, min, max, setter, display);
	}

	private static Component label(String key, Component value) {
		return Component.translatable("roleplayersquill.option." + key).append(": ").append(value);
	}

	private static class OptionSlider extends AbstractSliderButton {
		private final String key;
		private final float min;
		private final float max;
		private final Consumer<Float> setter;
		private final Function<Float, Component> display;

		OptionSlider(String key, float current, float min, float max, Consumer<Float> setter,
				Function<Float, Component> display) {
			super(0, 0, ROW_WIDTH, 20, Component.empty(), Mth.clamp((current - min) / (max - min), 0.0f, 1.0f));
			this.key = key;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.display = display;
			setTooltip(Tooltip.create(Component.translatable("roleplayersquill.option." + key + ".tooltip")));
			updateMessage();
		}

		private float currentValue() {
			return (float) (min + (max - min) * this.value);
		}

		@Override
		protected void updateMessage() {
			setMessage(label(key, display.apply(currentValue())));
		}

		@Override
		protected void applyValue() {
			setter.accept(currentValue());
		}
	}

	private static class OptionList extends ContainerObjectSelectionList<OptionList.Entry> {
		OptionList(Minecraft client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void addWidget(AbstractWidget widget) {
			addEntry(new WidgetEntry(widget));
		}

		void addHeader(Component text) {
			addEntry(new HeaderEntry(text));
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}

		abstract static class Entry extends ContainerObjectSelectionList.Entry<com.glamardor.roleplayersquill.gui.FallbackConfigScreen.OptionList.Entry> {
		}

		static class WidgetEntry extends com.glamardor.roleplayersquill.gui.FallbackConfigScreen.OptionList.Entry {
			private final AbstractWidget widget;

			WidgetEntry(AbstractWidget widget) {
				this.widget = widget;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
				int x = getContentX(), y = getContentY(), entryWidth = getContentWidth();
				widget.setX(x);
				widget.setY(y);
				widget.setWidth(entryWidth);
				widget.extractRenderState(context, mouseX, mouseY, tickDelta);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of(widget);
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of(widget);
			}
		}

		static class HeaderEntry extends com.glamardor.roleplayersquill.gui.FallbackConfigScreen.OptionList.Entry {
			private final Component text;

			HeaderEntry(Component text) {
				this.text = text;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
				int x = getContentX(), y = getContentY(), entryWidth = getContentWidth();
				Minecraft client = Minecraft.getInstance();
				context.centeredText(client.font, text, x + entryWidth / 2, y + 8, 0xFFE0C070);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of();
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of();
			}
		}
	}
}
