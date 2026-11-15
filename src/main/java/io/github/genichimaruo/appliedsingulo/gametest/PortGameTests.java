package io.github.genichimaruo.appliedsingulo.gametest;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
/** Registry-based equivalent of the old annotation scanner; keeps original test counts and timeouts. */
public final class PortGameTests {
 private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, "applied_singulo");
 private static final Class<?>[] SUITES = {MeInterfaceGameTests.class, PatternIdentityGameTests.class, PatternRequestGameTests.class, StockPatternGameTests.class};
 public static void init(IEventBus bus) {
  for (Class<?> suite:SUITES) for(Method m:suite.getDeclaredMethods()) if(m.isAnnotationPresent(GameTest.class)) {
   FUNCTIONS.register(name(suite,m), () -> helper -> {try {m.invoke(null,helper);} catch(ReflectiveOperationException e){throw new RuntimeException("GameTest invocation failed",e.getCause()==null?e:e.getCause());}});
  }
  FUNCTIONS.register(bus); bus.addListener(PortGameTests::register);
 }
 private static String name(Class<?> s,Method m){return (s.getSimpleName()+"."+m.getName()).toLowerCase(java.util.Locale.ROOT);}
 private static void register(RegisterGameTestsEvent event){
  var env=event.registerEnvironment(Identifier.fromNamespaceAndPath("applied_singulo","default"));
  for(Class<?> suite:SUITES) for(Method m:suite.getDeclaredMethods()) {var a=m.getAnnotation(GameTest.class); if(a==null)continue;
   var id=Identifier.fromNamespaceAndPath("applied_singulo",name(suite,m));
   event.registerTest(id,new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,id),new TestData<>(env,Identifier.fromNamespaceAndPath("applied_singulo",a.template()),a.timeoutTicks(),0,true)));
  }
 }
}
