package yuudaari.soulus.common.block.soul_totem;

import java.util.Random;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import yuudaari.soulus.Soulus;
import yuudaari.soulus.client.util.ParticleType;
import yuudaari.soulus.common.ModBlocks;
import yuudaari.soulus.common.block.soul_totem.SoulTotem.Upgrade;
import yuudaari.soulus.common.block.upgradeable_block.UpgradeableBlockTileEntity;
import yuudaari.soulus.common.config.ConfigInjected;
import yuudaari.soulus.common.config.ConfigInjected.Inject;
import yuudaari.soulus.common.config.block.ConfigSoulTotem;

@ConfigInjected(Soulus.MODID)
public class SoulTotemTileEntity extends UpgradeableBlockTileEntity {

	/////////////////////////////////////////
	// Config
	//

	@Inject public static ConfigSoulTotem CONFIG;

	/////////////////////////////////////////
	// Helper
	//

	@Override
	public SoulTotem getBlock () {
		return ModBlocks.SOUL_TOTEM;
	}

	@Override
	public boolean shouldRefresh (World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
		return oldState.getBlock() != newState.getBlock();
	}

	public boolean isActive () {
		return isConnected && (upgrades.get(Upgrade.SOUL_CATALYST) > 0 || fuelTimeRemaining > 0);
	}

	public int getSignalStrength () {
		return isActive() ? (int) Math.floor(14 * getFuelPercent()) + 1 : 0;
	}

	public float getFuelPercent () {
		return fuelTimeRemaining / CONFIG.soulCatalystFuelTime;
	}

	/////////////////////////////////////////
	// Soul Totem
	//

	private boolean isConnected = false;
	private float fuelTimeRemaining = 0;
	private int signalStrength = 0;

	@Override
	public void update () {
		validateStructure();

		if (isActive()) {
			createChunkLoader();
			if (fuelTimeRemaining <= 0) {
				upgrades.put(Upgrade.SOUL_CATALYST, upgrades.get(Upgrade.SOUL_CATALYST) - 1);
				fuelTimeRemaining = CONFIG.soulCatalystFuelTime;
				blockUpdate();
			} else {
				fuelTimeRemaining -= CONFIG.efficiencyUpgradesRange
					.get(upgrades.get(Upgrade.EFFICIENCY) / (double) Upgrade.EFFICIENCY.getMaxQuantity());
			}
		} else {
			removeChunkLoader();
		}

		updateSignalStrength();

		if (world.isRemote) updateRenderer();
	}

	private void updateSignalStrength () {
		int signalStrength = fuelTimeRemaining <= 0 ? 0 : (int) Math.floor(15 * getFuelPercent()) + 1;
		if (signalStrength != this.signalStrength) {
			this.signalStrength = signalStrength;
			markDirty();
		}
	}

	Ticket ticket = null;

	private void createChunkLoader () {
		if (ticket == null && isActive()) {
			if (CONFIG.isChunkloader) {
				ticket = ForgeChunkManager.requestTicket(Soulus.INSTANCE, world, ForgeChunkManager.Type.NORMAL);

				if (ticket != null) {
					ChunkPos cp = new ChunkPos(pos);
					for (int x = -1; x < 2; x++) {
						for (int z = -1; z < 3; z++) {
							ForgeChunkManager.forceChunk(ticket, new ChunkPos(cp.x + x, cp.z + z));
						}
					}
				}
			} else {
				removeChunkLoader();
			}
		}
	}

	private void removeChunkLoader () {
		if (ticket != null) {
			ForgeChunkManager.releaseTicket(ticket);
			ticket = null;
		}
	}

	@Override
	public void onLoad () {
		createChunkLoader();
	}

	@Override
	public void invalidate () {
		super.invalidate();
		removeChunkLoader();
	}

	/////////////////////////////////////////
	// Structure
	//

	private void validateStructure () {
		IBlockState state = world.getBlockState(pos);
		boolean structureValid = getBlock().structure.isValid(world, pos);

		if (state.getValue(SoulTotem.CONNECTED) != structureValid) {
			world.setBlockState(pos, state.withProperty(SoulTotem.CONNECTED, structureValid), 3);
		}

		isConnected = structureValid;
	}

	/////////////////////////////////////////
	// NBT
	//

	@Override
	public void onWriteToNBT (NBTTagCompound compound) {
		compound.setFloat("fuel_time_remaining", fuelTimeRemaining);
	}

	@Override
	public void onReadFromNBT (NBTTagCompound compound) {
		fuelTimeRemaining = compound.getFloat("fuel_time_remaining");
	}

	/////////////////////////////////////////
	// Renderer
	//

	public float scale = 0;
	public float lastScale = 0;
	public float rotation = new Random().nextFloat() * 360;
	public float lastRotation = rotation;
	private float velocity = 0;
	private float timeTillParticle = 0;

	@SideOnly(Side.CLIENT)
	private void updateRenderer () {
		boolean isActive = isActive();

		lastScale = scale;
		lastRotation = rotation;
		if (isActive) {
			velocity += 0.1;
			scale += (1 - scale) / 20;
		} else {
			scale -= 0.005 + (1 - scale) / 20;
		}
		velocity *= 0.9;
		scale = Math.min(1, Math.max(0, scale));

		rotation += velocity;

		// no particles when inactive
		if (!isActive) return;

		double particleCount = CONFIG.particleCountIn;
		if (particleCount < 1) {
			timeTillParticle += 0.01 + particleCount;

			if (timeTillParticle < 1)
				return;
		}

		timeTillParticle = 0;

		for (int i = 0; i < particleCount; i++) {
			double d3 = (pos.getX() + world.rand.nextFloat());
			double d2 = world.rand.nextFloat();
			double d4 = (pos.getY() + d2);
			double d5 = (pos.getZ() + world.rand.nextFloat());
			world.spawnParticle(ParticleType.SOUL_TOTEM
				.getId(), false, d3, d4 + 0.6, d5, (d3 - pos.getX() - 0.5F), -d2 * 1.6, (d5 - pos
					.getZ() - 0.5F), 1);
		}
	}
}
