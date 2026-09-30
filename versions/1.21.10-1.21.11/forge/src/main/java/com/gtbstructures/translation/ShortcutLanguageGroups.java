package com.gtbstructures.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtbstructures.stats.LangPatterns;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Immutable configuration model for independently evaluated shortcut language groups. */
public final class ShortcutLanguageGroups {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_NAME_CODE_POINTS = 32;
    public static final String DEFAULT_SERIALIZED =
            "{\"schemaVersion\":1,\"enabled\":false,\"groups\":[]}";

    public static final String ERROR_GROUP_NOT_FOUND = "group_not_found";
    public static final String ERROR_NAME_REQUIRED = "name_required";
    public static final String ERROR_NAME_TOO_LONG = "name_too_long";
    public static final String ERROR_DUPLICATE_NAME = "duplicate_name";
    public static final String ERROR_LANGUAGES_REQUIRED = "languages_required";
    public static final String ERROR_UNKNOWN_LANGUAGE = "unknown_language";

    private static State state = State.empty();

    private ShortcutLanguageGroups() {}

    public static synchronized State snapshot() {
        return new State(state.enabled, state.groups);
    }

    public static synchronized void restore(State replacement) {
        state = replacement == null ? State.empty()
                : new State(replacement.enabled, replacement.groups);
    }

    public static synchronized boolean isEnabled() {
        return state.enabled;
    }

    public static synchronized boolean setEnabled(boolean enabled) {
        if (state.enabled == enabled) return false;
        state = new State(enabled, state.groups);
        return true;
    }

    public static synchronized List<Group> groups() {
        return state.groups;
    }

    public static synchronized Result create(String name, Collection<String> languages) {
        return create(name, false, false, languages);
    }

    public static synchronized Result create(String name, boolean rawTranslation,
                                             boolean rawTranslationAllLowercase,
                                             Collection<String> languages) {
        Validation validation = validate(name, languages, null,
                Collections.<String>emptySet());
        if (!validation.ok()) return Result.error(validation.error, validation.detail);
        List<Group> updated = new ArrayList<Group>(state.groups);
        Group group = new Group(UUID.randomUUID().toString(), validation.name,
                true, rawTranslation, rawTranslationAllLowercase,
                validation.languages);
        updated.add(group);
        state = new State(state.enabled, updated);
        return Result.success(group);
    }

    public static synchronized Result update(String id, String name, boolean enabled,
                                             Collection<String> languages) {
        int index = indexOfId(id);
        if (index < 0) return Result.error(ERROR_GROUP_NOT_FOUND, id);
        Group current = state.groups.get(index);
        return update(id, name, enabled, current.rawTranslation,
                current.rawTranslationAllLowercase, languages);
    }

    public static synchronized Result update(String id, String name, boolean enabled,
                                             boolean rawTranslation,
                                             boolean rawTranslationAllLowercase,
                                             Collection<String> languages) {
        int index = indexOfId(id);
        if (index < 0) return Result.error(ERROR_GROUP_NOT_FOUND, id);
        Set<String> preservedUnknown = new HashSet<String>();
        for (String code : state.groups.get(index).languages) {
            if (!isSupportedLanguage(code)) preservedUnknown.add(code);
        }
        Validation validation = validate(name, languages, id, preservedUnknown);
        if (!validation.ok()) return Result.error(validation.error, validation.detail);
        Group group = new Group(state.groups.get(index).id, validation.name,
                enabled, rawTranslation, rawTranslationAllLowercase,
                validation.languages);
        List<Group> updated = new ArrayList<Group>(state.groups);
        updated.set(index, group);
        state = new State(state.enabled, updated);
        return Result.success(group);
    }

    public static synchronized Result rename(int oneBasedIndex, String name) {
        Group group = groupAt(oneBasedIndex);
        if (group == null) return Result.error(ERROR_GROUP_NOT_FOUND,
                Integer.toString(oneBasedIndex));
        return update(group.id, name, group.enabled, group.languages);
    }

    public static synchronized Result setLanguages(int oneBasedIndex,
                                                    Collection<String> languages) {
        Group group = groupAt(oneBasedIndex);
        if (group == null) return Result.error(ERROR_GROUP_NOT_FOUND,
                Integer.toString(oneBasedIndex));
        return update(group.id, group.name, group.enabled, languages);
    }

    public static synchronized Result setGroupEnabled(int oneBasedIndex, boolean enabled) {
        Group group = groupAt(oneBasedIndex);
        if (group == null) return Result.error(ERROR_GROUP_NOT_FOUND,
                Integer.toString(oneBasedIndex));
        return update(group.id, group.name, enabled, group.languages);
    }

