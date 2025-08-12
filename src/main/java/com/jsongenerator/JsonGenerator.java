package com.jsongenerator;

import com.jsongenerator.discovery.GameDataExporter;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(JsonGenerator.MODID)
public class JsonGenerator {
    public static final String MODID = "jsongenerator";
    private static final Logger LOGGER = LogManager.getLogger();
    private final GameDataExporter dataExporter = new GameDataExporter();

    public JsonGenerator() {
        LOGGER.info("JSON Generator mod initializing");
        
        // Register our main mod class to listen for FML events
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("JSON Generator mod client setup");
        
        // Export game data when the client is fully loaded
        event.enqueueWork(dataExporter::exportAll);
    }
}
