package ace.actually.EM4ES.mixin;

import ace.actually.EM4ES.VillagerDataAccessor;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(WanderingTraderEntity.class)
public abstract class WanderingTraderEntityMixin implements VillagerDataAccessor {

    @Unique
    private Set<Identifier> em4es_offeredStructureMaps = new HashSet<>();

    @Override
    public Set<Identifier> getOfferedStructureMaps() {
        return this.em4es_offeredStructureMaps;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void writeOfferedMapsToNbt(NbtCompound nbt, CallbackInfo ci) {
        if (!this.em4es_offeredStructureMaps.isEmpty()) {
            NbtList offeredList = new NbtList();
            for (Identifier id : this.em4es_offeredStructureMaps) {
                offeredList.add(NbtString.of(id.toString()));
            }
            nbt.put("EM4ES_OfferedMaps", offeredList);
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void readOfferedMapsFromNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("EM4ES_OfferedMaps", NbtElement.LIST_TYPE)) {
            NbtList offeredList = nbt.getList("EM4ES_OfferedMaps", NbtElement.STRING_TYPE);
            this.em4es_offeredStructureMaps.clear();
            for (NbtElement element : offeredList) {
                Identifier id = Identifier.tryParse(element.asString());
                if (id != null) {
                    this.em4es_offeredStructureMaps.add(id);
                }
            }
        }
    }
}