package yuudaari.soulus.common.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityList;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;
import yuudaari.soulus.Soulus;
import yuudaari.soulus.common.block.composer.ComposerCell.IHasComposerCellInfo;
import yuudaari.soulus.common.block.composer.IFillableWithEssence;
import yuudaari.soulus.common.block.summoner.Summoner;
import yuudaari.soulus.common.config.ConfigInjected;
import yuudaari.soulus.common.config.ConfigInjected.Inject;
import yuudaari.soulus.common.config.essence.ConfigColor;
import yuudaari.soulus.common.config.essence.ConfigEssence;
import yuudaari.soulus.common.config.essence.ConfigEssences;
import yuudaari.soulus.common.recipe.ingredient.IngredientPotentialEssence;
import yuudaari.soulus.common.registration.ItemRegistry;
import yuudaari.soulus.common.registration.Registration;
import yuudaari.soulus.common.util.Colour;
import yuudaari.soulus.common.util.EssenceType;
import yuudaari.soulus.common.util.Translation;

@ConfigInjected(Soulus.MODID)
public class Soulbook extends Registration.Item implements IHasComposerCellInfo, IFillableWithEssence {

	@Inject public static ConfigEssences CONFIG;

	public static ItemStack getFilled (String essenceType) {
		return getStack(essenceType, CONFIG.getSoulbookQuantity(essenceType));
	}

	public static ItemStack getStack (String essenceType) {
		return getStack(essenceType, 0);
	}

	public static ItemStack getStack (String essenceType, int essenceAmount) {
		ItemStack stack = new ItemStack(ItemRegistry.SOULBOOK, 1);
		EssenceType.setEssenceType(stack, essenceType);
		setContainedEssence(stack, essenceAmount);
		return stack;
	}

	public static boolean isFilled (ItemStack stack) {
		String essenceType = EssenceType.getEssenceType(stack);
		if (essenceType == null)
			return false;
		return getContainedEssence(stack) >= CONFIG.getSoulbookQuantity(essenceType);
	}

	public static class SoulbookRecipe extends ShapelessOreRecipe {

		public static NonNullList<Ingredient> getIngredients (int size) {

			List<Ingredient> ingredients = new ArrayList<>();

			ingredients.addAll(Collections.nCopies(size * size - 1, IngredientPotentialEssence.getInstanceNoAsh()));
			ingredients.add(Ingredient.fromItem(ItemRegistry.SOULBOOK));

			return NonNullList.from(Ingredient.EMPTY, ingredients.toArray(new Ingredient[0]));
		}

		public SoulbookRecipe (ResourceLocation name, int size) {
			super(new ResourceLocation(""), getIngredients(size), getFilled("unfocused"));
			setRegistryName(name + "" + size);
		}

		@ParametersAreNonnullByDefault
		@Override
		public boolean matches (InventoryCrafting inv, World worldIn) {
			return !getCraftingResult(inv).isEmpty();
		}

		@ParametersAreNonnullByDefault
		@Override
		public ItemStack getCraftingResult (InventoryCrafting inv) {
			int essenceCount = 0;
			ItemStack soulbook = null;
			String essenceType = null;
			int containedEssence = 0;

			final int inventorySize = inv.getSizeInventory();
			for (int i = 0; i < inventorySize; i++) {

				final ItemStack stack = inv.getStackInSlot(i);
				final Item stackItem = stack.getItem();

				if (stack == null || stack.isEmpty())
					continue;

				if (stackItem == ItemRegistry.SOULBOOK) {
					if (soulbook != null)
						return ItemStack.EMPTY;

					String itemTarget = EssenceType.getEssenceType(stack);
					if (itemTarget != null) {
						if (essenceType != null && !itemTarget.equals(essenceType))
							return ItemStack.EMPTY;

						essenceType = itemTarget;
					}

					containedEssence = getContainedEssence(stack);
					soulbook = stack;
					continue;

				} else if (stackItem == ItemRegistry.ESSENCE) {
					String itemTarget = EssenceType.getEssenceType(stack);
					if (itemTarget == null || (essenceType != null && !itemTarget.equals(essenceType)))
						return ItemStack.EMPTY;

					essenceType = itemTarget;
					essenceCount++;
					continue;
				}

				return ItemStack.EMPTY;
			}

			if (soulbook != null && essenceCount > 0 && containedEssence + essenceCount <= CONFIG.getSoulbookQuantity(essenceType)) {
				ItemStack newStack = soulbook.copy();
				EssenceType.setEssenceType(newStack, essenceType);
				setContainedEssence(newStack, containedEssence + essenceCount);
				return newStack;
			}

			return ItemStack.EMPTY;
		}
	}

