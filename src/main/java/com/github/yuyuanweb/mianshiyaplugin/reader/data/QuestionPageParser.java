package com.github.yuyuanweb.mianshiyaplugin.reader.data;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads the website's server-rendered data without executing website JavaScript. */
public final class QuestionPageParser {
    private static final int MAX_HTML_CHARS = 8 * 1024 * 1024;
    private static final int MAX_FLIGHT_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_NODES = 100_000;
    private static final String PUSH = "self.__next_f.push(";

    public JsonObject parse(String html, long requestedId) {
        if (html == null || html.length() > MAX_HTML_CHARS || requestedId <= 0) {
            throw invalid("Invalid question page input");
        }
        Map<String, Record> records = readRecords(extractFlight(html));
        List<JsonElement> candidates = new ArrayList<>();
        for (Record record : records.values()) {
            if (!record.text) findDetails(record.value, candidates, 0);
        }
        if (candidates.isEmpty()) throw invalid("Question page has no initialQuestionDetail");
        for (JsonElement candidate : candidates) {
            JsonElement resolved = dereference(candidate, records);
            if (!resolved.isJsonObject()) throw invalid("Question detail is not an object");
            JsonObject question = resolved.getAsJsonObject();
            JsonElement id = question.get("id");
            try {
                if (id != null && id.isJsonPrimitive() && Long.parseLong(id.getAsString()) == requestedId) {
                    return questionFields(question, records);
                }
            } catch (NumberFormatException ignored) {
                // Decimal or out-of-range IDs must never be rounded into a matching question.
            }
        }
        throw invalid("Question page ID does not match requested question");
    }

    private static JsonObject questionFields(JsonObject question, Map<String, Record> records) {
        // Author/user/bank metadata may contain React-only references. Resolve only reader inputs.
        JsonObject result = project(question, records, "id", "title", "content", "contentType",
                "needVip", "hasVipAuth", "hasBestAnswer");
        if (question.has("bestQuestionAnswer")) {
            JsonElement answer = dereference(question.get("bestQuestionAnswer"), records);
            if (answer.isJsonNull()) result.add("bestQuestionAnswer", JsonNull.INSTANCE);
            else if (answer.isJsonObject()) result.add("bestQuestionAnswer",
                    project(answer.getAsJsonObject(), records, "content", "contentType"));
            else throw invalid("Question answer is not an object");
        }
        return result;
    }

    private static JsonObject project(JsonObject object, Map<String, Record> records, String... fields) {
        JsonObject result = new JsonObject();
        int[] budget = {MAX_NODES};
        for (String field : fields) {
            if (object.has(field)) result.add(field, resolve(object.get(field), records, new HashSet<>(), 0, budget));
        }
        return result;
    }

