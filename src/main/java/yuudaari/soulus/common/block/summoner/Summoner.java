package yuudaari.soulus.common.block.summoner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;
import yuudaari.soulus.Soulus;
import yuudaari.soulus.common.CreativeTab;
import yuudaari.soulus.common.registration.BlockRegistry;
import yuudaari.soulus.common.registration.ItemRegistry;
import yuudaari.soulus.common.advancement.Advancements;
import yuudaari.soulus.common.block.EndersteelType;
import yuudaari.soulus.common.block.upgradeable_block.UpgradeableBlock;
import yuudaari.soulus.common.block.upgradeable_block.UpgradeableBlockTileEntity;
import yuudaari.soulus.common.config.ConfigInjected;
import yuudaari.soulus.common.config.ConfigInjected.Inject;
import yuudaari.soulus.common.config.block.ConfigSummoner;
import yuudaari.soulus.common.config.essence.ConfigEssence;
import yuudaari.soulus.common.config.essence.ConfigEssences;
import yuudaari.soulus.common.item.CrystalBlood;
import yuudaari.soulus.common.item.OrbMurky;
import yuudaari.soulus.common.item.Soulbook;
import yuudaari.soulus.common.registration.Registration;
import yuudaari.soulus.common.util.EssenceType;
import yuudaari.soulus.common.util.Material;
import yuudaari.soulus.common.util.Translation;

@ConfigInjected(Soulus.MODID)
public class Summoner extends UpgradeableBlock<SummonerTileEntity> {

	/////////////////////////////////////////
	// Upgrades
	//

	public static enum Upgrade implements IUpgrade {
		COUNT (0, "count", ItemRegistry.CRYSTAL_BLOOD.getItemStack()),
		DELAY (1, "delay",
			ItemRegistry.GEAR_OSCILLATING.getItemStack()),
		RANGE (2, "range", ItemRegistry.ORB_MURKY.getItemStack()),
		EFFICIENCY (3, "efficiency", ItemRegistry.GEAR_NIOBIUM.getItemStack()),
		CRYSTAL_DARK (4, "crystal_dark", ItemRegistry.CRYSTAL_DARK.getItemStack());

		private final int index;
		private final String name;
		private final ItemStack stack;
		// by default all upgrades are capped at 16
		private int maxQuantity = 16;

		private Upgrade (int index, String name, ItemStack item) {
			this.index = index;
			this.name = name;
			this.stack = item;
		}

		@Override
		public int getIndex () {
			return index;
		}

		@Override
		public String getName () {
			return name;
		}

		@Override
		public int getMaxQuantity () {
			// all upgrades by default are capped at 16
			return maxQuantity;
		}

		@Override
		public void setMaxQuantity (int quantity) {
			maxQuantity = quantity;
		}

		@Override
		public boolean isItemStack (ItemStack stack) {
			if (stack.getItem() != this.stack.getItem())
				return false;

			if (name == "count") {
				return CrystalBlood.isFilled(stack);
			} else if (name == "range") {
				return OrbMurky.isFilled(stack);
			}

			return true;
		}

		@Override
		public boolean isItemStackForTileEntity (ItemStack stack, UpgradeableBlockTileEntity te) {
			if (te.upgrades.get(Upgrade.CRYSTAL_DARK) > 0)
				return false;
			else if (name == "crystal_dark") {
				// a midnight jewel must be inserted into a summoner with no other upgrades
				for (Upgrade upgrade : Upgrade.values()) {
					if (te.upgrades.get(upgrade) > 0) return false;
				}
			}
			return IUpgrade.super.isItemStackForTileEntity(stack, te);
		}

		@Override
		public ItemStack getItemStackForTileEntity (UpgradeableBlockTileEntity te, int quantity) {
			SummonerTileEntity ste = (SummonerTileEntity) te;
			if (name == "crystal_dark" && ste.hasMalice()) return null;

			return IUpgrade.super.getItemStackForTileEntity(te, quantity);
		}

