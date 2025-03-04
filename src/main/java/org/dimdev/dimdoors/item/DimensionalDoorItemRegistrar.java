package org.dimdev.dimdoors.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.joml.Quaternionf;

import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TallBlockItem;
import net.minecraft.registry.Registry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.fabricmc.loader.api.FabricLoader;

import org.dimdev.dimdoors.DimensionalDoors;
import org.dimdev.dimdoors.api.util.function.QuadFunction;
import org.dimdev.dimdoors.block.door.DimensionalDoorBlock;
import org.dimdev.dimdoors.block.door.DimensionalTrapdoorBlock;
import org.dimdev.dimdoors.block.door.data.DoorData;
import org.dimdev.dimdoors.block.entity.EntranceRiftBlockEntity;
import org.dimdev.dimdoors.client.UnderlaidChildItemRenderer;
import org.dimdev.dimdoors.rift.targets.PublicPocketTarget;

public class DimensionalDoorItemRegistrar {
	public static final String PREFIX = "item_ag_dim_";

	private final Registry<Item> registry;

	private final Map<Block, Block> blocksAlreadyNotifiedAbout = new HashMap<>();
	private final Map<Block, Triple<Identifier, Item, Function<Block, BlockItem>>> toBeMapped = new HashMap<>();

	private final Map<Item, Function<ItemPlacementContext, ActionResult>> placementFunctions = new HashMap<>();

	public DimensionalDoorItemRegistrar(Registry<Item> registry) {
		this.registry = registry;

		init();
		RegistryEntryAddedCallback.event(registry).register((rawId, id, object) -> handleEntry(id, object));
	}

	public boolean isRegistered(Item item) {
		return placementFunctions.containsKey(item);
	}

	public ActionResult place(Item item, ItemPlacementContext context) {
		return placementFunctions.get(item).apply(context);
	}

	private void init() {
		new ArrayList<>(registry.getEntrySet())
				.forEach(entry -> handleEntry(entry.getKey().getValue(), entry.getValue()));
	}

	public void handleEntry(Identifier identifier, Item original) {
		if (DimensionalDoors.getConfig().getDoorsConfig().isAllowed(identifier)) {
			if (original instanceof TallBlockItem) {
				Block block = ((TallBlockItem) original).getBlock();
				handleEntry(identifier, original, block, AutoGenDimensionalDoorItem::new);
			} else if (original instanceof BlockItem) {
				Block originalBlock = ((BlockItem) original).getBlock();
				if (originalBlock instanceof DoorBlock) {
					handleEntry(identifier, original, originalBlock, AutoGenDimensionalDoorItem::new);
				} else {
					handleEntry(identifier, original, originalBlock, AutoGenDimensionalTrapdoorItem::new);
				}
			}
		}
	}

	private void handleEntry(Identifier identifier, Item original, Block originalBlock, QuadFunction<Block, Item.Settings, Consumer<? super EntranceRiftBlockEntity>, Item, ? extends BlockItem> constructor) {

		if (!(originalBlock instanceof DimensionalDoorBlock)
				&& !(originalBlock instanceof DimensionalTrapdoorBlock)
				&& (originalBlock instanceof DoorBlock || originalBlock instanceof TrapdoorBlock)) {
			Item.Settings settings = ItemExtensions.getSettings(original)/*.group(DoorData.PARENT_ITEMS.contains(original) || DoorData.PARENT_BLOCKS.contains(originalBlock) ? null : ModItems.DIMENSIONAL_DOORS)*/; //TODO: Redo with the new way Itemgroups work.

			Function<Block, BlockItem> dimItemConstructor = (dimBlock) -> constructor.apply(dimBlock, settings, rift -> rift.setDestination(new PublicPocketTarget()), original);

			if (!blocksAlreadyNotifiedAbout.containsKey(originalBlock)) {
				toBeMapped.put(originalBlock, new ImmutableTriple<>(identifier, original, dimItemConstructor));
				return;
			}

			register(identifier, original, blocksAlreadyNotifiedAbout.get(originalBlock), dimItemConstructor);
		}
	}

	public void notifyBlockMapped(Block original, Block dimBlock) {
		if (!toBeMapped.containsKey(original)) {
			blocksAlreadyNotifiedAbout.put(original, dimBlock);
			return;
		}
		Triple<Identifier, Item, Function<Block, BlockItem>> triple = toBeMapped.get(original);
		register(triple.getLeft(), triple.getMiddle(), dimBlock, triple.getRight());
	}

	private void register(Identifier identifier, Item original, Block block, Function<Block, BlockItem> dimItem) {
		if (!DoorData.PARENT_ITEMS.contains(original)) {
			Identifier gennedId = DimensionalDoors.id(PREFIX + identifier.getNamespace() + "_" + identifier.getPath());
			BlockItem item = Registry.register(registry, gennedId, dimItem.apply(block));
			placementFunctions.put(original, item::place);
			if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
				registerItemRenderer(item);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	private void registerItemRenderer(BlockItem dimItem) {
		BuiltinItemRendererRegistry.INSTANCE.register(dimItem, Renderer.RENDERER);
	}

	// extract renderer to inner interface so it can be removed in server environment via annotation
	@Environment(EnvType.CLIENT)
	private interface Renderer {
		UnderlaidChildItemRenderer RENDERER = new UnderlaidChildItemRenderer(Items.ENDER_PEARL);
	}

	private static class AutoGenDimensionalDoorItem extends DimensionalDoorItem implements ChildItem {
		private final Item originalItem;

		public AutoGenDimensionalDoorItem(Block block, Settings settings, Consumer<? super EntranceRiftBlockEntity> setupFunction, Item originalItem) {
			super(block, settings, setupFunction);
			this.originalItem = originalItem;
		}

		@Override
		public Text getName(ItemStack stack) {
			return MutableText.of(new TranslatableTextContent("dimdoors.autogen_item_prefix", I18n.translate(originalItem.getTranslationKey())));
		}

		@Override
		public Text getName() {
			return MutableText.of(new TranslatableTextContent("dimdoors.autogen_item_prefix", I18n.translate(originalItem.getTranslationKey())));
		}

		@Override
		public Item getOriginalItem() {
			return originalItem;
		}

		@Override
		public void transform(MatrixStack matrices) {
			matrices.scale(0.68f, 0.68f, 1);
			matrices.translate(0.05, 0.02, 0);
		}
	}

	private static class AutoGenDimensionalTrapdoorItem extends DimensionalTrapdoorItem implements ChildItem {
		private final Item originalItem;

		public AutoGenDimensionalTrapdoorItem(Block block, Settings settings, Consumer<? super EntranceRiftBlockEntity> setupFunction, Item originalItem) {
			super(block, settings, setupFunction);
			this.originalItem = originalItem;
		}

		@Override
		public Text getName(ItemStack stack) {
			return MutableText.of(new TranslatableTextContent("dimdoors.autogen_item_prefix", I18n.translate(originalItem.getTranslationKey())));
		}

		@Override
		public Text getName() {
			return MutableText.of(new TranslatableTextContent("dimdoors.autogen_item_prefix", I18n.translate(originalItem.getTranslationKey())));
		}

		@Override
		public Item getOriginalItem() {
			return originalItem;
		}

		@Override
		public void transform(MatrixStack matrices) {
			matrices.scale(0.55f, 0.55f, 0.6f);
			matrices.translate(0.05, -0.05, 0.41);
			matrices.multiply(new Quaternionf().rotateXYZ(90, 0, 0));
		}
	}

	public interface ChildItem {
		Item getOriginalItem();

		default void transform(MatrixStack matrices) {
		}
	}
}
