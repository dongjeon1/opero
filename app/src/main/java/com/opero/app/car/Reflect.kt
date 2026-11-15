package com.opero.app.car

import android.util.Log
import java.lang.reflect.Proxy

/**
 * Pleos SDK의 콜백 인터페이스와 데이터 클래스를 시그니처에 덜 의존하게 다루기 위한 도우미.
 * - 리스너는 java.lang.reflect.Proxy로 만들어 메서드 이름으로 이벤트를 구분합니다.
 * - 데이터 클래스의 값은 getter 이름으로 찾아 읽습니다.
 * SDK 버전이 바뀌어 콜백 인자가 달라져도 컴파일이 깨지지 않게 하려는 목적입니다.
 */
object Reflect {
    private const val TAG = "OperoReflect"

    fun call(target: Any, name: String, vararg args: Any?): Any? {
        val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.size == args.size }
            ?: throw NoSuchMethodException("${target.javaClass.simpleName}.$name(${args.size})")
        return method.invoke(target, *args)
    }

    fun callQuiet(target: Any?, name: String): Any? = try {
        if (target == null) null else call(target, name)
    } catch (t: Throwable) {
        Log.w(TAG, "$name failed: $t")
        null
    }

    fun proxy(iface: Class<*>, onCall: (String, Array<out Any?>) -> Unit): Any =
        Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { self, method, args ->
            when (method.name) {
                "toString" -> "OperoProxy(${iface.simpleName})"
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.getOrNull(0)
                else -> {
                    try {
                        onCall(method.name, args ?: emptyArray())
                    } catch (t: Throwable) {
                        Log.w(TAG, "listener ${method.name} failed: $t")
                    }
                    defaultValue(method.returnType)
                }
            }
        }

    /**
     * registerXxx(...) 메서드를 찾아, 리스너 인자는 Proxy로 만들어 등록합니다.
     * 1) 인자가 리스너 하나뿐인 메서드
     * 2) 기본값이 있는 다른 인자가 있는 Kotlin 함수(예: 영역 값) → 컴파일러가 만든 `$default` 메서드로 기본값 사용
     * 3) 그래도 없으면 나머지 인자를 0/null로 채워 호출
     * 등록에 성공하면 리스너를, 실패하면 null을 돌려줍니다.
     */
    fun register(target: Any, methodName: String, onCall: (String, Array<out Any?>) -> Unit): Any? {
        val all = target.javaClass.methods.filter { it.name == methodName }
        if (all.isEmpty()) {
            val near = target.javaClass.methods.map { it.name }.filter { it.startsWith("register") }.distinct().sorted()
            Diag.add("✗ ${target.javaClass.simpleName}.$methodName 없음. register 메서드: ${near.joinToString()}")
            return null
        }
        all.forEach { Diag.add("· 후보 ${describe(it)}") }
        val candidates = all.filter { m -> m.parameterTypes.any { isListenerType(it) } }
        if (candidates.isEmpty()) {
            Diag.add("✗ $methodName: 프록시로 만들 수 있는 리스너 인자가 없음(추상 클래스일 수 있음)")
            return null
        }

        // 1) 리스너만 받는 메서드
        candidates.firstOrNull { m -> m.parameterTypes.all { isListenerType(it) } }?.let { m ->
            val (args, listener) = buildArgs(m, onCall)
            if (tryInvoke(methodName) { m.invoke(target, *args.toTypedArray()) }) return ok(methodName, "리스너 인자", listener!!)
        }

        // 2) 영역 등 다른 인자는 찾은 값으로 채워 호출
        for (m in candidates.sortedBy { it.parameterTypes.size }) {
            if (m.parameterTypes.all { isListenerType(it) }) continue
            val (args, listener) = buildArgs(m, onCall)
            if (tryInvoke(methodName) { m.invoke(target, *args.toTypedArray()) }) return ok(methodName, "채운 인자", listener!!)
        }

        // 3) Kotlin 기본값 인자: $default 메서드 사용
        for (m in candidates) {
            val defaults = findDefaultMethod(target, m) ?: continue
            val (filled, listener) = buildArgs(m, onCall)
            val args = ArrayList<Any?>()
            args += target
            args.addAll(filled)
            var mask = 0
            for (i in m.parameterTypes.indices) if (!isListenerType(m.parameterTypes[i]) && i < 32) mask = mask or (1 shl i)
            args += mask
            args += null
            if (tryInvoke(methodName) { defaults.invoke(null, *args.toTypedArray()) }) return ok(methodName, "기본값 인자", listener!!)
        }
        Diag.add("✗ $methodName 등록 실패")
        return null
    }

    /**
     * DoorArea·SeatArea 같은 '영역' 인자 값을 만듭니다. 운전석(앞 왼쪽)에 해당하는 값을 우선합니다.
     * enum 상수 → 정적 상수 → companion 상수 → sealed 하위 object → Int 생성자 순으로 찾습니다.
     */
    fun areaValue(type: Class<*>): Any? {
        val options = LinkedHashMap<String, Any>()
        type.enumConstants?.forEach { options[(it as Enum<*>).name] = it }
        type.fields.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && type.isAssignableFrom(it.type) }
            .forEach { f -> runCatching { f.get(null) }.getOrNull()?.let { options[f.name] = it } }
        runCatching { type.getField("Companion").get(null) }.getOrNull()?.let { comp ->
            comp.javaClass.methods.filter { it.parameterTypes.isEmpty() && type.isAssignableFrom(it.returnType) }
                .forEach { m -> runCatching { m.invoke(comp) }.getOrNull()?.let { options[key(m.name).uppercase()] = it } }
        }
        type.declaredClasses.filter { type.isAssignableFrom(it) }.forEach { c ->
            runCatching { c.getField("INSTANCE").get(null) }.getOrNull()?.let { options[c.simpleName] = it }
        }
        if (options.isEmpty()) {
            type.constructors.firstOrNull { it.parameterTypes.size == 1 && it.parameterTypes[0] == Integer.TYPE }?.let { ctor ->
                runCatching { ctor.newInstance(1) }.getOrNull()?.let { options["new(1)"] = it }
            }
        }
        if (options.isEmpty()) {
            // 예: DoorArea(DoorValue[]) → 운전석 값 하나만 담은 배열로 생성
            type.constructors.firstOrNull { it.parameterTypes.size == 1 && it.parameterTypes[0].isArray }?.let { ctor ->
                val element = ctor.parameterTypes[0].componentType
                val value = areaValue(element)
                if (value != null) {
                    val array = java.lang.reflect.Array.newInstance(element, 1)
                    java.lang.reflect.Array.set(array, 0, value)
                    runCatching { ctor.newInstance(array) }
                        .onFailure { Diag.add("· ${type.simpleName} 생성 실패: ${it.cause ?: it}".take(200)) }
                        .getOrNull()?.let { options["[$value]"] = it }
                }
            }
        }
        Diag.add("· ${type.simpleName} 값: ${options.keys.joinToString().ifEmpty { "없음 / 생성자 " + type.constructors.joinToString { c -> c.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName } } }}".take(220))
        val prefer = listOf("DRIVER", "FRONT_LEFT", "ROW_1_LEFT", "ROW1_LEFT", "FL", "LEFT", "FRONT", "ALL")
        for (p in prefer) options.entries.firstOrNull { it.key.uppercase().replace(" ", "_") == p }?.let { return it.value }
        for (p in prefer) options.entries.firstOrNull { it.key.uppercase().contains(p) }?.let { return it.value }
        return options.values.firstOrNull()
    }

    /** 리스너 인자가 여러 개면 모두 Proxy로 만듭니다. 두 번째 이후 람다(Function)는 실패 콜백으로 봅니다. */
    private fun buildArgs(m: java.lang.reflect.Method, onCall: (String, Array<out Any?>) -> Unit): Pair<List<Any?>, Any?> {
        var first: Any? = null
        var lambdaCount = 0
        val args = m.parameterTypes.map { t ->
            if (isListenerType(t)) {
                val isLambda = t.name.startsWith("kotlin.jvm.functions")
                val failure = isLambda && lambdaCount++ > 0
                val p = proxy(t) { name, a -> onCall(if (failure) "onFailed" else name, a) }
                if (first == null) first = p
                p
            } else zeroOf(t)
        }
        return args to first
    }

    private fun ok(methodName: String, how: String, listener: Any): Any {
        Diag.add("✓ $methodName 등록($how)")
        return listener
    }

    /** Kotlin이 기본값 인자를 위해 만드는 정적 메서드 `name$default`를 찾습니다(클래스 또는 인터페이스의 DefaultImpls). */
    /** 리스너로 쓸 수 있는 인자 타입: java.* 가 아닌 인터페이스(콜백 인터페이스, Kotlin 람다 FunctionN 포함). */
    private fun isListenerType(type: Class<*>): Boolean =
        type.isInterface && !type.name.startsWith("java.")

    private fun findDefaultMethod(target: Any, method: java.lang.reflect.Method): java.lang.reflect.Method? {
        val name = method.name + "\$default"
        val owners = mutableListOf<Class<*>>()
        var c: Class<*>? = target.javaClass
        while (c != null) {
            owners += c
            c.interfaces.forEach { iface ->
                owners += iface
                runCatching { Class.forName(iface.name + "\$DefaultImpls", false, iface.classLoader) }.getOrNull()?.let { owners += it }
            }
            c = c.superclass
        }
        runCatching { Class.forName(method.declaringClass.name + "\$DefaultImpls", false, method.declaringClass.classLoader) }
            .getOrNull()?.let { owners += it }
        for (owner in owners.distinct()) {
            val found = owner.declaredMethods.firstOrNull { d ->
                d.name == name &&
                    java.lang.reflect.Modifier.isStatic(d.modifiers) &&
                    d.parameterTypes.size == method.parameterTypes.size + 3 &&
                    method.parameterTypes.indices.all { i -> d.parameterTypes[i + 1] == method.parameterTypes[i] }
            }
            if (found != null) {
                found.isAccessible = true
                return found
            }
        }
        return null
    }

    private inline fun tryInvoke(methodName: String, block: () -> Unit): Boolean = try {
        block()
        true
    } catch (t: Throwable) {
        Diag.add("· $methodName 호출 실패: ${(t.cause ?: t)}".take(220))
        false
    }

    private fun describe(m: java.lang.reflect.Method) =
        "${m.name}(${m.parameterTypes.joinToString { it.simpleName }})"

    private fun zeroOf(type: Class<*>): Any? = if (!type.isPrimitive && !type.name.startsWith("java.")) areaValue(type) else when (type) {
        java.lang.Boolean.TYPE -> false
        Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0f
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        else -> null
    }

    fun lastNumber(args: Array<out Any?>): Double? =
        (args.lastOrNull { it is Number } as? Number)?.toDouble()

    /** 콜백 인자를 짧게 보여 줍니다(진단용). */
    fun show(args: Array<out Any?>): String = args.joinToString { a ->
        when (a) {
            null -> "null"
            is Number, is Boolean, is CharSequence, is Enum<*> -> a.toString()
            else -> a.javaClass.simpleName + runCatching {
                getters(a).take(6).joinToString(prefix = "{", postfix = "}") { m -> key(m.name) + "=" + m.invoke(a) }
            }.getOrDefault("")
        }
    }.take(200)

    private fun getters(obj: Any) = obj.javaClass.methods.filter {
        it.parameterTypes.isEmpty() && it.name != "getClass" &&
            (it.name.startsWith("get") || it.name.startsWith("is"))
    }

    private fun key(methodName: String) =
        methodName.removePrefix("get").removePrefix("is").lowercase()

    /** 이름 후보 중 처음 일치하는 getter 값을 돌려줍니다(정확히 일치 → 포함 순). */
    fun prop(obj: Any?, vararg names: String): Any? {
        if (obj == null) return null
        val list = getters(obj)
        for (n in names) {
            list.firstOrNull { key(it.name) == n.lowercase() }?.let { return runCatching { it.invoke(obj) }.getOrNull() }
        }
        for (n in names) {
            list.firstOrNull { key(it.name).contains(n.lowercase()) }?.let { return runCatching { it.invoke(obj) }.getOrNull() }
        }
        return null
    }

    fun string(obj: Any?, vararg names: String): String? =
        (prop(obj, *names) as? CharSequence)?.toString()?.takeIf { it.isNotBlank() }

    fun double(obj: Any?, vararg names: String): Double? =
        (prop(obj, *names) as? Number)?.toDouble()

    /** getter 이름에 tokens가 모두 들어간 숫자 값을 찾습니다. (예: remain + time) */
    fun findNumber(obj: Any?, vararg tokens: String): Pair<String, Double>? {
        if (obj == null) return null
        for (m in getters(obj)) {
            val k = key(m.name)
            if (tokens.all { k.contains(it) }) {
                val v = runCatching { m.invoke(obj) }.getOrNull()
                if (v is Number) return k to v.toDouble()
            }
        }
        return null
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0f
        else -> null
    }
}
