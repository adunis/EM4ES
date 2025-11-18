package ace.actually.EM4ES;

import net.minecraft.util.Identifier;
import java.util.Set;

// This is a "duck interface" for our mixin.
public interface VillagerDataAccessor {
    Set<Identifier> getOfferedStructureMaps();
}