package com.genesiz.hexacreatecore.hexcasting.everbook;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class EverbookData {

  private static final String NBT_ROOT = "HexaEverbook";

  public record PatternEntry(
    String id,
    String angles,
    String startDir,
    String label,
    float x,
    float y,
    int orderIndex
  ) {
    public PatternEntry withPos(float nx, float ny) {
      return new PatternEntry(id, angles, startDir, label, nx, ny, orderIndex);
    }

    public PatternEntry withOrder(int newOrder) {
      return new PatternEntry(id, angles, startDir, label, x, y, newOrder);
    }

    public PatternEntry duplicateWithOffset(float dx, float dy, int newOrder) {
      return new PatternEntry(UUID.randomUUID().toString(), angles, startDir, label, x + dx, y + dy, newOrder);
    }

    public CompoundTag toNbt() {
      CompoundTag t = new CompoundTag();
      t.putString("id", id);
      t.putString("angles", angles);
      t.putString("startDir", startDir);
      t.putString("label", label);
      t.putFloat("x", x);
      t.putFloat("y", y);
      t.putInt("order", orderIndex);
      return t;
    }

    public static PatternEntry fromNbt(CompoundTag t) {
      return new PatternEntry(
        t.contains("id") ? t.getString("id") : UUID.randomUUID().toString(),
        t.getString("angles"),
        t.contains("startDir") ? t.getString("startDir") : "EAST",
        t.getString("label"),
        t.getFloat("x"),
        t.getFloat("y"),
        t.getInt("order")
      );
    }
  }

  public record BookPage(
    String title,
    List<PatternEntry> entries
  ) {
    public List<PatternEntry> orderedEntries() {
      List<PatternEntry> sorted = new ArrayList<>(entries);
      sorted.sort(Comparator.comparingInt(PatternEntry::orderIndex));
      return sorted;
    }

    public CompoundTag toNbt() {
      CompoundTag t = new CompoundTag();
      t.putString("title", title);
      ListTag list = new ListTag();
      for (PatternEntry e : entries) list.add(e.toNbt());
      t.put("entries", list);
      return t;
    }

    public static BookPage fromNbt(CompoundTag t) {
      String title = t.contains("title") ? t.getString("title") : "Untitled Chapter";
      List<PatternEntry> entries = new ArrayList<>();
      if (t.contains("entries", Tag.TAG_LIST)) {
        ListTag list = t.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; list.size() > i; i++) {
          entries.add(PatternEntry.fromNbt(list.getCompound(i)));
        }
      }
      return new BookPage(title, entries);
    }
  }

  public record BookState(
    int activePage,
    List<BookPage> pages
  ) {
    public BookPage currentPage() {
      if (pages.isEmpty()) return new BookPage("Chapter I", new ArrayList<>());
      int idx = Math.max(0, Math.min(activePage, pages.size() - 1));
      return pages.get(idx);
    }

    public CompoundTag toNbt() {
      CompoundTag t = new CompoundTag();
      t.putInt("activePage", activePage);
      ListTag pageList = new ListTag();
      for (BookPage p : pages) pageList.add(p.toNbt());
      t.put("pages", pageList);
      return t;
    }

    public static BookState fromNbt(CompoundTag t) {
      int active = t.getInt("activePage");
      List<BookPage> pages = new ArrayList<>();
      if (t.contains("pages", Tag.TAG_LIST)) {
        ListTag list = t.getList("pages", Tag.TAG_COMPOUND);
        for (int i = 0; list.size() > i; i++) {
          pages.add(BookPage.fromNbt(list.getCompound(i)));
        }
      }
      if (pages.isEmpty()) {
        pages.add(new BookPage("Chapter I", new ArrayList<>()));
      }
      return new BookState(Math.max(0, Math.min(active, pages.size() - 1)), pages);
    }
  }

  private EverbookData() {}

  public static BookState read(ItemStack stack) {
    CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
    if (custom == null) {
      return new BookState(0, new ArrayList<>(List.of(new BookPage("Chapter I", new ArrayList<>()))));
    }
    CompoundTag root = custom.copyTag();
    if (!root.contains(NBT_ROOT, Tag.TAG_COMPOUND)) {
      return new BookState(0, new ArrayList<>(List.of(new BookPage("Chapter I", new ArrayList<>()))));
    }
    return BookState.fromNbt(root.getCompound(NBT_ROOT));
  }

  public static void write(ItemStack stack, BookState state) {
    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(NBT_ROOT, state.toNbt()));
  }
}
