package team.creative.itemphysiclite;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.common.Mod;
import team.creative.creativecore.CreativeCore;
import team.creative.creativecore.ICreativeLoader;
import team.creative.creativecore.client.ClientLoader;
import team.creative.creativecore.common.config.holder.CreativeConfigRegistry;
import team.creative.creativecore.common.config.sync.ConfigSynchronization;
import team.creative.itemphysiclite.mixin.EntityAccessor;

@Mod(ItemPhysicLite.MODID)
public class ItemPhysicLite implements ClientLoader {
    
    public static final Logger LOGGER = LogManager.getLogger(ItemPhysicLite.MODID);
    public static final String MODID = "itemphysiclite";
    private static Minecraft mc = Minecraft.getInstance();
    public static ItemPhysicLiteConfig CONFIG;
    public static long lastTickTime;
    private static final double RANDOM_Y_OFFSET_SCALE = 0.05 / (Math.PI * 2);
    
    public static boolean render(ItemEntity entity, float entityYaw, float partialTicks, PoseStack pose, MultiBufferSource buffer, int packedLight, ItemRenderer itemRenderer, RandomSource rand) {
        if (entity.getAge() == 0)
            return false;
        
        pose.pushPose();
        ItemStack itemstack = entity.getItem();
        rand.setSeed(itemstack.isEmpty() ? 187 : Item.getId(itemstack.getItem()) + itemstack.getDamageValue());
        BakedModel bakedmodel = itemRenderer.getModel(itemstack, entity.level(), (LivingEntity) null, entity.getId());
        boolean flag = bakedmodel.isGui3d();
        int j = getModelCount(itemstack);
        float rotateBy = (System.nanoTime() - lastTickTime) / 200000000F * CONFIG.rotateSpeed;
        if (mc.isPaused())
            rotateBy = 0;
        
        Vec3 motionMultiplier = getStuckSpeedMultiplier(entity);
        if (motionMultiplier != null && motionMultiplier.lengthSqr() > 0)
            rotateBy *= motionMultiplier.x * 0.2;
        
        pose.mulPose(com.mojang.math.Axis.XP.rotation((float) Math.PI / 2));
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(entity.getYRot()));
        
        boolean applyEffects = entity.getAge() != 0 && (flag || mc.options != null);
        
        //Handle Rotations
        if (applyEffects) {
            if (flag) {
                if (!entity.onGround()) {
                    rotateBy *= 2;
                    Fluid fluid = getFluid(entity);
                    if (fluid == null)
                        fluid = getFluid(entity, true);
                    if (fluid != null)
                        rotateBy /= (1 + getViscosity(fluid, entity.level()));
                    
                    entity.setXRot(entity.getXRot() + rotateBy);
                }
            } else if (entity != null && !Double.isNaN(entity.getX()) && !Double.isNaN(entity.getY()) && !Double.isNaN(entity.getZ()) && entity.level() != null) {
                if (entity.onGround()) {
                    if (!flag)
                        entity.setXRot(0);
                } else {
                    rotateBy *= 2;
                    Fluid fluid = getFluid(entity);
                    if (fluid != null)
                        rotateBy /= (1 + getViscosity(fluid, entity.level()));
                    
                    entity.setXRot(entity.getXRot() + rotateBy);
                }
            }
            
            if (flag)
                pose.translate(0, -0.2, -0.08);
            else if (entity.level().getBlockState(entity.blockPosition()).getBlock() == Blocks.SNOW || entity.level().getBlockState(entity.blockPosition().below())
                    .getBlock() == Blocks.SOUL_SAND)
                pose.translate(0, 0.0, -0.14 - entity.bobOffs * RANDOM_Y_OFFSET_SCALE);
            else
                pose.translate(0, 0, -0.04 - entity.bobOffs * RANDOM_Y_OFFSET_SCALE);
            
            double height = 0.2;
            if (flag)
                pose.translate(0, height, 0);
            pose.mulPose(com.mojang.math.Axis.YP.rotation(entity.getXRot()));
            if (flag)
                pose.translate(0, -height, 0);
        }
        
        if (!flag) {
            float f7 = -0.0F * (j - 1) * 0.5F;
            float f8 = -0.0F * (j - 1) * 0.5F;
            float f9 = -0.09375F * (j - 1) * 0.5F;
            pose.translate(f7, f8, f9);
        }
        
        for (int k = 0; k < j; ++k) {
            pose.pushPose();
            if (k > 0) {
                if (flag) {
                    float f11 = (rand.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float f13 = (rand.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float f10 = (rand.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    pose.translate(f11, f13, f10);
                }
            }
            
            itemRenderer.render(itemstack, ItemDisplayContext.GROUND, false, pose, buffer, packedLight, OverlayTexture.NO_OVERLAY, bakedmodel);
            pose.popPose();
            if (!flag)
                pose.translate(0.0, 0.0, 0.09375F); // pose.translate(0.0, 0.0, 0.05375F);
                
        }
        
        pose.popPose();
        return true;
    }
    
    public static Fluid getFluid(ItemEntity item) {
        return getFluid(item, false);
    }
    
    public static Fluid getFluid(ItemEntity item, boolean below) {
        if (item.level() == null)
            return null;
        
        double d0 = item.position().y;
        BlockPos pos = item.blockPosition();
        if (below)
            pos = pos.below();
        
        FluidState state = item.level().getFluidState(pos);
        Fluid fluid = state.getType();
        if (fluid == null || fluid.getTickDelay(item.level()) == 0) {
            return null;
        }
        
        if (below)
            return fluid;
        
        double filled = state.getHeight(item.level(), pos);
        
        if (d0 - pos.getY() - 0.2 <= filled)
            return fluid;
        return null;
    }
    
    public static int getModelCount(ItemStack stack) {
        
        if (stack.getCount() > 48)
            return 5;
        if (stack.getCount() > 32)
            return 4;
        if (stack.getCount() > 16)
            return 3;
        if (stack.getCount() > 1)
            return 2;
        
        return 1;
    }
    
    public static Vec3 getStuckSpeedMultiplier(Entity entity) {
        return ((EntityAccessor) entity).getStuckSpeedMultiplier();
    }
    
    public static float getViscosity(Fluid fluid, Level level) {
        if (fluid == null)
            return 0;
        return CreativeCore.loader().getFluidViscosityMultiplier(fluid, level);
    }
    
    public ItemPhysicLite() {
        ICreativeLoader loader = CreativeCore.loader();
        loader.registerClient(this);
    }
    
    @Override
    public void onInitializeClient() {
        ICreativeLoader loader = CreativeCore.loader();
        loader.registerDisplayTest(() -> loader.ignoreServerNetworkConstant(), (a, b) -> true);
        loader.registerClientRenderGui(x -> lastTickTime = System.nanoTime());
        
        CreativeConfigRegistry.ROOT.registerValue(MODID, CONFIG = new ItemPhysicLiteConfig(), ConfigSynchronization.CLIENT, false);
    }
}
