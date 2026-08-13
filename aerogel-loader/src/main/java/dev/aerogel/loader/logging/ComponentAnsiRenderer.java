package dev.aerogel.loader.logging;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;

public final class ComponentAnsiRenderer {
    private static final String ESCAPE = "\u001B[";
    private static final String RESET = ESCAPE + "0m";
    private static final String DISABLE_ANSI_PROPERTY = "aerogel.console.disableAnsi";

    private ComponentAnsiRenderer() {
    }

    public static String render(Component component) {
        if (Boolean.parseBoolean(System.getProperty(DISABLE_ANSI_PROPERTY, "false"))) {
            return component.getString();
        }

        StringBuilder output = new StringBuilder();
        String[] active = {""};
        component.visit((style, text) -> {
            String codes = codes(style);
            if (!codes.equals(active[0])) {
                if (!active[0].isEmpty()) output.append(RESET);
                if (!codes.isEmpty()) output.append(ESCAPE).append(codes).append('m');
                active[0] = codes;
            }
            // Components can contain plugin-controlled text. Do not allow raw terminal escapes.
            output.append(text.replace("\u001B", ""));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        if (!active[0].isEmpty()) output.append(RESET);
        return output.toString();
    }

    private static String codes(Style style) {
        List<String> codes = new ArrayList<>(6);
        TextColor color = style.getColor();
        if (color != null) {
            int rgb = color.getValue();
            codes.add("38;2;" + ((rgb >> 16) & 0xFF) + ';' + ((rgb >> 8) & 0xFF) + ';' + (rgb & 0xFF));
        }
        if (style.isBold()) codes.add("1");
        if (style.isItalic()) codes.add("3");
        if (style.isUnderlined()) codes.add("4");
        if (style.isStrikethrough()) codes.add("9");
        return String.join(";", codes);
    }
}