		@Override
		public ItemStack getItemStack (int quantity) {
			ItemStack stack = new ItemStack(this.stack.getItem(), quantity);
			if (name == "count") {
				CrystalBlood.setFilled(stack);
			} else if (name == "range") {
				OrbMurky.setFilled(stack);
			}

			return stack;
		}

		@Override
		public boolean isSecret () {
			return name == "crystal_dark";
		}
	}

	@Override
	public IUpgrade[] getUpgrades () {
		return Upgrade.values();
	}

	/////////////////////////////////////////
	// Config
	//

	@Inject public static ConfigSummoner CONFIG;
	@Inject public static ConfigEssences CONFIG_ESSENCES;

	/////////////////////////////////////////
	// Properties
	//

	public static final IProperty<EndersteelType> VARIANT = PropertyEnum.create("variant", EndersteelType.class);
	public static final PropertyBool HAS_SOULBOOK = PropertyBool.create("has_soulbook");

	public Summoner () {
		super("summoner", new Material(MapColor.STONE).setTransparent());
		setHasItem();
		setHardness(5F);
		setResistance(30F);
		setHarvestLevel("pickaxe", 1);
		setSoundType(SoundType.METAL);
		disableStats();
		setDefaultState(getDefaultState().withProperty(HAS_SOULBOOK, false)
			.withProperty(VARIANT, EndersteelType.NORMAL));
		setHasDescription();
	}

	@Override
	public UpgradeableBlock<SummonerTileEntity> getInstance () {
		return BlockRegistry.SUMMONER;
	}

	@Override
	public boolean canActivateWithStack (ItemStack stack, World world, BlockPos pos) {
		return super.canActivateWithStack(stack, world, pos) || stack.getItem() == ItemRegistry.ESSENCE_PERFECT;
	}

	@Override
	public boolean canActivateTileEntity (SummonerTileEntity te) {
		return te == null || !te.hasMalice();
	}

	@Override
	public boolean hasComparatorInputOverride (IBlockState state) {
		return true;
	}

	@Override
	public int getComparatorInputOverride (IBlockState state, World world, BlockPos pos) {
		SummonerTileEntity te = (SummonerTileEntity) world.getTileEntity(pos);
		return te == null ? 0 : te.getSignalStrength();
	}

	@Override
	public void addBlockToList (List<ItemStack> list, World world, BlockPos pos) {
		list.add(getStackFromEndersteelType(world.getBlockState(pos).getValue(VARIANT)));
	}

