package io.github.genichimaruo.appliedsingulo.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.client.gui.components.Button;
import io.github.genichimaruo.appliedsingulo.me.AndroidInterfaceMenu;
import io.github.genichimaruo.appliedsingulo.me.InterfaceStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Status screen of the Android ME Interface, laid out like AE2's own ME Interface screen
 * ({@code assets/ae2/screens/applied_singulo/android_interface.json}): title, a few status lines, an "orders" slot row
 * and a "waiting for ME" slot row (hover for details), and the player inventory. AE2's priority button is top right.
 */
public final class AndroidInterfaceScreen extends AEBaseScreen<AndroidInterfaceMenu> {
    private static final int GREEN = 0xFF2E8B3A, YELLOW = 0xFFA07800, RED = 0xFFB02828;
    private static final int SLOTS_X = 8, JOBS_Y = 72, STOCK_Y = 104, SLOT = 18, COLUMNS = 9;

    private final Button cancelAll;
    private List<Component> hoverTooltip = List.of();

    public AndroidInterfaceScreen(AndroidInterfaceMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        widgets.addOpenPriorityButton();
        cancelAll = widgets.addButton("cancelAll", Component.translatable("gui.applied_singulo.cancel_all"), () -> {
            if (hasShiftDown()) menu.cancelAll();
        });
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        cancelAll.active = !menu.status.jobs().isEmpty();
        cancelAll.setTooltip(Tooltip.create(Component.translatable("gui.applied_singulo.cancel_all.hint")));
    }

    private int color(PaletteColor c) { return style.getColor(c).toARGB(); }