    public static synchronized Result setRawTranslation(int oneBasedIndex, boolean enabled) {
        Group group = groupAt(oneBasedIndex);
        if (group == null) return Result.error(ERROR_GROUP_NOT_FOUND,
                Integer.toString(oneBasedIndex));
        return update(group.id, group.name, group.enabled, enabled,
                group.rawTranslationAllLowercase, group.languages);
    }

    public static synchronized Result setRawTranslationAllLowercase(
            int oneBasedIndex, boolean enabled) {
        Group group = groupAt(oneBasedIndex);
        if (group == null) return Result.error(ERROR_GROUP_NOT_FOUND,
                Integer.toString(oneBasedIndex));
        return update(group.id, group.name, group.enabled, group.rawTranslation,
                enabled, group.languages);
    }

    public static synchronized Result delete(int oneBasedIndex) {
        Group group = groupAt(oneBasedIndex);
        if (group == null) return Result.error(ERROR_GROUP_NOT_FOUND,
                Integer.toString(oneBasedIndex));
        List<Group> updated = new ArrayList<Group>(state.groups);
        updated.remove(oneBasedIndex - 1);
        state = new State(state.enabled, updated);
        return Result.success(group);
    }

    public static synchronized Group groupAt(int oneBasedIndex) {
        return oneBasedIndex < 1 || oneBasedIndex > state.groups.size()
                ? null : state.groups.get(oneBasedIndex - 1);
    }

    public static synchronized LoadReport load(String primary, String backup) {
        ParseResult parsed = parse(primary, true);
        if (parsed.validRoot) {
            state = parsed.state;
            return new LoadReport(false, parsed.warnings);
        }

        List<String> warnings = new ArrayList<String>(parsed.warnings);
        ParseResult recovered = parse(backup, false);
        if (recovered.validRoot) {
            warnings.add("Recovered shortcut language groups from the backup config value.");
            warnings.addAll(recovered.warnings);
            state = recovered.state;
            return new LoadReport(true, warnings);
        }

        warnings.addAll(recovered.warnings);
        state = State.empty();
        if (primary != null && !primary.trim().isEmpty()) {
            warnings.add("Reset shortcut language groups because neither config value was usable.");
        }
        return new LoadReport(false, warnings);
    }

    public static synchronized String serialize() {
        return serialize(state);
    }

    public static boolean isValidSerialized(String raw) {
        return parse(raw, false).validRoot;
    }

    public static List<String> supportedLanguageCodes() {
        LinkedHashSet<String> codes = new LinkedHashSet<String>();
        for (String code : LangPatterns.languageCodes()) {
            String normalized = normalizeLanguageCode(code);
            if (normalized != null && !normalized.isEmpty()) codes.add(normalized);
        }
        return Collections.unmodifiableList(new ArrayList<String>(codes));
    }

    public static List<String> orderedSupportedLanguages(Collection<String> selected) {
        if (selected == null || selected.isEmpty()) return Collections.emptyList();
        Set<String> normalizedSelected = new HashSet<String>();
        for (String code : selected) {
            String normalized = normalizeLanguageCode(code);
            if (normalized != null) normalizedSelected.add(normalized);
        }
        List<String> ordered = new ArrayList<String>();
        for (String code : supportedLanguageCodes()) {
            if (normalizedSelected.contains(code)) ordered.add(code);
        }
        return ordered;
    }

    public static boolean isSupportedLanguage(String code) {
        String normalized = normalizeLanguageCode(code);
        return normalized != null && supportedLanguageCodes().contains(normalized);
    }

    public static String languageDisplayName(String code) {
        String normalized = normalizeLanguageCode(code);
        if (normalized == null) return "";
        String lookup = "ptbr".equals(normalized) ? "pt_br" : normalized;
        return LangPatterns.mapLanguageName(lookup);
    }

    public static String normalizeLanguageCode(String code) {
        if (code == null) return null;
        String trimmed = Normalizer.normalize(code.trim(), Normalizer.Form.NFC);
        if (trimmed.isEmpty()) return null;
        return TranslationData.normalizeLangCode(trimmed).toLowerCase(Locale.ROOT);
    }

