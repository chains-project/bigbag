package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.factory.Factory;

/**
 * Generic Spoon transformation to fix SpongeAPI breaking changes from older versions to 8.0.0
 * Focuses on the most common breaking changes:
 * - Sponge.getCommandManager() -> Sponge.game().commandManager() 
 * - Sponge.getServer() -> Sponge.game().server()
 * - Sponge.getChannelRegistrar() -> Sponge.game().channelManager()
 */
public class Main extends AbstractProcessor<CtInvocation> {
    
    @Override
    public void process(CtInvocation invocation) {
        Factory factory = getFactory();
        
        // Handle Sponge.getCommandManager() -> Sponge.game().commandManager()
        if (isSpongeGetCommandManager(invocation)) {
            replaceSpongeGetCommandManager(invocation, factory);
        }
        
        // Handle Sponge.getServer() -> Sponge.game().server()
        if (isSpongeGetServer(invocation)) {
            replaceSpongeGetServer(invocation, factory);
        }
        
        // Handle Sponge.getChannelRegistrar() -> Sponge.game().channelManager()
        if (isSpongeGetChannelRegistrar(invocation)) {
            replaceSpongeGetChannelRegistrar(invocation, factory);
        }
    }
    
    private boolean isSpongeGetCommandManager(CtInvocation invocation) {
        // Matches Sponge.getCommandManager() 
        return invocation.getExecutable().toString().equals("Sponge.getCommandManager()")
                || invocation.getExecutable().toString().equals("org.spongepowered.api.Sponge.getCommandManager()");
    }
    
    private void replaceSpongeGetCommandManager(CtInvocation invocation, Factory factory) {
        // Replace with Sponge.game().commandManager()
        CtTypeAccess<?> spongeAccess = factory.createTypeAccess(
            factory.Type().createReference("org.spongepowered.api.Sponge")
        );
        
        CtInvocation newInvocation = factory.createInvocation(
            factory.createInvocation(spongeAccess, "game"),
            "commandManager"
        );
        
        invocation.replace(newInvocation);
    }
    
    private boolean isSpongeGetServer(CtInvocation invocation) {
        // Matches Sponge.getServer()
        return invocation.getExecutable().toString().equals("Sponge.getServer()")
                || invocation.getExecutable().toString().equals("org.spongepowered.api.Sponge.getServer()");
    }
    
    private void replaceSpongeGetServer(CtInvocation invocation, Factory factory) {
        // Replace with Sponge.game().server()
        CtTypeAccess<?> spongeAccess = factory.createTypeAccess(
            factory.Type().createReference("org.spongepowered.api.Sponge")
        );
        
        CtInvocation newInvocation = factory.createInvocation(
            factory.createInvocation(spongeAccess, "game"),
            "server"
        );
        
        invocation.replace(newInvocation);
    }
    
    private boolean isSpongeGetChannelRegistrar(CtInvocation invocation) {
        // Matches Sponge.getChannelRegistrar()
        return invocation.getExecutable().toString().equals("Sponge.getChannelRegistrar()")
                || invocation.getExecutable().toString().equals("org.spongepowered.api.Sponge.getChannelRegistrar()");
    }
    
    private void replaceSpongeGetChannelRegistrar(CtInvocation invocation, Factory factory) {
        // Replace with Sponge.game().channelManager()
        CtTypeAccess<?> spongeAccess = factory.createTypeAccess(
            factory.Type().createReference("org.spongepowered.api.Sponge")
        );
        
        CtInvocation newInvocation = factory.createInvocation(
            factory.createInvocation(spongeAccess, "game"),
            "channelManager"
        );
        
        invocation.replace(newInvocation);
    }
}