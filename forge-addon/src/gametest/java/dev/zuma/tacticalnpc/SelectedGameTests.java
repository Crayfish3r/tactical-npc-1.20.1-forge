package dev.zuma.tacticalnpc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Test-only: use Forge's registration event; keep annotations, fixtures and timing intact. */
@Mod.EventBusSubscriber(modid = TacticalNpcAiMod.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SelectedGameTests {
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) throws ClassNotFoundException {
        String catalog = System.getProperty("tactical.tests.catalog", "");
        if (catalog.isBlank()) throw new IllegalStateException("Missing GameTest catalog; use Gradle combatTests run");
        String filter = System.getProperty("tactical.tests.selection", "");
        Set<String> requested = new HashSet<>();
        if (!filter.isBlank()) {
            for (String token : filter.split(",", -1)) {
                if (token.isBlank() || !requested.add(token))
                    throw new IllegalArgumentException("Empty/duplicate test selector: " + token);
            }
        }
        Set<String> matched = new HashSet<>();
        List<Method> selected = new ArrayList<>();
        for (String className : catalog.split(",")) {
            Class<?> type = Class.forName(className);
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(GameTest.class)) continue;
                String id = type.getSimpleName() + "#" + method.getName();
                boolean include = requested.isEmpty();
                for (String token : requested) {
                    if (token.equals(type.getSimpleName()) || token.equals(id)) {
                        matched.add(token);
                        include = true;
                    }
                }
                if (include) selected.add(method);
            }
        }
        if (!matched.equals(requested) || selected.isEmpty()) {
            requested.removeAll(matched);
            throw new IllegalArgumentException("Empty selection or unknown GameTests: " + requested);
        }
        selected.sort(Comparator.comparing(m -> m.getDeclaringClass().getSimpleName() + "#" + m.getName()));
        selected.forEach(event::register);
        System.out.println("TACTICAL_TEST_SELECTION profile=" + System.getProperty("tactical.tests.profile")
                + " count=" + selected.size() + " ids=" + Arrays.toString(selected.stream()
                .map(m -> m.getDeclaringClass().getSimpleName() + "#" + m.getName()).toArray()));
    }

    private SelectedGameTests() {}
}
