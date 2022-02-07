package io.github.ennuil.marcatexto.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import io.github.ennuil.marcatexto.MarkdownUtils;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
	@ModifyArg(
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/text/TranslatableText;<init>(Ljava/lang/String;[Ljava/lang/Object;)V"
		),
		method = "handleMessage"
	)
	private Object[] convertToMarkdown(Object[] objects) {
		if (objects[1] instanceof String string) {
			objects[1] = MarkdownUtils.parseMarkdownMessage(string);
		}
		// TODO - But what if it's an instance of Text already? Figure out a way to apply the conversion to 'em

		return objects;
	}
}
