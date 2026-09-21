# Roleplayer's Quill

A book editor for people who write in Minecraft. Select a passage, make it bold, colour it, align it
left, centre, right or justified – and the book that comes out is an ordinary vanilla book that
everybody can read, with no mod and no resource pack.

Client side only. The server is never told this exists.

> **Status:** in use on a roleplay server where most people do not have it, which is the case that
> matters. Every piece of arithmetic in here is checked by `tools/LayoutCheck.java`, which lays pages
> out against a font of known widths and measures what the encoder actually wrote – 995 checks as
> this is written. The ones that count hardest are about other readers: a page this mod did not
> touch goes back out character for character, and a page it did write is an ordinary book page for
> everybody else.

## What it does

**Formatting a selection.** Bold, italic, underline, strikethrough, obfuscated, and colour. The
toolbar shows what the caret is standing in, so the bold button looks bold inside a bold word.

**Alignment, to the pixel.** Left, centre, right and justified, including hanging indents for lists.
This is the part that needed thinking about, and there is a section on it below.

**Filling the page.** Optional hyphenation, by the rules of Russian or of English, so the lines
fill up instead of fraying. On the test text it saves one line in ten and takes the average line
from 101 pixels to 105 of the 114 available.

**Pasting a chapter.** Paste more than a page and it flows across as many pages as it needs, broken
at line ends and checked against both limits the game keeps – fourteen lines of height and 1024
characters of length. A word the page break lands inside is either hyphenated or moved down whole,
whichever you asked for. Typing past the bottom of a page does the same thing rather than refusing
the keystroke.

**Lists, tables and rules.** Bulleted, dashed, numbered and lettered lists that renumber themselves.
Tables typed as text and laid out in real columns. Horizontal rules.

**A character browser.** Shelves of hand-picked symbols – the quotation marks a Russian typesetter
uses, box drawing, dice faces, runes – and a search across eleven thousand characters by their
Unicode names.

**A format brush.** Pick formatting up from one place and paint it onto another, like the one in
Word.

**Finding.** `Ctrl+F` opens a strip below the book, the size of a browser's find bar, and the caret
moves to the match as the word is typed. Replacing is a window of its own, one button away. There is
also a search across every book you have written, which answers the question a writer actually has:
which book was that in.

**Spelling.** Words nothing recognises are underlined in red, and a right click offers what was
probably meant, "add to dictionary", and "skip this one". The count for the whole book is on the
button that switches it on, and `Shift+F7` walks the words one at a time across every page. The chat
box and the sign editor are checked by the same dictionary. Two things make it usable in a book of
roleplay rather than infuriating: a word that begins with a capital is left alone, because that is
what an invented name looks like, and the dictionary you add to is a plain text file in the config
folder, so fifty place names are fifty lines pasted in once. The word lists are not shipped – they
are somebody else's work and bigger than this whole mod – and are fetched the first time the check
is switched on.

**A blank that will not break.** `Ctrl+Shift+Space` puts in the space that holds two words together,
so "10 kg" and "p. 7" stay on one line. A book cannot hold such a character – the game breaks a line
at a space and at nothing else – so what holds it together is where the line breaks are written, and
the paragraph is only written out line by line where leaving it to the game would have parted the
pair.

**The book's history.** Twenty versions are kept per book, every time one is really written back,
with the page itself shown beside the list of dates – because a list of dates does not say which
evening's work is which. Restoring is an ordinary edit and a `Ctrl+Z` undoes it. A book stays the
same book when the server changes it from underneath – a plugin that tears a page out, say – because
it is recognised by the pages it still has rather than by being byte for byte what it was.

**Blocks you type over and over.** A dateline and a signature, ready made, alongside the page
templates. The signature takes the name off the tab list rather than the account, without its colours
and without the `[rank]` in front of it: on a roleplay server those are two different people.

**Import and export.** Read a chapter out of a text file through the system's own file picker; write
the book back out as text, or as a format that keeps every paragraph, alignment, link and colour so
it can be imported again unchanged.

**Voice typing.** Offline, on your own machine, in Russian or English. Nothing is uploaded and
nothing is stored. The model is not shipped with the mod and is fetched the first time dictation is
switched on – which is why it is a setting rather than a button.

**Links and tooltips**, where the game allows them. See below.

**Signs, anvils and chat** get a formatting bar too, with the truth about what each of them can
actually carry.