    private static Validation validate(String name, Collection<String> languages,
                                       String excludedId, Set<String> preservedUnknown) {
        String normalizedName = name == null ? "" : Normalizer.normalize(
                name.trim(), Normalizer.Form.NFC);
        if (normalizedName.isEmpty()) return Validation.error(ERROR_NAME_REQUIRED, "");
        if (normalizedName.codePointCount(0, normalizedName.length()) > MAX_NAME_CODE_POINTS) {
            return Validation.error(ERROR_NAME_TOO_LONG, normalizedName);
        }
        synchronized (ShortcutLanguageGroups.class) {
            for (Group group : state.groups) {
                if (excludedId != null && excludedId.equals(group.id)) continue;
                if (group.name.equalsIgnoreCase(normalizedName)) {
                    return Validation.error(ERROR_DUPLICATE_NAME, normalizedName);
                }
            }
        }

        LinkedHashSet<String> normalizedLanguages = new LinkedHashSet<String>();
        if (languages != null) {
            for (String raw : languages) {
                String code = normalizeLanguageCode(raw);
                if (code == null) continue;
                if (!isSupportedLanguage(code)
                        && (preservedUnknown == null || !preservedUnknown.contains(code))) {
                    return Validation.error(ERROR_UNKNOWN_LANGUAGE, raw == null ? "" : raw);
                }
                normalizedLanguages.add(code);
            }
        }
        if (normalizedLanguages.isEmpty()) {
            return Validation.error(ERROR_LANGUAGES_REQUIRED, "");
        }
        return Validation.success(normalizedName,
                new ArrayList<String>(normalizedLanguages));
    }

    private static int indexOfId(String id) {
        if (id == null) return -1;
        for (int i = 0; i < state.groups.size(); i++) {
            if (id.equals(state.groups.get(i).id)) return i;
        }
        return -1;
    }

    private static ParseResult parse(String raw, boolean blankIsDefault) {
        if (raw == null || raw.trim().isEmpty()) {
            return blankIsDefault
                    ? ParseResult.valid(State.empty(), Collections.<String>emptyList())
                    : ParseResult.invalid("Shortcut language-group config value is empty.");
        }
        try {
            JsonElement rootElement = JsonParser.parseString(raw);
            if (!rootElement.isJsonObject()) {
                return ParseResult.invalid("Shortcut language-group config root is not an object.");
            }
            JsonObject root = rootElement.getAsJsonObject();
            if (!root.has("schemaVersion")
                    || root.get("schemaVersion").getAsInt() != SCHEMA_VERSION) {
                return ParseResult.invalid("Unsupported shortcut language-group schema version.");
            }
            boolean enabled = root.has("enabled") && root.get("enabled").getAsBoolean();
            if (root.has("groups") && !root.get("groups").isJsonArray()) {
                return ParseResult.invalid("Shortcut language-group groups value is not an array.");
            }
            JsonArray rawGroups = root.has("groups")
                    ? root.getAsJsonArray("groups") : new JsonArray();
            List<Group> groups = new ArrayList<Group>();
            List<String> warnings = new ArrayList<String>();
            Set<String> ids = new HashSet<String>();
            Set<String> names = new HashSet<String>();
            for (int i = 0; i < rawGroups.size(); i++) {
                try {
                    JsonObject rawGroup = rawGroups.get(i).getAsJsonObject();
                    String id = requiredString(rawGroup, "id");
                    String name = requiredString(rawGroup, "name").trim();
                    boolean groupEnabled = !rawGroup.has("enabled")
                            || rawGroup.get("enabled").getAsBoolean();
                    boolean rawTranslation = rawGroup.has("rawTranslation")
                            && rawGroup.get("rawTranslation").getAsBoolean();
                    boolean rawTranslationAllLowercase =
                            rawGroup.has("rawTranslationAllLowercase")
                                    && rawGroup.get("rawTranslationAllLowercase").getAsBoolean();
                    if (id.isEmpty() || name.isEmpty()
                            || name.codePointCount(0, name.length()) > MAX_NAME_CODE_POINTS) {
                        throw new IllegalArgumentException("invalid id or name");
                    }
                    String normalizedName = name.toLowerCase(Locale.ROOT);
                    if (ids.contains(id) || names.contains(normalizedName)) {
                        throw new IllegalArgumentException("duplicate id or name");
                    }
                    if (!rawGroup.has("languages")
                            || !rawGroup.get("languages").isJsonArray()) {
                        throw new IllegalArgumentException("missing languages");
                    }
                    LinkedHashSet<String> languages = new LinkedHashSet<String>();
                    for (JsonElement language : rawGroup.getAsJsonArray("languages")) {
                        String languageCode = normalizeLanguageCode(language.getAsString());
                        if (languageCode != null) languages.add(languageCode);
                    }
                    if (languages.isEmpty()) throw new IllegalArgumentException("empty languages");
                    ids.add(id);
                    names.add(normalizedName);
                    groups.add(new Group(id, name, groupEnabled, rawTranslation,
                            rawTranslationAllLowercase, new ArrayList<String>(languages)));
                } catch (RuntimeException badGroup) {
                    warnings.add("Ignored malformed shortcut language group " + (i + 1) + ".");
                }
            }
            return ParseResult.valid(new State(enabled, groups), warnings);
        } catch (RuntimeException malformed) {
            return ParseResult.invalid("Malformed shortcut language-group config JSON.");
        }
    }

