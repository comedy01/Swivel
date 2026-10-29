package dev.swivel.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class ControlifyBindings {
    private static final Logger LOGGER = LoggerFactory.getLogger("swivel");
    private static final String API = "dev.isxander.controlify.api.bind.";

    private ControlifyBindings() {
    }

    static void register(KeyMapping... keys) {
        Class<?> apiClass;
        try {
            apiClass = Class.forName(API + "ControlifyBindApi");
        } catch (ClassNotFoundException | LinkageError e) {
            return;
        }
        try {
            Class<?> callbackClass = Class.forName(API + "ControlifyBindApi$RegistryCallback");
            Class<?> builderClass = Class.forName(API + "InputBindingBuilder");
            Class<?> contextClass = Class.forName("dev.isxander.controlify.bindings.BindContext");
            Object contexts = Array.newInstance(contextClass, 1);
            Array.set(contexts, 0, contextClass.getField("IN_GAME").get(null));

            Method id = builderClass.getMethod("id", String.class, String.class);
            Method name = builderClass.getMethod("name", Component.class);
            Method description = builderClass.getMethod("description", Component.class);
            Method category = builderClass.getMethod("category", Component.class);
            Method allowedContexts = builderClass.getMethod("allowedContexts", contexts.getClass());
            Method keyEmulation = builderClass.getMethod("keyEmulation", KeyMapping.class);

            Object api = apiClass.getMethod("get").invoke(null);
            Method registerBinding = apiClass.getMethod("registerBinding", callbackClass);
            Component swivel = Component.translatable("key.category." + SwivelClient.MOD_ID + ".main");

            for (KeyMapping key : keys) {
                String path = key.getName().substring(("key." + SwivelClient.MOD_ID + ".").length());
                InvocationHandler fill = (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            default -> "Swivel binding " + path;
                        };
                    }
                    Object builder = args[0];
                    id.invoke(builder, SwivelClient.MOD_ID, path);
                    name.invoke(builder, Component.translatable(key.getName()));
                    description.invoke(builder, Component.translatable(key.getName() + ".controller"));
                    category.invoke(builder, swivel);
                    allowedContexts.invoke(builder, contexts);
                    keyEmulation.invoke(builder, key);
                    return builder;
                };
                Object callback = Proxy.newProxyInstance(
                        callbackClass.getClassLoader(), new Class<?>[] {callbackClass}, fill);
                registerBinding.invoke(api, callback);
            }
            LOGGER.info("Added {} Swivel bindings to Controlify", keys.length);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.warn("Could not add Swivel's bindings to Controlify: {}", e.toString());
        }
    }
}
