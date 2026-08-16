package orbiter.modules.misc;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;


import java.util.ArrayDeque;
import java.util.List;

public final class DisplayTextSanitizer {

    private DisplayTextSanitizer() {}

    public static boolean shouldSimplify(Component component, int maxChars, int maxNodes, int maxDepth,
                                          int maxStyleScore, int maxObfuscatedChars, int maxComplexNodes) {
        TextCost cost = analyze(component, Math.max(1, maxDepth));
        return cost.tooDeep
            || cost.nodeCount > Math.max(1, maxNodes)
            || cost.totalChars > Math.max(1, maxChars)
            || cost.styleScore > Math.max(1, maxStyleScore)
            || cost.obfuscatedChars > Math.max(1, maxObfuscatedChars)
            || cost.complexNodeCount > Math.max(1, maxComplexNodes);
    }

    public static Component simplifiedText() {
        return Component.literal("[display text hidden]");
    }

    public static int clampLineWidth(int lineWidth, int maxSafeLineWidth) {
        return Math.min(lineWidth, Math.max(1, maxSafeLineWidth));
    }

    private static TextCost analyze(Component root, int maxDepth) {
        TextCost cost = new TextCost();
        ArrayDeque<VisitNode> stack = new ArrayDeque<>();
        stack.push(new VisitNode(root, 0));
        while (!stack.isEmpty()) {
            VisitNode node = stack.removeLast();
            if (node.depth > maxDepth) {
                cost.tooDeep = true;
                return cost;
            }
            Component component = node.component;
            cost.nodeCount++;
            int directChars = estimateDirectChars(component);
            cost.totalChars += directChars;
            cost.styleScore += estimateStyleScore(component.getStyle(), directChars);
            if (component.getStyle().isObfuscated()) {
                cost.obfuscatedChars += Math.max(directChars, 8);
            }
            if (!(component.getContents() instanceof ComponentContents)) {
                cost.complexNodeCount++;
            }
            List<Component> siblings = component.getSiblings();
            for (int index = siblings.size() - 1; index >= 0; index--) {
                stack.addLast(new VisitNode(siblings.get(index), node.depth + 1));
            }
        }
        return cost;
    }

    private static int estimateDirectChars(Component component) {
        String collapsed = component.getString();
        if (collapsed != null) return collapsed.length();
        return component.getString().length();
    }

    private static int estimateStyleScore(Style style, int directChars) {
        if (style.isEmpty()) return 0;
        int score = 1;
        if (style.getColor() != null) score++;
        if (style.isBold()) score++;
        if (style.isItalic()) score++;
        if (style.isUnderlined()) score++;
        if (style.isStrikethrough()) score++;
        if (style.isObfuscated()) score += 6 + Math.min(8, Math.max(1, directChars / 16));
        if (style.getClickEvent() != null) score += 2;
        if (style.getHoverEvent() != null) score += 2;
        if (style.getInsertion() != null) score++;
        if (!style.getFont().toString().equals("minecraft:default")) score++;
        return score;
    }

    private static final class TextCost {
        int totalChars, nodeCount, styleScore, obfuscatedChars, complexNodeCount;
        boolean tooDeep;
    }

    private record VisitNode(Component component, int depth) {}
}