**Sneak to get out of the way.** Opening a book while sneaking opens the vanilla editor instead.
Other mods add to that screen – [ImagineBook](https://modrinth.com/mod/imaginebook) puts pictures on
a page through it – and replacing it outright would mean quietly removing a feature this mod knows
nothing about.

## The thing that makes it work

A book page leaves the client as a plain string. `BookUpdateC2SPacket` carries
`List<String>`, and the server turns the strings into text components itself. So everything the
editor can do has to survive as characters in that string.

One thing does: **the section sign**. Chat is checked for it and a client that sends one is
disconnected; the anvil runs the typed name through `StringHelper.stripInvalidChars`, whose entire
purpose is to remove it. Books are not checked at all. `ServerPlayNetworkHandler.onBookUpdate`
passes the pages through untouched, and the book renderer resolves the codes exactly as it resolves
the ones in a sign. That is what carries bold, italic, underline, strikethrough, obfuscation and
sixteen colours.

Alignment is the interesting half, because no code says "centre this". The only way to move text to
the right is to put something in front of it, and the only thing that can go in front of it without
being seen is a space.

A space is four pixels. A **bold** space is five, because bold widens every glyph by one – and
`§l` is written once for a run, not once per space. So a run of spaces is worth `4n + b` pixels for
`n` spaces of which `b` are bold, and choosing `n` and `b` gives every width from eight upwards and
all but three below it:

```java
int count = (int) Math.floor(target / space);
int bold  = Math.min(count, (int) Math.floor((target - count * space) / boldGain));
```

Four pixels and up, that lands within two pixels of any target, and two pixels is a quarter of a
letter. Below four there is nothing to pad with at all, which only matters for a line that is
already within three pixels of full width.

Justification is the same sum applied between the words instead of in front of them, and it comes
out exact: every justified line in the test text finishes flush against the right margin.

A horizontal rule is the same trick again – a run of blanks with `§n` on it is an underline, and an
underline is a line of exactly the width you paid for.

None of this needs the reader to have anything installed. The book is a vanilla book with spaces in
it.

### Costing nothing when nothing is asked for

A paragraph that wants no pixels is written with none. Plain text, left aligned, no list and no
frame, goes out as one paragraph with the game's own line breaking left to do the wrapping – not as
a line break per line, which is what an editor that lays everything out would naturally emit.

It matters twice over. A book written without this mod and opened with it goes back unchanged, so
nothing of somebody else's formatting is rearranged. And a server plugin that tears a page out and
copies it onto paper sees the paragraph it expects: a page full of line breaks came out on the paper
double spaced, and a page with none comes out looking the way it was written.

The same goes for the ink. Inside a book black and "no colour at all" are the same thing, which is
why turning formatting off used to be spelled `§0` – the one code that resets everything and always
means it. On a torn page, where the ink is the pale grey of an item's lore, black is black, and a
sentence that merely followed a coloured word arrived unreadable. So text nobody formatted now
leaves the book saying nothing whatsoever about its colour, which takes some care: `§r` means "back
to the style in force at the last line break", so it is only written where the writer put that break
in itself and knows it was plain. Pages that would otherwise have to fall back to black ink are
written a second time, spending two characters at the end of any line that hands its formatting on –
and, inside a paragraph the game breaks up by itself, writing the blank it breaks at plain, which
costs nothing at all, since that blank is thrown away before anybody sees it.

A page written by an older version of this mod mends itself the next time the book is signed: `§0`
is read back as no colour rather than as a colour somebody chose, and a page carrying one is written
again rather than handed back untouched. Nothing else is rewritten, and nothing about the page in
the book changes – black and no colour draw the same on parchment.

### What it cannot do, and what creative mode does about it

A link needs a `clickEvent`, and a `clickEvent` needs the page to be a text component rather than a
string. There is no way to send one with `BookUpdateC2SPacket`.

There is one way to send one at all. A creative player's client may hand the server a finished
item – that is what taking something out of the creative menu is – and
`ServerPlayNetworkHandler.onCreativeInventoryAction` accepts the stack as given after a single
check:

```java
if (this.player.isInCreativeMode()) { ... }
```

Not a permission. Not an operator level. Creative mode, which a roleplay server's builders usually
have. So in creative the book is written as components, and it carries real links, tooltips, commands,
page jumps and exact `#RRGGBB` colours, at 32767 characters a page instead of 1024 – and every
reader sees them, mod or no mod, because the book reader has always known how to follow a click
event.

Everywhere else the editor says so before you sign, and writes what it can.

Separately from all that, **any** book becomes clickable when you read it: an address written on a
page is turned into a link on the way to the screen, with the game's own confirmation dialog. That
changes nothing in the book and sends nothing anywhere; a reader without the mod still just sees the
address.

## Settings

`config/roleplayersquill.json`, or the settings screen – Cloth Config's if you have it, a built-in
one if you do not. Both have the same options.

The ones worth knowing about:

- **Page size.** The vanilla page is small. This draws it up to two and a half times larger while
  writing, without changing anything about the book.
- **Hyphenate.** Off by default: it changes how prose reads, and that should be a decision.
- **Hyphenate across pages.** Off means a word the page break lands inside travels down whole.
- **Links and exact colour.** When to write a page as a component. `In creative` is the default.
- **Code character.** What the chat and anvil bars insert. An ampersand by default, because a
  section sign in chat disconnects you.

## Voice typing

The recognition engine is [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) and the models are
the ones it publishes. Nothing is sent anywhere: the audio goes from the microphone into a model on
your disk and comes back as text in the same process.

The Russian default is GigaAM v3 RNN-T, which writes its own punctuation and capitals and is 162 MB.
English uses NVIDIA's Parakeet. Neither is shipped with the mod; both are downloaded the first time
dictation is used, and only then.

If [Voice Subtitles](https://modrinth.com/mod/voice-subtitles) is installed, its copy of the engine
and of the model is used and nothing is downloaded twice.

## Keys

| | |
|---|---|
| `Ctrl+B` `Ctrl+I` `Ctrl+U` | bold, italic, underline |
| `Ctrl+Shift+S` `Ctrl+Shift+O` | strikethrough, obfuscated |
| `Ctrl+L` `Ctrl+E` `Ctrl+R` `Ctrl+J` | left, centre, right, justify |
| `Tab` `Shift+Tab` | indent, outdent |
| `Ctrl+Z` `Ctrl+Y` | undo, redo |
| `Ctrl+C` `Ctrl+X` `Ctrl+V` | copy, cut, paste |
| `Ctrl+Shift+C` `Ctrl+Shift+V` | pick up formatting, paint it on |
| `Ctrl+K` `Ctrl+G` `Ctrl+T` | link, symbols, table |
| `Ctrl+F` | find |
| `Ctrl+H` | hyphenation on and off |
| `Ctrl+Shift+Space` | a space the line will not break at |
| `F7` `Shift+F7` | spelling on and off, next word not recognised |
| `Ctrl+Enter` `Page Up` `Page Down` | new page, turn back, turn on |
| `Insert` | dictate |

## Versions

One jar, `1.21.6` to `1.21.8`. The range was not decided by eye: `tools/CompatCheck.java` reads the
built jar, pulls out every reference it makes to Minecraft – after remapping those are intermediary
names, which are stable across versions by construction – and asks each version's mappings whether
it has that member with that exact signature.

```
./gradlew compatCheck
```

```
roleplayers-quill-1.0.0+1.21.6-1.21.8.jar refers to 259 distinct Minecraft members

1.21.6   OK
1.21.7   OK
1.21.8   OK
```

It stops at 1.21.8 on one side because 1.21.9 rewrote the GUI, and at 1.21.6 on the other because
1.21.6 rewrote the render stack. Neither is a mapping change that a jar can be made to straddle.

## Checking the arithmetic

The alignment is a sum over glyph advances, and a sum that is quietly two pixels out looks fine in a
screenshot and wrong in a book. So it is checked, without a game:

```
./gradlew layoutCheck
```

`tools/LayoutCheck.java` replaces the font with one whose widths are known, lays pages out, writes
them, and then **measures what was written** the way `TextHandler` will measure it – walking the
string, resolving the codes, adding up. Measuring the output rather than the model is the point: a
bug that affected the layout and the encoder identically would pass a test that only asked the
layout what it thought.

It checks that padding lands within two pixels, that centred and right-aligned lines sit where they
claim to, that justified lines reach the margin, that a page read back and written again is the same
page, that hyphenation breaks Russian and English words where a reader expects, that pagination
loses nothing and stays inside both limits, and – the one that matters most – that **the game will
not wrap the page again**. Every line this mod writes ends in a real line break; if one of them came
out a pixel too wide the game would break it in two, the page would grow a line it was not supposed
to have, and the last line would fall off the bottom.

```
== The game will not re-wrap what was written
  44 lines laid out, 44 after the game wraps them
```

The spelling has a check of its own, because none of it can be seen in a screenshot either: the
Russian list is Windows-1251 and is decoded by hand, the index is eight bytes a word, and the
suggestions are generated rather than looked up.

```
./gradlew spellCheck
```

It fetches both lists into `build/dictionaries`, builds the indexes, reads them back, and asks about
words whose answers are known:

```
== ru
  1528698 words, built in 662 ms, read in 19 ms, 11 MB on disk
  кнга → [кинга, книга, куга, кн га, инга, юнга]
  какбы → [кабы, как бы]
```

## Building

```
./gradlew build
```

The jar lands in `build/libs`. Java 21.

## Thanks

To [Stendhal](https://modrinth.com/mod/stendhal), which did this first and showed that the section
sign survives a book.

To [danakt/russian-words](https://github.com/danakt/russian-words) (MIT) and
[dwyl/english-words](https://github.com/dwyl/english-words) for the spelling lists. Neither is
shipped inside this mod; both are fetched from their own repositories when the check is first
switched on, and what is kept afterwards is an index rather than the words.

To [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), which does the listening. The Java bindings
under `src/main/java/com/k2fsa/sherpa/onnx` are theirs, carried along unchanged; the native library
and the recognition models are downloaded from their releases the first time dictation is switched
on, and are not shipped in this jar. Their authors are named in the file headers.

## Licence

LGPL-3.0-or-later, except the sherpa-onnx bindings described above, which are Apache-2.0: the text
is in `licenses/sherpa-onnx-LICENSE.txt`. The recognition models are not part of this repository and
each carries its own licence, stated where it is published.
