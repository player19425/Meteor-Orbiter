package orbiter.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class UserListLoader {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public record Result(List<String> users, List<String> errors, long bytesRead) {
        public Result {
            users = List.copyOf(users);
            errors = List.copyOf(errors);
        }

        public boolean valid() {
            return errors.isEmpty();
        }
    }

    private UserListLoader() {}

    public static Result load(Path controlledRoot, String relativePath, int maxEntries, long maxBytes) {
        List<String> errors = new ArrayList<>();
        if (controlledRoot == null || relativePath == null || relativePath.isBlank()) {
            return new Result(List.of(), List.of(), 0L);
        }

        Path root = controlledRoot.toAbsolutePath().normalize();
        Path file = root.resolve(relativePath.trim()).normalize();
        if (!file.startsWith(root)) {
            return new Result(List.of(), List.of("Import path escapes the Orbiter data directory."), 0L);
        }

        try {
            if (!Files.exists(file)) return new Result(List.of(), List.of("Import file does not exist: " + file), 0L);
            if (!Files.isRegularFile(file)) return new Result(List.of(), List.of("Import path is not a regular file."), 0L);
            long declaredSize = Files.size(file);
            if (declaredSize > maxBytes) {
                return new Result(List.of(), List.of("Import file exceeds the " + maxBytes + " byte limit."), declaredSize);
            }

            byte[] bytes;
            try (var input = Files.newInputStream(file)) {
                bytes = input.readNBytes(Math.toIntExact(maxBytes + 1L));
            }
            if (bytes.length > maxBytes) {
                return new Result(List.of(), List.of("Import file grew beyond the byte limit while being read."), bytes.length);
            }
            String text = decodeStrictUtf8(bytes);
            LinkedHashMap<String, String> users = new LinkedHashMap<>();
            String[] lines = text.split("\\R", -1);
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index].trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (!USERNAME.matcher(line).matches()) {
                    errors.add("Invalid username on line " + (index + 1) + ": " + line);
                    if (errors.size() >= 32) break;
                    continue;
                }
                users.putIfAbsent(line.toLowerCase(Locale.ROOT), line);
                if (users.size() > maxEntries) {
                    errors.add("Import contains more than " + maxEntries + " unique usernames.");
                    break;
                }
            }
            return new Result(new ArrayList<>(users.values()), errors, bytes.length);
        } catch (CharacterCodingException exception) {
            return new Result(List.of(), List.of("Import file is not valid UTF-8."), 0L);
        } catch (IOException | SecurityException exception) {
            return new Result(List.of(), List.of("Unable to read import file: " + exception.getMessage()), 0L);
        }
    }

    public static boolean isValidUsername(String value) {
        return value != null && USERNAME.matcher(value).matches();
    }

    private static String decodeStrictUtf8(byte[] bytes) throws CharacterCodingException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer decoded = decoder.decode(ByteBuffer.wrap(bytes));
        return decoded.toString();
    }
}
