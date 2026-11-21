package com.inza.twoh.command.agent;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;

import com.inza.twoh.command.agent.AgentStrings.Wire;

/**
 * EN: Decode one model decision. Parsing grants neither tool access nor command permission.
 * 中文：每轮只解析一个模型决策，解析成功不授予工具或命令权限。
 */
public final class ModelReplyParser {
    private static final int MAX_LENGTH = 2048;

    public enum Kind {
        TOOL, COMMAND, REPLY
    }

    public static final class Decision {
        public final Kind kind;
        public final String value;
        public final JsonObject arguments;

        private Decision(Kind kind, String value, JsonObject arguments) {
            this.kind = kind;
            this.value = value;
            this.arguments = arguments;
        }
    }

    private ModelReplyParser() {
    }

    public static Decision parse(String content) {
        if (content == null || content.length() > MAX_LENGTH) {
            throw new IllegalArgumentException();
        }

        try (JsonReader reader = new JsonReader(new StringReader(content))) {
            JsonObject data = readObject(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException();
            }

            switch (string(data, Wire.TYPE)) {
                case Wire.TOOL_CALL:
                    requireSize(data, 3);
                    if (!data.has(Wire.ARGUMENTS) || !data.get(Wire.ARGUMENTS).isJsonObject()) {
                        throw new IllegalArgumentException();
                    }
                    return new Decision(Kind.TOOL, string(data, Wire.NAME), data.getAsJsonObject(Wire.ARGUMENTS));
                case Wire.COMMAND:
                    requireSize(data, 2);
                    return new Decision(Kind.COMMAND, string(data, Wire.COMMAND), null);
                case Wire.REPLY:
                    requireSize(data, 2);
                    return new Decision(Kind.REPLY, string(data, Wire.TEXT), null);
                default:
                    throw new IllegalArgumentException();
            }
        } catch (IOException malformedJson) {
            // EN: A syntax error in an in-memory reply is not an API connection failure.
            // 中文：内存中的回复格式错误，不是模型连接故障。
            throw new IllegalArgumentException(malformedJson);
        }
    }

    // EN: Reject duplicate keys, nested decisions and trailing text rather than guessing intent.
    // 中文：拒绝重复字段、嵌套决策和额外文本，不猜测模型想表达哪一个动作。
    private static JsonObject readObject(JsonReader reader, int depth) throws IOException {
        JsonObject object = new JsonObject();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (object.has(key)) {
                throw new IllegalArgumentException();
            }
            if (reader.peek() == JsonToken.STRING) {
                object.addProperty(key, reader.nextString());
            } else if (reader.peek() == JsonToken.BEGIN_OBJECT && depth == 0) {
                object.add(key, readObject(reader, depth + 1));
            } else {
                throw new IllegalArgumentException();
            }
        }
        reader.endObject();
        return object;
    }

    static void requireSize(JsonObject object, int expected) {
        if (object.entrySet().size() != expected) {
            throw new IllegalArgumentException();
        }
    }

    static String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException();
        }
        String value = object.get(key).getAsString();
        if (value.trim().isEmpty() || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException();
        }
        return value;
    }
}