    @Override
    public void drawFG(GuiGraphics g, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(g, offsetX, offsetY, mouseX, mouseY);
        var s = menu.status;
        int text = color(PaletteColor.DEFAULT_TEXT_COLOR), muted = color(PaletteColor.MUTED_TEXT_COLOR);
        int mx = mouseX - offsetX, my = mouseY - offsetY;
        hoverTooltip = List.of();

        // --- status: two short lines, a red third line only when something needs attention
        var link = s.link();
        int linkColor = link == InterfaceStatus.Link.ONLINE ? GREEN : link == InterfaceStatus.Link.NO_CHANNEL ? YELLOW : RED;
        draw(g, Component.literal("\u25CF ").append(Component.translatable("gui.applied_singulo.link." + link.name().toLowerCase(Locale.ROOT))),
            8, 18, linkColor, 120);
        var prio = Component.translatable("gui.applied_singulo.priority", s.priority());
        draw(g, prio, 168 - font.width(prio), 18, muted, 168);
        if (s.console().isEmpty()) draw(g, Component.translatable("gui.applied_singulo.console.none"), 8, 29, RED, 168);
        else draw(g, Component.translatable("gui.applied_singulo.console", s.console()), 8, 29, text, 168);
        Component warning = !s.consoleProblem().isEmpty() ? reason(s.consoleProblem())
            : s.outputBlocked() ? Component.translatable("gui.applied_singulo.output_blocked")
            : !s.problem().isEmpty() ? reason(s.problem()) : null;
        if (warning != null) {   // wrap instead of cutting off (two lines fit above the orders row)
            var lines = font.split(warning, 160);
            for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), 8, 40 + i * 9, RED, false);
        }
        if (mx >= 6 && mx < 170 && my >= 16 && my < 50) hoverTooltip = details(s);

        // --- orders row
        draw(g, Component.translatable("gui.applied_singulo.orders.title"), 8, 60, text, 118);
        for (int i = 0; i < Math.min(COLUMNS, s.jobs().size()); i++) {
            var job = s.jobs().get(i);
            int x = SLOTS_X + i * SLOT;
            var stack = item(job.item());
            g.renderItem(stack, x, JOBS_Y);
            g.renderItemDecorations(font, stack, x, JOBS_Y, shortCount(job.count()));
            g.fill(x, JOBS_Y + 15, x + 16, JOBS_Y + 16, 0xFF000000 | stateColor(job));   // state bar, like AE2's status indicators
            if (inside(mx, my, x, JOBS_Y)) {
                g.fill(x, JOBS_Y, x + 16, JOBS_Y + 16, 0x80FFFFFF);
                hoverTooltip = jobTooltip(stack, job);
            }
        }

        // --- waiting for ME row
        // --- keep in stock row
        draw(g, Component.translatable("gui.applied_singulo.stock.title"), 8, 92, text, 168);
        if (mx >= 6 && mx < 170 && my >= 90 && my < 101)
            hoverTooltip = List.of(Component.translatable("gui.applied_singulo.stock.hint"));
        for (int i = 0; i < Math.min(COLUMNS, s.stock().size()); i++) {
            var k = s.stock().get(i);
            int x = SLOTS_X + i * SLOT;
            if (!k.item().isEmpty()) {
                var stack = item(k.item());
                g.renderItem(stack, x, STOCK_Y);
                g.renderItemDecorations(font, stack, x, STOCK_Y, shortCount(k.target()));
                int bar = switch (k.state()) {
                    case "ok" -> 0xFF3AB04A;
                    case "crafting" -> 0xFF3AA0D0;
                    case "missing", "no_pattern", "no_cpu" -> 0xFFC03030;
                    default -> 0x00000000;
                };
                if (bar != 0) g.fill(x, STOCK_Y + 15, x + 16, STOCK_Y + 16, bar);
            }
            if (inside(mx, my, x, STOCK_Y)) {
                g.fill(x, STOCK_Y, x + 16, STOCK_Y + 16, 0x80FFFFFF);
                hoverTooltip = stockTooltip(k);
            }
        }

        // --- waiting for ME row (real insert-only slots)
        draw(g, Component.translatable("gui.applied_singulo.waiting.title"), 8, 124, text, 168);
        if (mx >= 6 && mx < 170 && my >= 122 && my < 133)
            hoverTooltip = List.of(Component.translatable("gui.applied_singulo.waiting.hint"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        super.render(g, mouseX, mouseY, partialTicks);
        if (!hoverTooltip.isEmpty()) g.renderComponentTooltip(font, hoverTooltip, mouseX, mouseY);
    }

    private List<Component> stockTooltip(InterfaceStatus.Stock k) {
        List<Component> lines = new ArrayList<>();
        if (k.item().isEmpty()) {
            lines.add(Component.translatable("gui.applied_singulo.stock.empty"));
        } else {
            lines.add(item(k.item()).getHoverName());
            lines.add(Component.translatable("gui.applied_singulo.stock.target", k.target(), k.stored()).withStyle(ChatFormatting.GRAY));
            if (!k.state().isEmpty())
                lines.add(Component.translatable("gui.applied_singulo.stock.state." + k.state()).withStyle(
                    "ok".equals(k.state()) ? ChatFormatting.GREEN : "crafting".equals(k.state()) ? ChatFormatting.AQUA : ChatFormatting.RED));
        }
        lines.add(Component.translatable("gui.applied_singulo.stock.controls").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private int stockSlotAt(double mouseX, double mouseY) {
        int mx = (int) mouseX - leftPos, my = (int) mouseY - topPos;
        for (int i = 0; i < COLUMNS; i++) if (inside(mx, my, SLOTS_X + i * SLOT, STOCK_Y)) return i;
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slot = stockSlotAt(mouseX, mouseY);
        if (slot >= 0) {
            var carried = menu.getCarried();
            if (!carried.isEmpty() && (button == 0 || button == 1)) {
                menu.setStock(new AndroidInterfaceMenu.StockAction(slot,
                    BuiltInRegistries.ITEM.getKey(carried.getItem()).toString(), button == 0 ? carried.getCount() : 1));
            } else if (carried.isEmpty() && button == 1) {
                menu.setStock(new AndroidInterfaceMenu.StockAction(slot, "", 0));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int slot = stockSlotAt(mouseX, mouseY);
        if (slot >= 0 && slot < menu.status.stock().size()) {
            var k = menu.status.stock().get(slot);
            if (!k.item().isEmpty() && scrollY != 0) {
                long step = hasShiftDown() ? 64 : 1;
                long amount = Math.max(1, k.target() + (scrollY > 0 ? step : -step));
                menu.setStock(new AndroidInterfaceMenu.StockAction(slot, k.item(), amount));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    /** Tooltip of the status lines: the numbers that used to be printed. */
    private List<Component> details(InterfaceStatus s) {
        List<Component> lines = new ArrayList<>();
        if (s.console().isEmpty())
            lines.add(Component.translatable("gui.applied_singulo.console.hint").withStyle(ChatFormatting.YELLOW));
        lines.add(Component.translatable("gui.applied_singulo.recipes", s.patterns(), s.chainPatterns()));
        lines.add(Component.translatable("gui.applied_singulo.orders", s.running(), s.batching()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("gui.applied_singulo.buffers", s.held(), s.heldMax(), s.outputs(), s.outputsMax())
            .withStyle(ChatFormatting.GRAY));
        if (!s.recent().isEmpty())
            lines.add(Component.translatable("gui.applied_singulo.recent", recent(s)).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static boolean inside(int mx, int my, int x, int y) {
        return mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17;
    }

    private List<Component> jobTooltip(ItemStack stack, InterfaceStatus.Job job) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty().append(stack.getHoverName()).append(" ×" + job.count()));
        if (!job.submitted() && "QUEUED".equals(job.state())) {
            lines.add(Component.translatable("gui.applied_singulo.state.batching", job.pushes()).withStyle(ChatFormatting.GOLD));
        } else {
            lines.add(Component.translatable("gui.applied_singulo.state." + job.state().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("gui.applied_singulo.progress", job.claimed(), job.count()).withStyle(ChatFormatting.GRAY));
        }
        if (!job.reason().isEmpty())
            lines.add(Component.translatable("gui.applied_singulo.state.waiting", reason(job.reason())).withStyle(ChatFormatting.RED));
        return lines;
    }

    private static int stateColor(InterfaceStatus.Job job) {
        return switch (job.state()) {
            case "RUNNING" -> 0x3AA0D0;
            case "COMPLETED" -> 0x3AB04A;
            case "CANCELLED", "FAILED", "LOST" -> 0xC03030;
            default -> job.reason().isEmpty() ? 0xE09020 : 0xC8A000;
        };
    }

    private static String recent(InterfaceStatus s) {
        StringBuilder out = new StringBuilder();
        for (var p : s.recent()) {
            if (!out.isEmpty()) out.append(", ");
            out.append(item(p.item()).getHoverName().getString()).append(" (")
                .append(I18n.get(p.chain() ? "gui.applied_singulo.chain" : "gui.applied_singulo.step")).append(") ×").append(p.count());
        }
        return out.toString();
    }

    private static String shortCount(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 10_000) return String.format(Locale.ROOT, "%.1fk", n / 1000.0);
        return n / 1000 + "k";
    }

    /** Known reason codes are translated; Singulo's own (already localised) messages are shown as they are. */
    private static Component reason(String code) {
        String key = "gui.applied_singulo.reason." + code;
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(code);
    }

    private static ItemStack item(String id) {
        var rl = ResourceLocation.tryParse(id);
        return rl == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.get(rl).getDefaultInstance();
    }

    /** Draw text clipped at {@code right}. */
    private void draw(GuiGraphics g, Component text, int x, int y, int color, int right) {
        var clipped = font.substrByWidth(text, right - x);
        g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(clipped), x, y, color, false);
    }
}
