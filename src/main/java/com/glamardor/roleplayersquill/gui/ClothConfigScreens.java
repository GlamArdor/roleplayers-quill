package com.glamardor.roleplayersquill.gui;

import com.glamardor.roleplayersquill.config.QuillConfig;
import com.glamardor.roleplayersquill.speech.Microphone;
import com.glamardor.roleplayersquill.speech.SpeechModels;
import com.glamardor.roleplayersquill.text.Alignment;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Cloth Config version of the settings.
 *
 * <p>Only classloaded when Cloth is present, and only ever from inside the try in
 * {@link ConfigScreenFactory}: Cloth is a compile-time dependency here and may simply not be there.
 */
final class ClothConfigScreens {
	/** Cloth's sliders are whole numbers, so fractions are edited in hundredths. */
	private static final int PERCENT = 100;

	private static final List<Runnable> LIVE = new ArrayList<>();

	private ClothConfigScreens() {
	}

	static Screen build(@Nullable Screen parent) {
		QuillConfig config = QuillConfig.get();
		QuillConfig defaults = new QuillConfig();
		LIVE.clear();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.translatable("roleplayersquill.config.title"))
				.setSavingRunnable(() -> {
					LivePreview.markSaved();
					config.save();
				});
		// The editor is sitting behind this menu and half of these settings change how it lays a
		// page out, so the menu does not paint over it.
		builder.setTransparentBackground(true);
		// One long list with the sections down the side rather than tabs: tabbed, the search box
		// only looks inside the tab you happen to be standing in, which is the opposite of a search.
		builder.setGlobalized(true);
		builder.setGlobalizedExpanded(true);

		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory editor = builder.getOrCreateCategory(Text.translatable("roleplayersquill.category.editor"));
		editor.addEntry(toggle(entries, "replace_editor", config.replaceBookEditor, defaults.replaceBookEditor,
				v -> config.replaceBookEditor = v));
		editor.addEntry(percentSlider(entries, "editor_scale", config.editorScale, defaults.editorScale,
				v -> config.editorScale = v, 100, 250));
		editor.addEntry(toggle(entries, "show_counter", config.showCounter, defaults.showCounter,
				v -> config.showCounter = v));
		editor.addEntry(toggle(entries, "show_guides", config.showGuides, defaults.showGuides,
				v -> config.showGuides = v));
		editor.addEntry(toggle(entries, "carry_formatting", config.carryFormatting, defaults.carryFormatting,
				v -> config.carryFormatting = v));
		var alignmentEntry = entries.startEnumSelector(text("default_alignment"), Alignment.class, config.defaultAlignment)
				.setDefaultValue(defaults.defaultAlignment)
				.setEnumNameProvider(value -> ((Alignment) value).label())
				.setTooltip(tooltip("default_alignment"))
				.setSaveConsumer(v -> config.defaultAlignment = v)
				.build();
		editor.addEntry(alignmentEntry);
		LIVE.add(() -> config.defaultAlignment = alignmentEntry.getValue());

		ConfigCategory layout = builder.getOrCreateCategory(Text.translatable("roleplayersquill.category.layout"));
		layout.addEntry(toggle(entries, "hyphenate", config.hyphenate, defaults.hyphenate,
				v -> config.hyphenate = v));
		layout.addEntry(intSlider(entries, "hyphen_min_before", config.hyphenMinBefore, defaults.hyphenMinBefore,
				v -> config.hyphenMinBefore = v, 1, 5));
		layout.addEntry(intSlider(entries, "hyphen_min_after", config.hyphenMinAfter, defaults.hyphenMinAfter,
				v -> config.hyphenMinAfter = v, 1, 5));
		layout.addEntry(toggle(entries, "page_break_hyphen", config.hyphenateAtPageBreak,
				defaults.hyphenateAtPageBreak, v -> config.hyphenateAtPageBreak = v));
		layout.addEntry(toggle(entries, "justify_last_line", config.justifyLastLine, defaults.justifyLastLine,
				v -> config.justifyLastLine = v));

