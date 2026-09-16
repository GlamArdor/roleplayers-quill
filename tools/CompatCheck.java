import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Checks that one jar really does fit every game version it claims.
 *
 * <p>A remapped Fabric jar refers to Minecraft by intermediary name – {@code class_332},
 * {@code method_25294} – and those names are stable across versions by design: the same name means
 * the same member for as long as that member exists. So whether this jar runs on a given version is
 * a question with an exact answer, and it is this: does every intermediary class, field and method
 * the jar names, with the exact descriptor it names it by, appear in that version's intermediary
 * mappings?
 *
 * <p>Both halves matter. A missing name is a member that was deleted or added later; a name whose
 * descriptor no longer matches is a member whose signature was changed, which crashes just as hard
 * and is far easier to miss – {@code InGameHud.render} is the same name either side of 1.20.5 and
 * took a bare float on one side of it.
 *
 * <p>Mixin targets are checked too, out of the refmap, since those are resolved by name at load
 * time and never appear in the bytecode.
 *
 * <pre>
 * javac -d build/tools-compat tools/CompatCheck.java
 * java -cp build/tools-compat CompatCheck build/libs/&lt;the jar&gt; build/mappings
 * </pre>
 */
public final class CompatCheck {

	private static final Pattern MEMBER = Pattern.compile("(class_\\d+|method_\\d+|field_\\d+)");
	/** A refmap value: {@code Lnet/minecraft/class_329;render(Lnet/minecraft/class_332;)V} */
	private static final Pattern REFMAP = Pattern.compile(
			"L(net/minecraft/class_\\d+);(method_\\d+|field_\\d+|[A-Za-z_$][\\w$]*)([;(].*)?");

	/** One thing the jar refers to. */
	private record Reference(String kind, String name, String descriptor, String where) {
		String key() {
			return descriptor == null ? name : name + descriptor;
		}

		@Override
		public String toString() {
			return descriptor == null ? name + "  (" + where + ")"
					: name + descriptor + "  (" + where + ")";
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("usage: CompatCheck <mod jar> <mappings dir>");
			System.exit(2);
		}
		Path jar = Path.of(args[0]);
		Path mappingsDir = Path.of(args[1]);

		List<Reference> references = readJar(jar);
		System.out.printf("%s refers to %d distinct Minecraft members%n%n",
				jar.getFileName(), references.size());

		List<Path> versions = new ArrayList<>();
		try (var stream = Files.list(mappingsDir)) {
			stream.filter(path -> path.getFileName().toString().endsWith(".jar"))
					.forEach(versions::add);
		}
		versions.sort(Comparator.comparing(CompatCheck::versionKey));

		boolean allClear = true;
		for (Path version : versions) {
			String name = version.getFileName().toString().replace(".jar", "");
			Mappings mappings = Mappings.read(version);
			List<Reference> missing = new ArrayList<>();
			List<Reference> changed = new ArrayList<>();
			for (Reference reference : references) {
				if (reference.kind().equals("class")) {
					if (!mappings.classes.contains(reference.name())) {
						missing.add(reference);
					}
					continue;
				}
				Set<String> names = reference.kind().equals("method") ? mappings.methodNames : mappings.fieldNames;
				Set<String> signatures = reference.kind().equals("method")
						? mappings.methodSignatures : mappings.fieldSignatures;
				if (!names.contains(reference.name())) {
					missing.add(reference);
				} else if (!signatures.contains(reference.key())) {
					changed.add(reference);
				}
			}
			if (missing.isEmpty() && changed.isEmpty()) {
				System.out.printf("%-8s OK%n", name);
				continue;
			}
			allClear = false;
			System.out.printf("%-8s %d missing, %d with a different signature%n",
					name, missing.size(), changed.size());
			for (Reference reference : missing) {
				System.out.println("           missing  " + reference);
			}
			for (Reference reference : changed) {
				System.out.println("           changed  " + reference);
			}
		}
		System.out.println();
		System.out.println(allClear
				? "Every version listed can load this jar."
				: "Some versions cannot: see above.");
	}

