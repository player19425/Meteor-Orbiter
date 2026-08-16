package orbiter.util;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public final class LegacyTextFormatter {
    private LegacyTextFormatter() {}

    public static MutableComponent parse(String input) {
        String value = input == null ? "" : input;
        MutableComponent result = Component.empty();
        StringBuilder plain = new StringBuilder();
        ChatFormatting pending = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '&' && i + 1 < value.length()) {
                char code = Character.toLowerCase(value.charAt(i + 1));
                if (code == '&') { plain.append('&'); i++; continue; }
                ChatFormatting formatting = ChatFormatting.getByCode(code);
                if (formatting != null) {
                    if (!plain.isEmpty()) {
                        result.append(Component.literal(plain.toString()).withStyle(pending == null ? ChatFormatting.RESET : pending));
                        plain.setLength(0);
                    }
                    pending = formatting;
                    i++;
                    continue;
                }
            }
            plain.append(c);
        }
        if (!plain.isEmpty()) result.append(Component.literal(plain.toString()).withStyle(pending == null ? ChatFormatting.RESET : pending));
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
                if (ChatFormatting.getByCode(code) != null) { result.append('\u00a7').append(code); i++; continue; }
            }
            result.append(c);
        }
        return result.toString();
    }
}