	@Override
	public void getSubBlocks (CreativeTabs tab, NonNullList<ItemStack> list) {
		for (EndersteelType variant : EndersteelType.values()) {
			list.add(getStackFromEndersteelType(variant));
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void registerItemModel () {
		NonNullList<ItemStack> stacks = NonNullList.create();
		getSubBlocks(CreativeTab.INSTANCE, stacks);
		for (ItemStack stack : stacks) {
			IBlockState state = getStateFromMeta(stack.getMetadata());
			ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(this), stack
				.getMetadata(), new ModelResourceLocation(this
					.getRegistryName(), HAS_SOULBOOK.getName() + "=false," + VARIANT
						.getName() + "=" + state.getValue(VARIANT).getName().toLowerCase()));
			ModelLoader.setCustomModelResourceLocation(Item
				.getItemFromBlock(this), stack.getMetadata() + 1, new ModelResourceLocation(this
					.getRegistryName(), HAS_SOULBOOK.getName() + "=true," + VARIANT
						.getName() + "=" + state.getValue(VARIANT).getName().toLowerCase()));
		}
	}

	/////////////////////////////////////////
	// Blockstate
	//

	@Override
	protected BlockStateContainer createBlockState () {
		return new BlockStateContainer(this, new IProperty<?>[] {
			VARIANT, HAS_SOULBOOK
		});
	}

	@Override
	public IBlockState getStateFromMeta (int meta) {
		return getDefaultState().withProperty(HAS_SOULBOOK, (meta & 1) == 0 ? false : true)
			.withProperty(VARIANT, EndersteelType.byMetadata(meta / 2));
	}

	@Override
	public int getMetaFromState (IBlockState state) {
		return state.getValue(VARIANT).getMeta() * 2 + (state.getValue(HAS_SOULBOOK) ? 1 : 0);
	}

	@Override
	public int damageDropped (IBlockState state) {
		return getMetaFromState(state.withProperty(HAS_SOULBOOK, false));
	}

	/////////////////////////////////////////
	// Item
	//

	@Override
	public ItemBlock createItemBlock () {
		return new Registration.ItemMultiTexture(this, i -> "summoner_" + EndersteelType.byMetadata(i.getItemDamage()).getName()) {

			@Override
			public int getMetadata (int damage) {
				return damage;
			}

			@Override
			public String getItemStackDisplayName (ItemStack stack) {
				String essenceType = EssenceType.getEssenceType(stack);
				ConfigEssence config = CONFIG_ESSENCES.get(essenceType);
				if (essenceType == null || config == null)
					return Translation.localize(this.getUnlocalizedName() + ".unfocused.name").trim();

				String alignment = config.name;
				if (alignment == null) {
					alignment = Translation.localizeEntity(essenceType);
				}

				return Translation.localize(this.getUnlocalizedName() + ".focused.name", alignment).trim();
			}

			@SideOnly(Side.CLIENT)
			@Override
			public void addInformation (ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
				tooltip.add(Translation.localize("tooltip." + Soulus.MODID + ":summoner.style." + EndersteelType
					.byMetadata(stack.getItemDamage() / 2)
					.getName()));
			}
		}
			.setHasSubtypes(true);
	}

	public ItemStack getItemStack (SummonerTileEntity te, int count, int metadata) {
		ItemStack itemStack = new ItemStack(getItemBlock(), count, metadata);

		itemStack.setTagCompound(te.writeToNBT(new NBTTagCompound()));

		return itemStack;
	}

	/////////////////////////////////////////
	// Tile Entity
	//

	@Override
	public boolean hasTileEntity (IBlockState blockState) {
		return blockState.getValue(HAS_SOULBOOK);
	}

	@Override
	public Class<? extends UpgradeableBlockTileEntity> getTileEntityClass () {
		return SummonerTileEntity.class;
	}

	/////////////////////////////////////////
	// Events
	//

	@Override
	public void onRegisterRecipes (IForgeRegistry<IRecipe> registry) {
		registry.registerAll(CONFIG.styleItems.entrySet()
			.stream()
			.map(item -> new ShapelessOreRecipe(null, getStackFromEndersteelType(item.getValue()), getIngredientFromStacksOfOtherEndersteelTypes(item.getValue()), ForgeRegistries.ITEMS.getValue(new ResourceLocation(item.getKey())))
				.setRegistryName(getRegistryName() + "_dye_" + item.getValue().getName().toLowerCase()))
			.toArray(ShapelessOreRecipe[]::new));

		registry.registerAll(CONFIG.styleItems.entrySet()
			.stream()
			.map(item -> new ShapedOreRecipe(null, getStackFromEndersteelType(item.getValue()), "BBB", "BeB", "BBB", 'B', BlockRegistry.BARS_ENDERSTEEL.getStackFromEndersteelType(item.getValue()), 'e', ItemRegistry.BONEMEAL_ENDER)
				.setRegistryName(getRegistryName() + "_" + item.getValue().getName().toLowerCase()))
			.toArray(ShapedOreRecipe[]::new));
	}

	private ItemStack getStackFromEndersteelType (final EndersteelType type) {
		return getItemStack(1, getMetaFromState(getDefaultState().withProperty(VARIANT, type)));
	}

	private Ingredient getIngredientFromStacksOfOtherEndersteelTypes (final EndersteelType type) {
		return Ingredient.fromStacks(Arrays.stream(EndersteelType.values())
			.filter(e -> e != type)
			.map(e -> getStackFromEndersteelType(e))
			.toArray(ItemStack[]::new));
	}

	@Override
	public boolean onActivateEmptyHand (World world, BlockPos pos, EntityPlayer player) {
		IBlockState state = world.getBlockState(pos);
		if (!state.getValue(HAS_SOULBOOK))
			return false;

		boolean returnedUpgrade = super.onActivateEmptyHand(world, pos, player);

		if (!returnedUpgrade) {
			SummonerTileEntity te = (SummonerTileEntity) world.getTileEntity(pos);

			returnItemsToPlayer(world, Collections.singletonList(getSoulbook(te)), player);

			world.setBlockState(pos, getDefaultState().withProperty(VARIANT, state.getValue(VARIANT)));

			return true;
		}

		return false;
	}

	@Override
	public boolean onActivateEmptyHandSneaking (World world, BlockPos pos, EntityPlayer player) {
		super.onActivateEmptyHandSneaking(world, pos, player);

		IBlockState state = world.getBlockState(pos);
		world.setBlockState(pos, getDefaultState().withProperty(VARIANT, state.getValue(VARIANT)));

		return true;
	}

	@Override
	public void onBlockDestroy (World world, BlockPos pos, int fortune, EntityPlayer player) {
		TileEntity te = world.getTileEntity(pos);

		if (te != null && te instanceof SummonerTileEntity) {
			SummonerTileEntity ste = (SummonerTileEntity) te;
			if (ste.hasMalice()) {
				Advancements.BREAK_SUMMONER_MALICE.trigger(player, null);
			}
		}

		super.onBlockDestroy(world, pos, fortune, player);
	}

	@Override
	public void addOtherDropStacksToList (List<ItemStack> list, World world, BlockPos pos, IBlockState state) {
		TileEntity te = world.getTileEntity(pos);

		if (te != null && te instanceof SummonerTileEntity) {
			SummonerTileEntity ste = (SummonerTileEntity) te;
			list.add(getSoulbook(ste));
		}
	}

	@Override
	public boolean onActivateInsert (World world, BlockPos pos, EntityPlayer player, ItemStack stack) {
		final Item item = stack.getItem();
		final IBlockState state = world.getBlockState(pos);

		// the summoner style can always be changed
		final EndersteelType itemStyle = CONFIG.styleItems.get(item.getRegistryName().toString().toLowerCase());
		if (itemStyle != null && state.getValue(VARIANT) != itemStyle) {
			world.setBlockState(pos, state.withProperty(VARIANT, itemStyle));
			stack.shrink(1);
			Advancements.STYLE_SUMMONER.trigger(player, itemStyle.getName());
			return true;
		}

		// try to insert a soulbook
		if (item == ItemRegistry.SOULBOOK) {
			final String essenceType = EssenceType.getEssenceType(stack);
			if (essenceType == null)
				return false;

			// if the contained essence is less than the essence required to insert the soulbook (required % of the soulbook quantity of the essence type)
			if (Soulbook.getContainedEssence(stack) < CONFIG.soulbookEssenceRequiredToInsert * CONFIG_ESSENCES.getSoulbookQuantity(essenceType))
				return false;

			// if the soulbook isn't filled and the soulbook is in "lasts forever" mode, it needs to be completely filled before it can be inserted
			if (!Soulbook.isFilled(stack) && (CONFIG.soulbookUses == null || CONFIG.soulbookUses <= 0))
				return false;

			if (!state.getValue(HAS_SOULBOOK)) {
				world.setBlockState(pos, getDefaultState().withProperty(VARIANT, state.getValue(VARIANT))
					.withProperty(HAS_SOULBOOK, true));
			}

			SummonerTileEntity te = (SummonerTileEntity) world.getTileEntity(pos);

			// there was already a tile entity here, with an essence type
			// that means there's a soulbook inside, so return it
			String oldEssenceType = te.getEssenceType();
			if (oldEssenceType != null) {
				returnItemsToPlayer(world, Collections.singletonList(getSoulbook(te)), player);
			}

			String newEssenceType = EssenceType.getEssenceType(stack);
			te.setEssenceType(newEssenceType);

			if (CONFIG.soulbookUses != null && CONFIG.soulbookUses > 0) {
				te.setSoulbookUses((float) (Soulbook.getContainedEssence(stack) / (double) CONFIG_ESSENCES
					.getSoulbookQuantity(newEssenceType) * CONFIG.soulbookUses));
			} else
				te.setSoulbookUses(null);

			te.reset();

			stack.shrink(1);

			return true;
		}

		// we can't insert anything else if it's an empty summoner
		if (!state.getValue(HAS_SOULBOOK))
			return false;

		if (item == ItemRegistry.ESSENCE_PERFECT) {
			final SummonerTileEntity te = (SummonerTileEntity) world.getTileEntity(pos);
			return te.insertPerfectEssence(stack, player.isSneaking());
		}

		// trying to insert the upgrades
		return super.onActivateInsert(world, pos, player, stack);
	}

	@Override
	protected ItemStack getSilkTouchDrop (IBlockState state) {
		return ItemStack.EMPTY;
	}

	public ItemStack getSoulbook (SummonerTileEntity te) {
		String essenceType = te.getEssenceType();

		if (te.hasMalice()) {
			int essenceQuantity = CONFIG.midnightJewelSoulbookEssenceQuantity.getInt(te.getWorld().rand);
			return Soulbook.getStack(essenceType, essenceQuantity);
		}

		if (CONFIG.soulbookUses != null && CONFIG.soulbookUses > 0) {
			int essenceQuantity = (int) Math
				.max(0, te.getSoulbookUses() / (double) CONFIG.soulbookUses * CONFIG_ESSENCES
					.getSoulbookQuantity(essenceType));
			return Soulbook.getStack(essenceType, essenceQuantity);
		}

		return Soulbook.getFilled(essenceType);
	}

	/////////////////////////////////////////
	// Waila
	//

	@Override
	protected void onWailaTooltipHeader (List<String> currentTooltip, IBlockState blockState, SummonerTileEntity te, EntityPlayer player) {

		if (te == null) return;

		// the stats of summoners with midnight jewels are secret
		if (te.upgrades.get(Upgrade.CRYSTAL_DARK) > 0) return;

		int summonPercentage = (int) Math.floor(te.getSpawnPercent() * 100);
		currentTooltip
			.add(Translation.localize("waila." + Soulus.MODID + ":summoner.summon_percentage", summonPercentage));

		if (CONFIG.soulbookUses != null && CONFIG.soulbookUses > 0) {
			int summonsRemaining = (int) Math.ceil(te.getSoulbookUses() / CONFIG.soulbookUses * 100);
			currentTooltip
				.add(Translation.localize("waila." + Soulus.MODID + ":summoner.summons_remaining", summonsRemaining));
		}

	}

	@Override
	protected boolean shouldWailaTooltipShowAll (IBlockState blockState, SummonerTileEntity te) {
		return te.upgrades.get(Upgrade.CRYSTAL_DARK) > 0;
	}

	@Override
	protected String getWailaTooltipUpgrade (IUpgrade upgrade, SummonerTileEntity te) {
		if (upgrade != Upgrade.CRYSTAL_DARK && te.upgrades.get(Upgrade.CRYSTAL_DARK) > 0)
			return null;

		return super.getWailaTooltipUpgrade(upgrade, te);
	}

	@Override
	protected List<String> onWailaTooltipMore (IBlockState blockState, SummonerTileEntity te, EntityPlayer player) {
		String variant = blockState.getValue(VARIANT).getName();
		return Collections.singletonList(Translation.localize("tooltip." + Soulus.MODID + ":summoner.style." + variant));
	}

	@Override
	public ItemStack getWailaStack (IDataAccessor accessor) {
		TileEntity te = accessor.getTileEntity();
		if (te == null || !(te instanceof SummonerTileEntity))
			return null;
		return getItemStack((SummonerTileEntity) te, 1, te.getBlockMetadata());
	}

}
