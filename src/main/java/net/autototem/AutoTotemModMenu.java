package net.autototem;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Lets Mod Menu open the settings screen from the mod list. */
public class AutoTotemModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AutoTotemConfigScreen::new;
    }
}
