package org.dimdev.dimdoors.client;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import net.minecraft.block.Block;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.client.model.ModelProviderContext;
import net.fabricmc.fabric.api.client.model.ModelProviderException;
import net.fabricmc.fabric.api.client.model.ModelVariantProvider;

import org.dimdev.dimdoors.DimensionalDoors;
import org.dimdev.dimdoors.block.door.DimensionalDoorBlockRegistrar;
import org.dimdev.dimdoors.item.DimensionalDoorItemRegistrar;

public class DimensionalDoorModelVariantProvider implements ModelVariantProvider {
	private static final Identifier childItem = DimensionalDoors.id("item/child_item");

	@Override
	public @Nullable UnbakedModel loadModelVariant(ModelIdentifier modelId, ModelProviderContext context) throws ModelProviderException {
		Identifier identifier = new Identifier(modelId.getNamespace(), modelId.getPath());

		DimensionalDoorBlockRegistrar blockRegistrar = DimensionalDoors.getDimensionalDoorBlockRegistrar();
		if (blockRegistrar.isMapped(identifier)) {
			Identifier mapped = blockRegistrar.get(identifier);
			//ModelIdentifier newId = new ModelIdentifier(mapped, modelId.getVariant());
			//UnbakedModel model = context.loadModel(newId);
			//if (model != null) return model;

			Block original = Registries.BLOCK.get(mapped);
			Set<String> originalProperties = original.getStateManager().getProperties().stream().map(Property::getName).collect(Collectors.toSet());

			ArrayList<String> variantArray = new ArrayList<>();
			for (String part : modelId.getVariant().split(",")) {
				if (originalProperties.contains(part.split("=")[0])) variantArray.add(part);
			}
			String variant = String.join(",", variantArray);
			ModelIdentifier newId = new ModelIdentifier(mapped, variant);
			return context.loadModel(newId);
		} else if (identifier.getPath().startsWith(DimensionalDoorItemRegistrar.PREFIX)) {
			return context.loadModel(childItem);
		}
		return null;
	}
}
