package com.itwastall.client.render;

import com.itwastall.ItWasTall;
import com.itwastall.entity.DeepOneEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** geo/entity/deep_one.geo.json + textures/entity/deep_one.png, two emissive pin-prick eyes (deep_one_glowmask.png). */
public class DeepOneRenderer extends GeoEntityRenderer<DeepOneEntity> {
    public DeepOneRenderer(EntityRendererFactory.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ItWasTall.id("deep_one"), false));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0f;      // it hangs from the ceiling: no shadow on the floor
    }
}
