package orbiter.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class LegacyTextFormatter {
    private LegacyTextFormatter() {}

    public static MutableText parse(String input) {
        String value = input == null ? "" : input;
        MutableText result = Text.empty();
        StringBuilder plain = new StringBuilder();
        Formatting pending = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '&' && i + 1 < value.length()) {
                char code = Character.toLowerCase(value.charAt(i + 1));
                if (code == '&') { plain.append('&'); i++; continue; }
                Formatting formatting = Formatting.byCode(code);
                if (formatting != null) {
                    if (!plain.isEmpty()) {
                        result.append(Text.literal(plain.toString()).formatted(pending == null ? Formatting.RESET : pending));
                        plain.setLength(0);
                    }
                    pending = formatting;
                    i++;
                    continue;
                }
            }
            plain.append(c);
        }
        if (!plain.isEmpty()) result.append(Text.literal(plain.toString()).formatted(pending == null ? Formatting.RESET : pending));
        return result;
    }

    public static String translate(String input) {
        if (input == null) return "";
        StringBuilder result = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                if (code == '&') { result.append('&'); i++; continue; }
                if (Formatting.byCode(code) != null) { result.append('\u00a7').append(code); i++; continue; }
            }
            result.append(c);
        }
        return result.toString();
    }
}
