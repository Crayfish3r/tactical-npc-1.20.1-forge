package dev.zuma.tacticalnpc.command;

import java.nio.file.Files;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** DimensionDataStorage catches decode/IO exceptions. Never replace an existing failed file. */
public final class StrictSavedData {
    public static <T extends SavedData> T create(MinecraftServer server,String id,Supplier<T> factory){
        var file=server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(id+".dat");
        if(!Files.notExists(file))throw new IllegalStateException(id+" schema unreadable/rejected; see preceding schema/IO error. Refusing an empty replacement: "+file);
        return factory.get();
    }
    private StrictSavedData(){}
}
