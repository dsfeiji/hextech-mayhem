package com.hexmayhem.mixin;

import com.hexmayhem.game.HexCombat;
import com.hexmayhem.game.HexGame;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Shadow
	protected ItemStack useItem;

	@ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
	private float hexmayhem$modifyDamage(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		return self.level().isClientSide ? amount : HexCombat.modifyIncoming(self, source, amount);
	}

	@Inject(method = "hurt", at = @At("RETURN"))
	private void hexmayhem$afterHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide) {
			HexCombat.afterHurt(self, source, amount, cir.getReturnValueZ());
		}
	}

	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void hexmayhem$onCompleteUsing(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && useItem.has(DataComponents.FOOD)) {
			HexGame.onFoodEaten(player);
		}
	}
}
