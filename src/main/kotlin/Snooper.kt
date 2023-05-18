import com.pokeemu.client.Client
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.asm.Advice
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy
import net.bytebuddy.matcher.ElementMatchers

val SEND_PACKET_METHODS = arrayOf(
    "f.el#Mz0(Lf/KN;)V",
    "f.Nh#Qy0(Lf/Qm;)V",
    "f.Ip#Lu0(Lf/hr0;)V",
)

private fun recursivePrint(obj: Any, sb: StringBuilder, indent: Int, visitedObjects: MutableList<Any> = mutableListOf()) {
    if (visitedObjects.contains(obj)) {
        return
    }
    visitedObjects.add(obj)
    val indentStr = " ".repeat(indent * 4)
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
            } else if (!value.javaClass.isPrimitive && !value.javaClass.name.startsWith("java.lang") && !value.javaClass.name.startsWith("java.util")) {
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
        printObj(packet)
    }
}

fun initHooks() {
    ByteBuddyAgent.install()
    val bb = ByteBuddy()

    // Send Hooks
    for (method in SEND_PACKET_METHODS) {
        val clazzName = method.substringBefore('#')
        val methodName = method.substringAfter('#').substringBefore('(')
        val clazz = Class.forName(clazzName)
        bb.rebase(clazz)
            .visit(
                Advice.to(SendInterceptor::class.java).on(ElementMatchers.named(methodName))
            )
            .make()
            .load(clazz.classLoader, ClassReloadingStrategy.fromInstalledAgent())
    }
}

fun main() {
    initHooks()
    Client.main(emptyArray())
}