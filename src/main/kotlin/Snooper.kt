import com.pokeemu.client.Client
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.Advice
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.matcher.ElementMatchers

val SEND_PACKET_METHODS = arrayOf(
    "f.in#F2(Lf/x1;)V", // Handshake packets (enchanging encryption keys etc)
    "f.O30#MD0(Lf/wB0;)V", // Game packets
    "f.ld0#CK(Lf/oV;)V", // Login packets
    "f.FJ#D4(Lf/VK0;)V", // Chat packets
)

val HANDLE_PACKET_METHOD = "HA0()V" // every incoming packet inherits a handle method that is called in a new thread after the packet got decoded

val POKEMMO_PACKAGE = "f." // the obfuscated package name of all PokeMMO classes

// TODO: this is a very naive implementation. Enums and other objects are not handled properly and produce ugly output
private fun recursivePrint(obj: Any, sb: StringBuilder, indent: Int, visitedObjects: MutableList<Any> = mutableListOf()) {
    if (visitedObjects.contains(obj)) {
        return
    }
    visitedObjects.add(obj)
    val indentStr = " ".repeat(indent * 2)
    val fields = obj.javaClass.declaredFields
    for (field in fields) {
        try {
            field.isAccessible = true
        } catch (e: Exception) {
            continue
        }
        val value = field.get(obj)
        if (value != null) {
            sb.append("\n")
            sb.append(indentStr)
            sb.append(field.name)
            sb.append(": ")
            if (value.javaClass.isArray) {
                sb.append("[")
                for (i in 0 until java.lang.reflect.Array.getLength(value)) {
                    if (i != 0) {
                        sb.append(", ")
                    }
                    sb.append(java.lang.reflect.Array.get(value, i))
                }
                sb.append("]")
            } else if (!value.javaClass.isPrimitive && value.javaClass.name.startsWith(POKEMMO_PACKAGE)) {
                sb.append(value.javaClass.name)
                sb.append(" {")
                recursivePrint(value, sb, indent + 1, visitedObjects)
                sb.append("\n")
                sb.append(indentStr)
                sb.append("}")
            } else {
                sb.append(value)
            }
        }
    }
}

fun printObj(obj: Any) {
    val sb = StringBuilder()
    sb.append(obj.javaClass.name)
    sb.append(" {")
    recursivePrint(obj, sb, 1)
    sb.append("\n}")
    println(sb.toString())
}

object SendInterceptor {
    @JvmStatic
    @Advice.OnMethodEnter
    fun intercept(@Advice.Argument(0) packet: Any) {
        print("Outgoing packet: ")
        printObj(packet)
    }
}

object RecvInterceptor {
    @JvmStatic
    @Advice.OnMethodEnter
    fun intercept(@Advice.This packet: Any) {
        print("Incoming packet: ")
        printObj(packet)
    }
}

fun initHooks() {
    ByteBuddyAgent.install()

    // Send Hooks
    for (method in SEND_PACKET_METHODS) {
        val clazzName = method.substringBefore('#')
        val methodName = method.substringAfter('#').substringBefore('(')
        AgentBuilder.Default()
            .with(AgentBuilder.InitializationStrategy.SelfInjection.Eager())
            .type(ElementMatchers.named(clazzName))
            .transform { builder, _, _, _, _ ->
                builder.method(ElementMatchers.named(methodName))
                    .intercept(Advice.to(SendInterceptor::class.java))
            }
            .installOnByteBuddyAgent()
    }
    // Recv Hooks
    // TODO: there is currently no way to distinguish between different packet types (e.g. game packets, login packets, chat packets etc)
    val methodName = HANDLE_PACKET_METHOD.substringBefore('(')
    AgentBuilder.Default()
        .with(AgentBuilder.InitializationStrategy.SelfInjection.Eager())
        .type(ElementMatchers.nameStartsWith(POKEMMO_PACKAGE))
        .transform { builder, _, _, _, _ ->
            builder.method(ElementMatchers.named(methodName))
                .intercept(Advice.to(RecvInterceptor::class.java))
        }
        .installOnByteBuddyAgent()
}

fun main() {
    initHooks()
    Client.main(emptyArray())
}