	public Soulbook () {
		super("soulbook");
		setMaxStackSize(1);
		setHasDescription();

		if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
			registerColorHandler( (ItemStack stack, int tintIndex) -> {
				if (tintIndex == 2) return -1;

				int defaultColour = tintIndex == 0 ? 0x333F58 : 0x5E5997;

				String essenceType = EssenceType.getEssenceType(stack);
				if (essenceType == null)
					return defaultColour;

				EntityEntry entry = ForgeRegistries.ENTITIES.getValue(new ResourceLocation(essenceType));
				if (entry == null)
					return defaultColour;

				ConfigEssence essenceConfig = CONFIG.get(essenceType);
				if (essenceConfig == null)
					return defaultColour;

				ConfigColor colors = essenceConfig.colors;
				if (colors == null) {
					EntityList.EntityEggInfo eggInfo = entry.getEgg();
					if (eggInfo == null)
						return defaultColour;
					colors = new ConfigColor(eggInfo);
				}

				int color = tintIndex == 0 ? colors.primary : colors.secondary;
				if (Summoner.CONFIG.soulbookEssenceRequiredToInsert <= 0) return color;

				double percent = getContainedEssence(stack) / Summoner.CONFIG.soulbookEssenceRequiredToInsert / (double) essenceConfig.soulbookQuantity;
				return Colour.mix(defaultColour, color, percent).get();
			});
		}
	}

	@Override
	public void onRegisterRecipes (IForgeRegistry<IRecipe> registry) {
		registry.registerAll( //
			new SoulbookRecipe(getRegistryName(), 2), //
			new SoulbookRecipe(getRegistryName(), 3) //
		);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public String getItemStackDisplayName (ItemStack stack) {
		String essenceType = EssenceType.getEssenceType(stack);
		ConfigEssence config = CONFIG.get(essenceType);
		if (essenceType == null || config == null)
			return Translation.localize(this.getUnlocalizedName() + ".unfocused.name").trim();

		String alignment = config.name;
		if (alignment == null) {
			alignment = Translation.localizeEntity(essenceType);
		}

		return Translation.localize(this.getUnlocalizedName() + ".focused.name", alignment).trim();
	}

	@Override
	public boolean showDurabilityBar (ItemStack stack) {
		String essenceType = EssenceType.getEssenceType(stack);
		int containedEssence = getContainedEssence(stack);
		if (essenceType == null)
			return containedEssence == 0;
		return containedEssence < CONFIG.getSoulbookQuantity(essenceType);
	}

	@Override
	public double getDurabilityForDisplay (ItemStack stack) {
		String essenceType = EssenceType.getEssenceType(stack);
		if (essenceType == null)
			return 1;
		int containedEssence = getContainedEssence(stack);
		return (1 - containedEssence / (double) CONFIG.getSoulbookQuantity(essenceType));
	}

	@Override
	public int fill (ItemStack currentStack, ItemStack fillWith, int quantity) {
		int currentEssence = getContainedEssence(currentStack);
		String essenceType = EssenceType.getEssenceType(currentStack);
		String fillWithEssenceType = EssenceType.getEssenceType(fillWith);

		if (fillWithEssenceType == null || //
			(essenceType != null && !essenceType.equals(fillWithEssenceType)))
			return 0;

		if (essenceType == null) EssenceType.setEssenceType(currentStack, essenceType = fillWithEssenceType);

		int requiredEssence = CONFIG.getSoulbookQuantity(essenceType);
		int insertQuantity = Math.max(0, Math.min(quantity, requiredEssence - currentEssence));

		if (insertQuantity > 0) setContainedEssence(currentStack, currentEssence + insertQuantity);

		return insertQuantity;
	}

	@Override
	public float getFillPercentage (ItemStack stack) {
		String essenceType = EssenceType.getEssenceType(stack);
		if (essenceType == null) return 0;
		int requiredEssence = CONFIG.getSoulbookQuantity(essenceType);
		if (requiredEssence < 0) return 0;
		return getContainedEssence(stack) / (float) requiredEssence;
	}

	public static int getContainedEssence (ItemStack stack) {
		NBTTagCompound tag = stack.getTagCompound();
		if (tag != null && tag.hasKey("essence_quantity", 3)) {
			return tag.getInteger("essence_quantity");
		}
		return 0;
	}

	public static ItemStack setContainedEssence (ItemStack stack, int count) {
		NBTTagCompound tag = stack.getTagCompound();
		if (tag == null) {
			tag = new NBTTagCompound();
			stack.setTagCompound(tag);
		}
		tag.setInteger("essence_quantity", count);
		return stack;
	}

	@Override
	public void getSubItems (CreativeTabs tab, NonNullList<ItemStack> items) {
		if (!this.isInCreativeTab(tab)) return;

		items.add(getItemStack());
		for (ConfigEssence essence : CONFIG.essences) {
			if (essence.essence.equals("NONE")) continue;

			items.add(getStack(essence.essence, essence.soulbookQuantity));
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void addInformation (ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		addImportantInformation(tooltip, stack);
	}

	public void addImportantInformation (List<String> tooltip, ItemStack stack) {
		int containedEssence = Soulbook.getContainedEssence(stack);
		String mobTarget = EssenceType.getEssenceType(stack);
		if (mobTarget != null) {
			int requiredEssence = CONFIG.getSoulbookQuantity(mobTarget);
			if (containedEssence < requiredEssence) {
				tooltip.add(new Translation("tooltip." + Soulus.MODID + ":soulbook.contained_essence")
					.addArgs(containedEssence, requiredEssence)
					.get());
			}
		}
	}

	@Override
	public void addComposerCellInfo (List<String> tooltip, ItemStack stack, int stackSize) {
		if (stackSize == 1) addImportantInformation(tooltip, stack);
	}
}
