package com.glamardor.roleplayersquill.gui;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.speech.Microphone;
import com.glamardor.roleplayersquill.speech.SpeechModels;
import com.glamardor.roleplayersquill.text.Alignment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
		super(Text.translatable("roleplayersquill.config.title"));
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
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Deliberately not calling super: no gradient, no darkening. The blur is handled by the mixin.
	}

	@Override
	protected void init() {
		list = new OptionList(this.client, this.width, this.height - 96, 40, 25);

		list.addHeader(Text.translatable("roleplayersquill.category.editor"));
		list.addWidget(toggle("replace_editor", () -> config.replaceBookEditor, v -> config.replaceBookEditor = v));
		list.addWidget(slider("editor_scale", config.editorScale, 1.0f, 2.5f,
				v -> config.editorScale = v, v -> Text.literal(Math.round(v * 100) + "%")));
		list.addWidget(toggle("show_counter", () -> config.showCounter, v -> config.showCounter = v));
		list.addWidget(toggle("show_guides", () -> config.showGuides, v -> config.showGuides = v));
		list.addWidget(toggle("carry_formatting", () -> config.carryFormatting, v -> config.carryFormatting = v));
		list.addWidget(cycle("default_alignment", () -> config.defaultAlignment.label(),
				() -> config.defaultAlignment = config.defaultAlignment.next()));

		list.addHeader(Text.translatable("roleplayersquill.category.layout"));
		list.addWidget(toggle("hyphenate", () -> config.hyphenate, v -> config.hyphenate = v));
		list.addWidget(slider("hyphen_min_before", config.hyphenMinBefore, 1.0f, 5.0f,
				v -> config.hyphenMinBefore = Math.round(v), v -> Text.literal(String.valueOf(Math.round(v)))));
		list.addWidget(slider("hyphen_min_after", config.hyphenMinAfter, 1.0f, 5.0f,
				v -> config.hyphenMinAfter = Math.round(v), v -> Text.literal(String.valueOf(Math.round(v)))));
		list.addWidget(toggle("page_break_hyphen", () -> config.hyphenateAtPageBreak,
				v -> config.hyphenateAtPageBreak = v));
		list.addWidget(toggle("justify_last_line", () -> config.justifyLastLine, v -> config.justifyLastLine = v));

		list.addHeader(Text.translatable("roleplayersquill.category.spelling"));
		list.addWidget(toggle("spell_check", () -> config.spellCheck, v -> config.spellCheck = v));
		list.addWidget(toggle("spell_skip_capitals", () -> config.spellSkipCapitals,
				v -> config.spellSkipCapitals = v));
		list.addWidget(toggle("spell_russian", () -> config.spellRussian, v -> config.spellRussian = v));
		list.addWidget(toggle("spell_english", () -> config.spellEnglish, v -> config.spellEnglish = v));
		list.addWidget(toggle("spell_elsewhere", () -> config.spellElsewhere, v -> config.spellElsewhere = v));
		list.addWidget(toggle("spell_auto_download", () -> config.spellAutoDownload,
				v -> config.spellAutoDownload = v));

		list.addHeader(Text.translatable("roleplayersquill.category.paste"));
		list.addWidget(toggle("auto_paste_pages", () -> config.autoPasteMultiPage, v -> config.autoPasteMultiPage = v));
		list.addWidget(toggle("confirm_big_paste", () -> config.confirmBigPaste, v -> config.confirmBigPaste = v));
		list.addWidget(toggle("import_join_lines", () -> config.importJoinLines, v -> config.importJoinLines = v));
		list.addWidget(textField("export_folder", config.exportFolder, v -> config.exportFolder = v));

		list.addHeader(Text.translatable("roleplayersquill.category.links"));
		list.addWidget(cycle("rich_mode", () -> config.richMode.label(), () -> config.richMode = config.richMode.next()));
		list.addWidget(toggle("highlight_links", () -> config.highlightLinks, v -> config.highlightLinks = v));
		list.addWidget(toggle("reader_links", () -> config.readerLinks, v -> config.readerLinks = v));
		list.addWidget(toggle("reader_links_no_scheme", () -> config.readerLinksWithoutScheme,
				v -> config.readerLinksWithoutScheme = v));

		list.addHeader(Text.translatable("roleplayersquill.category.elsewhere"));
		list.addWidget(toggle("sign_editor", () -> config.signEditor, v -> config.signEditor = v));
		list.addWidget(toggle("anvil_editor", () -> config.anvilEditor, v -> config.anvilEditor = v));
		list.addWidget(toggle("chat_symbols", () -> config.chatSymbols, v -> config.chatSymbols = v));
		list.addWidget(toggle("chat_formatting", () -> config.chatFormatting, v -> config.chatFormatting = v));
		list.addWidget(textField("chat_prefix", config.chatCodePrefix, v -> config.chatCodePrefix = v));

		list.addHeader(Text.translatable("roleplayersquill.category.voice"));
		list.addWidget(toggle("voice_enabled", () -> config.voiceEnabled, v -> config.voiceEnabled = v));
		list.addWidget(cycle("voice_model", () -> modelLabel(config.voiceModel),
				() -> config.voiceModel = nextModel(config.voiceModel)));
		list.addWidget(toggle("voice_auto_download", () -> config.voiceAutoDownload, v -> config.voiceAutoDownload = v));
		list.addWidget(cycle("voice_device", () -> deviceLabel(config.voiceDevice),
				() -> config.voiceDevice = nextDevice(config.voiceDevice)));
		list.addWidget(toggle("voice_tidy", () -> config.voiceTidyUp, v -> config.voiceTidyUp = v));

		addDrawableChild(list);

		ButtonWidget reset = ButtonWidget.builder(Text.translatable("roleplayersquill.config.reset"), button -> {
			config.resetToDefaults();
			clearAndInit();
		}).dimensions(this.width / 2 - 154, this.height - 30, 150, 20).build();
		reset.setTooltip(Tooltip.of(Text.translatable("roleplayersquill.config.reset.tooltip")));
		addDrawableChild(reset);

		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(this.width / 2 + 4, this.height - 30, 150, 20)
				.build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("roleplayersquill.config.no_cloth"), this.width / 2, this.height - 44, 0xFF9A9A9A);
	}

	@Override
	public void close() {
		config.save();
		MinecraftClient.getInstance().setScreen(parent);
	}

	// ---- the voice lists ---------------------------------------------------------------------------

	private static Text modelLabel(String id) {
		SpeechModels.Entry entry = SpeechModels.byId(id);
		if (entry == null) {
			return Text.literal(id);
		}
		return Text.literal(entry.language() + " · " + entry.megabytes() + " MB");
	}

	private static String nextModel(String id) {
		List<String> ids = new ArrayList<>(SpeechModels.catalogue().keySet());
		int at = ids.indexOf(id);
		return ids.get((at + 1 + ids.size()) % ids.size());
	}

	private static Text deviceLabel(String name) {
		return name == null || name.isBlank()
				? Text.translatable("roleplayersquill.option.voice_device.default")
				: Text.literal(name);
	}

	private static String nextDevice(String current) {
		List<String> names = new ArrayList<>();
		names.add("");
		names.addAll(Microphone.devices());
		int at = names.indexOf(current == null ? "" : current);
		return names.get((at + 1 + names.size()) % names.size());
	}

	// ---- widget shorthands ---------------------------------------------------------------------------

	private ClickableWidget toggle(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return cycle(key, () -> getter.get() ? ScreenTexts.ON : ScreenTexts.OFF, () -> setter.accept(!getter.get()));
	}

	private ClickableWidget cycle(String key, Supplier<Text> value, Runnable onClick) {
		ButtonWidget button = ButtonWidget.builder(label(key, value.get()), b -> {
			onClick.run();
			b.setMessage(label(key, value.get()));
		}).dimensions(0, 0, ROW_WIDTH, 20).build();
		button.setTooltip(Tooltip.of(Text.translatable("roleplayersquill.option." + key + ".tooltip")));
		return button;
	}

	private ClickableWidget textField(String key, String current, Consumer<String> setter) {
		TextFieldWidget field = new TextFieldWidget(this.textRenderer, 0, 0, ROW_WIDTH, 20,
				Text.translatable("roleplayersquill.option." + key));
		field.setMaxLength(128);
		field.setText(current);
		// Written on every keystroke, like everything else here: a field that only committed on
		// enter would silently lose what was typed.
		field.setChangedListener(setter);
		field.setTooltip(Tooltip.of(Text.translatable("roleplayersquill.option." + key + ".tooltip")));
		return field;
	}

	private ClickableWidget slider(String key, float current, float min, float max, Consumer<Float> setter,
			Function<Float, Text> display) {
		return new OptionSlider(key, current, min, max, setter, display);
	}

	private static Text label(String key, Text value) {
		return Text.translatable("roleplayersquill.option." + key).append(": ").append(value);
	}

	private static class OptionSlider extends SliderWidget {
		private final String key;
		private final float min;
		private final float max;
		private final Consumer<Float> setter;
		private final Function<Float, Text> display;

		OptionSlider(String key, float current, float min, float max, Consumer<Float> setter,
				Function<Float, Text> display) {
			super(0, 0, ROW_WIDTH, 20, Text.empty(), MathHelper.clamp((current - min) / (max - min), 0.0f, 1.0f));
			this.key = key;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.display = display;
			setTooltip(Tooltip.of(Text.translatable("roleplayersquill.option." + key + ".tooltip")));
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

	private static class OptionList extends ElementListWidget<OptionList.Entry> {
		OptionList(MinecraftClient client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void addWidget(ClickableWidget widget) {
			addEntry(new WidgetEntry(widget));
		}

		void addHeader(Text text) {
			addEntry(new HeaderEntry(text));
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}

		abstract static class Entry extends ElementListWidget.Entry<Entry> {
		}

		static class WidgetEntry extends Entry {
			private final ClickableWidget widget;

			WidgetEntry(ClickableWidget widget) {
				this.widget = widget;
			}

			@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
					int mouseX, int mouseY, boolean hovered, float tickDelta) {
				widget.setX(x);
				widget.setY(y);
				widget.setWidth(entryWidth);
				widget.render(context, mouseX, mouseY, tickDelta);
			}

			@Override
			public List<? extends Element> children() {
				return List.of(widget);
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of(widget);
			}
		}

		static class HeaderEntry extends Entry {
			private final Text text;

			HeaderEntry(Text text) {
				this.text = text;
			}

			@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
					int mouseX, int mouseY, boolean hovered, float tickDelta) {
				MinecraftClient client = MinecraftClient.getInstance();
				context.drawCenteredTextWithShadow(client.textRenderer, text, x + entryWidth / 2, y + 8, 0xFFE0C070);
			}

			@Override
			public List<? extends Element> children() {
				return List.of();
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of();
			}
		}
	}
}