		// Every rule on its own line rather than behind one switch. Which of them is in the way
		// depends entirely on what is being written, and the writer is the one who knows.
		ConfigCategory correct = builder.getOrCreateCategory(Text.translatable("roleplayersquill.category.correct"));
		correct.addEntry(toggle(entries, "auto_correct", config.autoCorrect, defaults.autoCorrect,
				v -> config.autoCorrect = v));
		correct.addEntry(toggle(entries, "auto_quotes", config.autoQuotes, defaults.autoQuotes,
				v -> config.autoQuotes = v));
		correct.addEntry(toggle(entries, "auto_dashes", config.autoDashes, defaults.autoDashes,
				v -> config.autoDashes = v));
		correct.addEntry(toggle(entries, "auto_ellipsis", config.autoEllipsis, defaults.autoEllipsis,
				v -> config.autoEllipsis = v));
		correct.addEntry(toggle(entries, "auto_apostrophe", config.autoApostrophe, defaults.autoApostrophe,
				v -> config.autoApostrophe = v));
		correct.addEntry(toggle(entries, "auto_signs", config.autoSigns, defaults.autoSigns,
				v -> config.autoSigns = v));
		correct.addEntry(toggle(entries, "auto_capitals", config.autoCapitals, defaults.autoCapitals,
				v -> config.autoCapitals = v));

		// The word lists are not here to be chosen between: they are downloaded on demand, and the
		// switches only say which languages this book is likely to be written in.
		ConfigCategory spelling = builder.getOrCreateCategory(Text.translatable("roleplayersquill.category.spelling"));
		spelling.addEntry(toggle(entries, "spell_check", config.spellCheck, defaults.spellCheck,
				v -> config.spellCheck = v));
		spelling.addEntry(toggle(entries, "spell_skip_capitals", config.spellSkipCapitals,
				defaults.spellSkipCapitals, v -> config.spellSkipCapitals = v));
		spelling.addEntry(toggle(entries, "spell_russian", config.spellRussian, defaults.spellRussian,
				v -> config.spellRussian = v));
		spelling.addEntry(toggle(entries, "spell_english", config.spellEnglish, defaults.spellEnglish,
				v -> config.spellEnglish = v));
		spelling.addEntry(toggle(entries, "spell_elsewhere", config.spellElsewhere, defaults.spellElsewhere,
				v -> config.spellElsewhere = v));
		spelling.addEntry(toggle(entries, "spell_auto_download", config.spellAutoDownload,
				defaults.spellAutoDownload, v -> config.spellAutoDownload = v));

		ConfigCategory paste = builder.getOrCreateCategory(Text.translatable("roleplayersquill.category.paste"));
		paste.addEntry(toggle(entries, "auto_paste_pages", config.autoPasteMultiPage, defaults.autoPasteMultiPage,
				v -> config.autoPasteMultiPage = v));
		paste.addEntry(toggle(entries, "confirm_big_paste", config.confirmBigPaste, defaults.confirmBigPaste,
				v -> config.confirmBigPaste = v));
		paste.addEntry(toggle(entries, "import_join_lines", config.importJoinLines, defaults.importJoinLines,
				v -> config.importJoinLines = v));
		paste.addEntry(stringField(entries, "export_folder", config.exportFolder, defaults.exportFolder,
				v -> config.exportFolder = v));

		ConfigCategory links = builder.getOrCreateCategory(Text.translatable("roleplayersquill.category.links"));
		var richEntry = entries.startEnumSelector(text("rich_mode"), QuillConfig.RichMode.class, config.richMode)
				.setDefaultValue(defaults.richMode)
				.setEnumNameProvider(value -> ((QuillConfig.RichMode) value).label())
				.setTooltip(tooltip("rich_mode"))
				.setSaveConsumer(v -> config.richMode = v)
				.build();
		links.addEntry(richEntry);
		LIVE.add(() -> config.richMode = richEntry.getValue());
		links.addEntry(toggle(entries, "highlight_links", config.highlightLinks, defaults.highlightLinks,
				v -> config.highlightLinks = v));
		links.addEntry(toggle(entries, "reader_links", config.readerLinks, defaults.readerLinks,
				v -> config.readerLinks = v));
		links.addEntry(toggle(entries, "reader_links_no_scheme", config.readerLinksWithoutScheme,
				defaults.readerLinksWithoutScheme, v -> config.readerLinksWithoutScheme = v));

		ConfigCategory elsewhere = builder.getOrCreateCategory(Text.translatable("roleplayersquill.category.elsewhere"));
		elsewhere.addEntry(toggle(entries, "sign_editor", config.signEditor, defaults.signEditor,
				v -> config.signEditor = v));
		elsewhere.addEntry(toggle(entries, "anvil_editor", config.anvilEditor, defaults.anvilEditor,
				v -> config.anvilEditor = v));
		elsewhere.addEntry(toggle(entries, "chat_symbols", config.chatSymbols, defaults.chatSymbols,
				v -> config.chatSymbols = v));
		elsewhere.addEntry(toggle(entries, "chat_formatting", config.chatFormatting, defaults.chatFormatting,
				v -> config.chatFormatting = v));
		elsewhere.addEntry(stringField(entries, "chat_prefix", config.chatCodePrefix, defaults.chatCodePrefix,
				v -> config.chatCodePrefix = v));

