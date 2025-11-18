package ace.actually.EM4ES;

import net.minecraft.util.Identifier;
import java.util.Set;

public interface VillagerDataAccessor {
    Set<Identifier> getOfferedStructureMaps();

    boolean isSearching();
    void setSearching(boolean searching);

    // NEW METHODS FOR LEVEL MEMORY
    int getLastMapLevelGenerated();
    void setLastMapLevelGenerated(int level);
}