    private static JsonElement dereference(JsonElement value, Map<String, Record> records) {
        Set<String> seen = new HashSet<>();
        while (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String string = value.getAsString();
            if ("$undefined".equals(string)) return JsonNull.INSTANCE;
            if (!string.matches("\\$[0-9a-fA-F]+")) return value;
            String id = string.substring(1).toLowerCase(Locale.ROOT);
            if (!seen.add(id) || seen.size() > MAX_DEPTH) throw invalid("Cyclic or deep question object reference");
            Record record = records.get(id);
            if (record == null) throw invalid("Missing question object reference");
            value = record.value;
            if (record.text) return value;
        }
        return value;
    }
    private static byte[] extractFlight(String html) {
        StringBuilder flight = new StringBuilder();
        for (Element script : Jsoup.parse(html).select("script:not([src])")) {
            String source = script.data();
            int offset = 0;
            while ((offset = source.indexOf(PUSH, offset)) >= 0) {
                int start = offset + PUSH.length();
                while (start < source.length() && Character.isWhitespace(source.charAt(start))) start++;
                int end = arrayEnd(source, start);
                JsonElement payload = json(source.substring(start, end));
                if (!payload.isJsonArray()) throw invalid("Invalid Flight script payload");
                JsonArray array = payload.getAsJsonArray();
                if (array.size() >= 2 && array.get(0).isJsonPrimitive()
                        && "1".equals(array.get(0).getAsString())) {
                    if (!array.get(1).isJsonPrimitive() || !array.get(1).getAsJsonPrimitive().isString()) {
                        throw invalid("Invalid Flight text chunk");
                    }
                    flight.append(array.get(1).getAsString());
                    if (flight.length() > MAX_FLIGHT_BYTES) throw invalid("Flight data exceeds limit");
                }
                offset = end;
            }
        }
        byte[] bytes = flight.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_FLIGHT_BYTES) throw invalid("Missing or oversized Flight data");
        return bytes;
    }

    private static int arrayEnd(String source, int start) {
        if (start >= source.length() || source.charAt(start) != '[') throw invalid("Expected Flight JSON array");
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '[') depth++;
            else if (c == ']' && --depth == 0) return i + 1;
        }
        throw invalid("Truncated Flight JSON array");
    }

    private static Map<String, Record> readRecords(byte[] bytes) {
        Map<String, Record> records = new LinkedHashMap<>();
        int offset = 0;
        while (offset < bytes.length) {
            if (bytes[offset] == '\n' || bytes[offset] == '\r') { offset++; continue; }
            int start = offset;
            while (offset < bytes.length && hex(bytes[offset])) offset++;
            if (offset == start || offset >= bytes.length || bytes[offset++] != ':') throw invalid("Invalid Flight record ID");
            String id = new String(bytes, start, offset - start - 1, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
            if (offset >= bytes.length) throw invalid("Truncated Flight record");
            Record record = null;
            if (bytes[offset] == 'T') {
                start = ++offset;
                while (offset < bytes.length && hex(bytes[offset])) offset++;
                if (start == offset || offset >= bytes.length || bytes[offset] != ',') throw invalid("Invalid Flight text length");
                int length;
                try { length = Integer.parseInt(new String(bytes, start, offset - start, StandardCharsets.US_ASCII), 16); }
                catch (NumberFormatException e) { throw invalid("Invalid Flight text length"); }
                offset++;
                if (length < 0 || length > bytes.length - offset) throw invalid("Truncated Flight text");
                record = new Record(new JsonPrimitive(utf8(bytes, offset, length)), true);
                offset += length;
            } else {
                start = offset;
                while (offset < bytes.length && bytes[offset] != '\n') offset++;
                String value = utf8(bytes, start, offset - start).trim();
                if (value.isEmpty()) throw invalid("Empty Flight record");
                // Module, preload, debug and other React records are not question data.
                char first = value.charAt(0);
                if (first == '{' || first == '[' || first == '"' || first == '-' || Character.isDigit(first)
                        || first == 't' || first == 'f' || first == 'n') {
                    record = new Record(json(value), false);
                }
            }
            if (record != null && records.put(id, record) != null) throw invalid("Duplicate Flight record ID");
            if (records.size() > MAX_NODES) throw invalid("Too many Flight records");
        }
        return records;
    }

    private static String utf8(byte[] bytes, int offset, int length) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, offset, length)).toString();
        } catch (CharacterCodingException e) { throw invalid("Invalid UTF-8 Flight text"); }
    }

    private static boolean hex(byte c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static void findDetails(JsonElement element, List<JsonElement> candidates, int depth) {
        if (depth > MAX_DEPTH) throw invalid("Flight structure is too deep");
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if ("initialQuestionDetail".equals(entry.getKey())) candidates.add(entry.getValue());
                else findDetails(entry.getValue(), candidates, depth + 1);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) findDetails(item, candidates, depth + 1);
        }
    }

    private static JsonElement resolve(JsonElement value, Map<String, Record> records, Set<String> path, int depth, int[] budget) {
        if (depth > MAX_DEPTH || --budget[0] < 0) throw invalid("Question data exceeds traversal limit");
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String string = value.getAsString();
            if (string.startsWith("$$")) return new JsonPrimitive(string.substring(1));
            if ("$undefined".equals(string)) return JsonNull.INSTANCE;
            if (string.matches("\\$[0-9a-fA-F]+")) {
                String id = string.substring(1).toLowerCase(Locale.ROOT);
                if (!path.add(id)) throw invalid("Cyclic question data reference");
                Record record = records.get(id);
                if (record == null) throw invalid("Missing question data reference");
                JsonElement resolved = record.text ? record.value : resolve(record.value, records, path, depth + 1, budget);
                path.remove(id);
                return resolved;
            }
            return value;
        }
        if (value.isJsonObject()) {
            JsonObject resolved = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                resolved.add(entry.getKey(), resolve(entry.getValue(), records, path, depth + 1, budget));
            }
            return resolved;
        }
        if (value.isJsonArray()) {
            JsonArray resolved = new JsonArray();
            for (JsonElement item : value.getAsJsonArray()) resolved.add(resolve(item, records, path, depth + 1, budget));
            return resolved;
        }
        return value;
    }

    private static JsonElement json(String source) {
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setLenient(false);
            JsonElement value = readJson(reader, 0, new int[]{MAX_NODES});
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("Trailing JSON data");
            return value;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw new JsonParseException("Invalid Flight JSON", e);
        }
    }

    private static JsonElement readJson(JsonReader reader, int depth, int[] budget) throws IOException {
        if (depth > MAX_DEPTH || --budget[0] < 0) throw invalid("Flight JSON exceeds traversal limit");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) object.add(reader.nextName(), readJson(reader, depth + 1, budget));
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(readJson(reader, depth + 1, budget));
                reader.endArray();
                return array;
            case STRING: return new JsonPrimitive(reader.nextString());
            case NUMBER: return new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw invalid("Unexpected JSON token");
        }
    }

    private static JsonParseException invalid(String message) { return new JsonParseException(message); }
    private static final class Record {
        final JsonElement value;
        final boolean text;
        Record(JsonElement value, boolean text) { this.value = value; this.text = text; }
    }
}