		ConfigCategory voice = builder.getOrCreateCategory(Text.translatable("roleplayersquill.category.voice"));
		voice.addEntry(toggle(entries, "voice_enabled", config.voiceEnabled, defaults.voiceEnabled,
				v -> config.voiceEnabled = v));
		String[] models = SpeechModels.catalogue().keySet().toArray(new String[0]);
		var modelEntry = entries.startSelector(text("voice_model"), models, pick(models, config.voiceModel))
				.setDefaultValue(defaults.voiceModel)
				.setNameProvider(id -> {
					SpeechModels.Entry model = SpeechModels.byId(id);
					return model == null ? Text.literal(id)
							: Text.literal(model.language() + " · " + model.megabytes() + " MB");
				})
				.setTooltip(tooltip("voice_model"))
				.setSaveConsumer(v -> config.voiceModel = v)
				.build();
		voice.addEntry(modelEntry);
		LIVE.add(() -> config.voiceModel = modelEntry.getValue());
		voice.addEntry(toggle(entries, "voice_auto_download", config.voiceAutoDownload, defaults.voiceAutoDownload,
				v -> config.voiceAutoDownload = v));
		List<String> deviceList = new ArrayList<>();
		deviceList.add("");
		deviceList.addAll(Microphone.devices());
		String[] devices = deviceList.toArray(new String[0]);
		var deviceEntry = entries.startSelector(text("voice_device"), devices, pick(devices, config.voiceDevice))
				.setDefaultValue("")
				.setNameProvider(name -> name == null || name.isBlank()
						? Text.translatable("roleplayersquill.option.voice_device.default")
						: Text.literal(name))
				.setTooltip(tooltip("voice_device"))
				.setSaveConsumer(v -> config.voiceDevice = v)
				.build();
		voice.addEntry(deviceEntry);
		LIVE.add(() -> config.voiceDevice = deviceEntry.getValue());
		voice.addEntry(toggle(entries, "voice_tidy", config.voiceTidyUp, defaults.voiceTidyUp,
				v -> config.voiceTidyUp = v));

		Screen screen = builder.build();
		LivePreview.start(screen, LIVE);
		return screen;
	}

	/** Cloth insists the current value be one of the array's own instances. */
	private static String pick(String[] values, String wanted) {
		for (String value : values) {
			if (value.equals(wanted == null ? "" : wanted)) {
				return value;
			}
		}
		return values.length > 0 ? values[0] : "";
	}

	private static AbstractConfigListEntry<?> toggle(ConfigEntryBuilder entries, String key, boolean value,
			boolean fallback, Consumer<Boolean> save) {
		var entry = entries.startBooleanToggle(text(key), value)
				.setDefaultValue(fallback)
				.setTooltip(tooltip(key))
				.setSaveConsumer(save)
				.build();
		LIVE.add(() -> save.accept(entry.getValue()));
		return entry;
	}

	private static AbstractConfigListEntry<?> stringField(ConfigEntryBuilder entries, String key, String value,
			String fallback, Consumer<String> save) {
		var entry = entries.startStrField(text(key), value)
				.setDefaultValue(fallback)
				.setTooltip(tooltip(key))
				.setSaveConsumer(save)
				.build();
		LIVE.add(() -> save.accept(entry.getValue()));
		return entry;
	}

	private static AbstractConfigListEntry<?> intSlider(ConfigEntryBuilder entries, String key, int value,
			int fallback, Consumer<Integer> save, int min, int max) {
		IntegerSliderEntry entry = entries.startIntSlider(text(key), value, min, max)
				.setDefaultValue(fallback)
				.setTooltip(tooltip(key))
				.setSaveConsumer(save)
				.build();
		LIVE.add(() -> save.accept(entry.getValue()));
		return entry;
	}

	private static AbstractConfigListEntry<?> percentSlider(ConfigEntryBuilder entries, String key, float value,
			float fallback, Consumer<Float> save, int min, int max) {
		IntegerSliderEntry entry = entries.startIntSlider(text(key), Math.round(value * PERCENT), min, max)
				.setDefaultValue(Math.round(fallback * PERCENT))
				.setTextGetter(v -> Text.literal(v + "%"))
				.setTooltip(tooltip(key))
				.setSaveConsumer(v -> save.accept(v / (float) PERCENT))
				.build();
		LIVE.add(() -> save.accept(entry.getValue() / (float) PERCENT));
		return entry;
	}

	private static Text text(String key) {
		return Text.translatable("roleplayersquill.option." + key);
	}

	private static Text[] tooltip(String key) {
		return new Text[] { Text.translatable("roleplayersquill.option." + key + ".tooltip") };
	}
}