    private static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new IllegalArgumentException("missing " + key);
        }
        return Normalizer.normalize(object.get(key).getAsString(), Normalizer.Form.NFC);
    }

    private static String serialize(State value) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("enabled", value.enabled);
        JsonArray groups = new JsonArray();
        for (Group group : value.groups) {
            JsonObject encoded = new JsonObject();
            encoded.addProperty("id", group.id);
            encoded.addProperty("name", group.name);
            encoded.addProperty("enabled", group.enabled);
            encoded.addProperty("rawTranslation", group.rawTranslation);
            encoded.addProperty("rawTranslationAllLowercase",
                    group.rawTranslationAllLowercase);
            JsonArray languages = new JsonArray();
            for (String code : group.languages) languages.add(code);
            encoded.add("languages", languages);
            groups.add(encoded);
        }
        root.add("groups", groups);
        return root.toString();
    }

    public static final class State {
        public final boolean enabled;
        public final List<Group> groups;

        public State(boolean enabled, Collection<Group> groups) {
            this.enabled = enabled;
            List<Group> copy = new ArrayList<Group>();
            if (groups != null) {
                for (Group group : groups) {
                    if (group != null) copy.add(new Group(group.id, group.name,
                            group.enabled, group.rawTranslation,
                            group.rawTranslationAllLowercase, group.languages));
                }
            }
            this.groups = Collections.unmodifiableList(copy);
        }

        public static State empty() {
            return new State(false, Collections.<Group>emptyList());
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof State)) return false;
            State that = (State) other;
            return enabled == that.enabled && groups.equals(that.groups);
        }

        @Override public int hashCode() {
            return 31 * (enabled ? 1 : 0) + groups.hashCode();
        }
    }

    public static final class Group {
        public final String id;
        public final String name;
        public final boolean enabled;
        public final boolean rawTranslation;
        public final boolean rawTranslationAllLowercase;
        public final List<String> languages;

        public Group(String id, String name, boolean enabled, Collection<String> languages) {
            this(id, name, enabled, false, false, languages);
        }

        public Group(String id, String name, boolean enabled,
                     boolean rawTranslation, boolean rawTranslationAllLowercase,
                     Collection<String> languages) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.enabled = enabled;
            this.rawTranslation = rawTranslation;
            this.rawTranslationAllLowercase = rawTranslationAllLowercase;
            this.languages = Collections.unmodifiableList(new ArrayList<String>(languages));
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Group)) return false;
            Group that = (Group) other;
            return enabled == that.enabled
                    && rawTranslation == that.rawTranslation
                    && rawTranslationAllLowercase == that.rawTranslationAllLowercase
                    && id.equals(that.id)
                    && name.equals(that.name) && languages.equals(that.languages);
        }

        @Override public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + (enabled ? 1 : 0);
            result = 31 * result + (rawTranslation ? 1 : 0);
            result = 31 * result + (rawTranslationAllLowercase ? 1 : 0);
            return 31 * result + languages.hashCode();
        }
    }

    public static final class Result {
        public final boolean success;
        public final String error;
        public final String detail;
        public final Group group;

        private Result(boolean success, String error, String detail, Group group) {
            this.success = success;
            this.error = error;
            this.detail = detail;
            this.group = group;
        }

        static Result success(Group group) {
            return new Result(true, null, null, group);
        }

        static Result error(String error, String detail) {
            return new Result(false, error, detail, null);
        }
    }

    public static final class LoadReport {
        public final boolean usedBackup;
        public final List<String> warnings;

        LoadReport(boolean usedBackup, List<String> warnings) {
            this.usedBackup = usedBackup;
            this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
        }
    }

    private static final class Validation {
        final String error;
        final String detail;
        final String name;
        final List<String> languages;

        private Validation(String error, String detail, String name, List<String> languages) {
            this.error = error;
            this.detail = detail;
            this.name = name;
            this.languages = languages;
        }

        boolean ok() { return error == null; }

        static Validation success(String name, List<String> languages) {
            return new Validation(null, null, name, languages);
        }

        static Validation error(String error, String detail) {
            return new Validation(error, detail, null, Collections.<String>emptyList());
        }
    }

    private static final class ParseResult {
        final boolean validRoot;
        final State state;
        final List<String> warnings;

        private ParseResult(boolean validRoot, State state, List<String> warnings) {
            this.validRoot = validRoot;
            this.state = state;
            this.warnings = warnings;
        }

        static ParseResult valid(State state, List<String> warnings) {
            return new ParseResult(true, state, warnings);
        }

        static ParseResult invalid(String warning) {
            return new ParseResult(false, State.empty(),
                    Collections.singletonList(warning));
        }
    }
}