	private static String versionKey(Path path) {
		String[] parts = path.getFileName().toString().replace(".jar", "").split("\\.");
		StringBuilder key = new StringBuilder();
		for (int i = 0; i < 3; i++) {
			key.append(String.format("%03d", i < parts.length ? Integer.parseInt(parts[i]) : 0));
		}
		return key.toString();
	}

	// ── The jar ──────────────────────────────────────────────────────────────

	private static List<Reference> readJar(Path jar) throws IOException {
		Map<String, Reference> found = new HashMap<>();
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			var entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.getName().endsWith(".class")) {
					try (InputStream in = zip.getInputStream(entry)) {
						readClass(in, entry.getName(), found);
					}
				} else if (entry.getName().endsWith("refmap.json")) {
					try (InputStream in = zip.getInputStream(entry)) {
						readRefmap(new String(in.readAllBytes(), StandardCharsets.UTF_8),
								entry.getName(), found);
					}
				} else if (entry.getName().endsWith(".jar")) {
					// Nested jars are somebody else's problem, and we ship none.
					try (ZipInputStream nested = new ZipInputStream(zip.getInputStream(entry))) {
						while (nested.getNextEntry() != null) {
							// drained deliberately
						}
					}
				}
			}
		}
		List<Reference> list = new ArrayList<>(found.values());
		list.sort(Comparator.comparing(Reference::name));
		return list;
	}

	private static void readRefmap(String json, String where, Map<String, Reference> found) {
		Matcher quoted = Pattern.compile("\"([^\"]*)\"").matcher(json);
		while (quoted.find()) {
			String value = quoted.group(1);
			Matcher target = REFMAP.matcher(value);
			if (target.matches()) {
				add(found, "class", target.group(1).substring("net/minecraft/".length()), null, where);
				String member = target.group(2);
				String rest = target.group(3);
				if (member.startsWith("method_") && rest != null && rest.startsWith("(")) {
					add(found, "method", member, rest, where);
				} else if (member.startsWith("field_") && rest != null && rest.startsWith(":")) {
					add(found, "field", member, rest.substring(1), where);
				}
			}
			// Plain class names in the refmap, e.g. a mixin target.
			Matcher plain = Pattern.compile("^net/minecraft/(class_\\d+)$").matcher(value);
			if (plain.matches()) {
				add(found, "class", plain.group(1), null, where);
			}
		}
	}

	private static void add(Map<String, Reference> found, String kind, String name,
			String descriptor, String where) {
		Reference reference = new Reference(kind, name, descriptor, where);
		found.putIfAbsent(kind + ":" + reference.key(), reference);
	}

	/** Reads a class file as far as its constant pool, which is where every reference lives. */
	private static void readClass(InputStream stream, String where, Map<String, Reference> found)
			throws IOException {
		DataInputStream in = new DataInputStream(stream);
		if (in.readInt() != 0xCAFEBABE) {
			return;
		}
		in.readUnsignedShort();
		in.readUnsignedShort();
		int count = in.readUnsignedShort();
		String[] utf8 = new String[count];
		int[][] refs = new int[count][];
		int[] classNames = new int[count];
		int[][] nameAndTypes = new int[count][];

		for (int i = 1; i < count; i++) {
			int tag = in.readUnsignedByte();
			switch (tag) {
				case 1 -> utf8[i] = in.readUTF();
				case 7 -> classNames[i] = in.readUnsignedShort();
				case 8, 16, 19, 20 -> in.readUnsignedShort();
				case 15 -> {
					in.readUnsignedByte();
					in.readUnsignedShort();
				}
				case 3, 4, 17, 18 -> in.readInt();
				case 5, 6 -> {
					in.readLong();
					i++;
				}
				case 9, 10, 11 -> refs[i] = new int[]{tag, in.readUnsignedShort(), in.readUnsignedShort()};
				case 12 -> nameAndTypes[i] = new int[]{in.readUnsignedShort(), in.readUnsignedShort()};
				default -> throw new IOException("unknown constant pool tag " + tag + " in " + where);
			}
		}

		for (int i = 1; i < count; i++) {
			if (classNames[i] != 0) {
				String name = utf8[classNames[i]];
				if (name != null) {
					for (String member : membersIn(name)) {
						if (member.startsWith("class_")) {
							add(found, "class", member, null, where);
						}
					}
				}
			}
			if (refs[i] == null) {
				continue;
			}
			int[] nat = nameAndTypes[refs[i][2]];
			if (nat == null) {
				continue;
			}
			String name = utf8[nat[0]];
			String descriptor = utf8[nat[1]];
			if (name == null || descriptor == null) {
				continue;
			}
			// Descriptors carry class references of their own, and those matter as much.
			for (String member : membersIn(descriptor)) {
				if (member.startsWith("class_")) {
					add(found, "class", member, null, where);
				}
			}
			if (name.startsWith("method_")) {
				add(found, "method", name, descriptor, where);
			} else if (name.startsWith("field_")) {
				add(found, "field", name, descriptor, where);
			}
		}
	}

	private static Set<String> membersIn(String text) {
		Set<String> names = new LinkedHashSet<>();
		Matcher matcher = MEMBER.matcher(text);
		while (matcher.find()) {
			names.add(matcher.group(1));
		}
		return names;
	}

	// ── The mappings ─────────────────────────────────────────────────────────

	/** One version's intermediary names, with every descriptor translated into intermediary too. */
	private record Mappings(Set<String> classes, Set<String> methodNames, Set<String> fieldNames,
			Set<String> methodSignatures, Set<String> fieldSignatures) {

		static Mappings read(Path intermediaryJar) throws IOException {
			String text;
			try (ZipFile zip = new ZipFile(intermediaryJar.toFile())) {
				ZipEntry entry = zip.getEntry("mappings/mappings.tiny");
				if (entry == null) {
					throw new IOException("no mappings in " + intermediaryJar);
				}
				try (InputStream in = zip.getInputStream(entry)) {
					text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				}
			}

			Map<String, String> officialToIntermediary = new HashMap<>();
			List<String> lines = text.lines().toList();
			for (String line : lines) {
				if (line.startsWith("c\t")) {
					String[] parts = line.split("\t");
					if (parts.length >= 3) {
						officialToIntermediary.put(parts[1], parts[2]);
					}
				}
			}

			Set<String> classes = new TreeSet<>();
			Set<String> methodNames = new HashSet<>();
			Set<String> fieldNames = new HashSet<>();
			Set<String> methodSignatures = new HashSet<>();
			Set<String> fieldSignatures = new HashSet<>();
			for (String intermediary : officialToIntermediary.values()) {
				String simple = intermediary.substring(intermediary.lastIndexOf('/') + 1);
				classes.add(simple);
				// Nested classes are written class_1140$class_11518, and the jar refers to the inner
				// one by its own name, so both halves count as present.
				for (String part : simple.split("\\$")) {
					classes.add(part);
				}
			}
			for (String line : lines) {
				if (!line.startsWith("\tm\t") && !line.startsWith("\tf\t")) {
					continue;
				}
				String[] parts = line.split("\t");
				if (parts.length < 5) {
					continue;
				}
				String descriptor = translate(parts[2], officialToIntermediary);
				String name = parts[4];
				if (line.startsWith("\tm\t")) {
					methodNames.add(name);
					methodSignatures.add(name + descriptor);
				} else {
					fieldNames.add(name);
					fieldSignatures.add(name + descriptor);
				}
			}
			return new Mappings(classes, methodNames, fieldNames, methodSignatures, fieldSignatures);
		}

		/** Rewrites the class names inside a descriptor from official to intermediary. */
		private static String translate(String descriptor, Map<String, String> officialToIntermediary) {
			StringBuilder out = new StringBuilder(descriptor.length());
			int i = 0;
			while (i < descriptor.length()) {
				char c = descriptor.charAt(i);
				if (c != 'L') {
					out.append(c);
					i++;
					continue;
				}
				int end = descriptor.indexOf(';', i);
				if (end < 0) {
					out.append(descriptor.substring(i));
					break;
				}
				String owner = descriptor.substring(i + 1, end);
				out.append('L').append(officialToIntermediary.getOrDefault(owner, owner)).append(';');
				i = end + 1;
			}
			return out.toString();
		}
	}
}
