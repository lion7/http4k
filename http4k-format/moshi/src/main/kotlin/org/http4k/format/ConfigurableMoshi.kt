package org.http4k.format

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.buffer
import okio.source
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.ContentType.Companion.APPLICATION_JSON
import org.http4k.format.MoshiNode.MoshiArray
import org.http4k.format.MoshiNode.MoshiBoolean
import org.http4k.format.MoshiNode.MoshiNull
import org.http4k.format.MoshiNode.MoshiNumber
import org.http4k.format.MoshiNode.MoshiObject
import org.http4k.format.MoshiNode.MoshiString
import org.http4k.lens.BiDiBodyLensSpec
import org.http4k.lens.BiDiMapping
import org.http4k.lens.BiDiWsMessageLensSpec
import org.http4k.lens.ContentNegotiation
import org.http4k.lens.ContentNegotiation.Companion.None
import org.http4k.lens.string
import org.http4k.websocket.WsMessage
import java.io.InputStream
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass

open class ConfigurableMoshi(
    builder: Moshi.Builder,
    val defaultContentType: ContentType = APPLICATION_JSON
) : AutoMarshallingJson<MoshiNode>() {

    private val moshi: Moshi = builder.build()

    override fun asFormatString(input: Any): String = moshi.adapter(input.javaClass).toJson(input)

    fun <T : Any> asJsonString(t: T, c: KClass<T>): String = moshi.adapter(c.java).toJson(t)

    override fun <T : Any> asA(input: String, target: KClass<T>): T = moshi.adapter(target.java).fromJson(input)!!

    override fun <T : Any> asA(input: InputStream, target: KClass<T>): T = moshi.adapter(target.java).fromJson(
        input.source().buffer()
    )!!

    inline fun <reified T : Any> Body.Companion.auto(
        description: String? = null,
        contentNegotiation: ContentNegotiation = None,
        contentType: ContentType = defaultContentType
    ): BiDiBodyLensSpec<T> =
        autoBody(description, contentNegotiation, contentType)

    inline fun <reified T : Any> autoBody(
        description: String? = null,
        contentNegotiation: ContentNegotiation = None,
        contentType: ContentType = defaultContentType
    ): BiDiBodyLensSpec<T> =
        Body.string(contentType, description, contentNegotiation).map({ asA(it, T::class) }, {
            asFormatString(it)
        })

    inline fun <reified T : Any> WsMessage.Companion.auto(): BiDiWsMessageLensSpec<T> =
        WsMessage.string().map({ it.asA(T::class) }, { asFormatString(it) })

    override fun asJsonObject(input: Any): MoshiNode = asA(asFormatString(input))

    override fun MoshiNode.asPrettyJsonString(): String = asFormatString(this)

    override fun MoshiNode.asCompactJsonString(): String = asFormatString(this)

    override fun String.asJsonObject(): MoshiNode = asA(this, MoshiNode::class)

    override fun String?.asJsonValue(): MoshiNode = this?.let(::MoshiString) ?: MoshiNull

    override fun Int?.asJsonValue(): MoshiNode = this?.let(::MoshiNumber) ?: MoshiNull

    override fun Double?.asJsonValue(): MoshiNode = this?.let(::MoshiNumber) ?: MoshiNull

    override fun Long?.asJsonValue(): MoshiNode = this?.let(::MoshiNumber) ?: MoshiNull

    override fun BigDecimal?.asJsonValue(): MoshiNode = this?.let(::MoshiNumber) ?: MoshiNull

    override fun BigInteger?.asJsonValue(): MoshiNode = this?.let(::MoshiNumber) ?: MoshiNull

    override fun Boolean?.asJsonValue(): MoshiNode = this?.let(::MoshiBoolean) ?: MoshiNull

    override fun <T : Iterable<MoshiNode>> T.asJsonArray(): MoshiNode = MoshiArray(toList())

    override fun <LIST : Iterable<Pair<String, MoshiNode>>> LIST.asJsonObject(): MoshiNode = MoshiObject(toMap())

    override fun typeOf(value: MoshiNode) = when (value) {
        is MoshiString -> JsonType.String
        is MoshiBoolean -> JsonType.Boolean
        is MoshiNumber -> JsonType.Number
        is MoshiArray -> JsonType.Array
        is MoshiObject -> JsonType.Object
        is MoshiNull -> JsonType.Null
    }

    override fun fields(node: MoshiNode): Iterable<Pair<String, MoshiNode>> = when (node) {
        is MoshiObject -> node.fields.toList()
        else -> emptyList()
    }

    override fun elements(value: MoshiNode): Iterable<MoshiNode> = when (value) {
        is MoshiObject -> value.fields.map { it.value }.toList()
        is MoshiArray -> value.elements
        else -> emptyList()
    }

    override fun text(value: MoshiNode) = when (value) {
        is MoshiString -> value.value
        else -> error("not a string node")
    }

    override fun bool(value: MoshiNode) = when (value) {
        is MoshiBoolean -> value.value
        else -> error("not a boolean node")
    }

    override fun integer(value: MoshiNode) = when (value) {
        is MoshiNumber -> value.value.toLong()
        else -> error("not a number node")
    }

    override fun decimal(value: MoshiNode) = when (value) {
        is MoshiNumber -> BigDecimal(value.value.toDouble())
        else -> error("not a number node")
    }

    override fun textValueOf(node: MoshiNode, name: String) = when (node) {
        is MoshiObject -> node[name]?.let { text(it) }
        else -> null
    }

    override fun <T : Any> asA(j: MoshiNode, target: KClass<T>) = asA(asFormatString(j), target)
}

fun Moshi.Builder.asConfigurable() = object : AutoMappingConfiguration<Moshi.Builder> {
    override fun <OUT> int(mapping: BiDiMapping<Int, OUT>) = adapter(mapping, { value(it) }, { nextInt() })
    override fun <OUT> long(mapping: BiDiMapping<Long, OUT>) =
        adapter(mapping, { value(it) }, { nextLong() })

    override fun <OUT> double(mapping: BiDiMapping<Double, OUT>) =
        adapter(mapping, { value(it) }, { nextDouble() })

    override fun <OUT> bigInteger(mapping: BiDiMapping<BigInteger, OUT>) =
        adapter(mapping, { value(it) }, { nextLong().toBigInteger() })

    override fun <OUT> bigDecimal(mapping: BiDiMapping<BigDecimal, OUT>) =
        adapter(mapping, { value(it) }, { nextDouble().toBigDecimal() })

    override fun <OUT> boolean(mapping: BiDiMapping<Boolean, OUT>) =
        adapter(mapping, { value(it) }, { nextBoolean() })

    override fun <OUT> text(mapping: BiDiMapping<String, OUT>) =
        adapter(mapping, { value(it) }, { nextString() })

    private fun <IN, OUT> adapter(
        mapping: BiDiMapping<IN, OUT>,
        write: JsonWriter.(IN) -> Unit,
        read: JsonReader.() -> IN
    ) =
        apply {
            add(mapping.clazz, object : JsonAdapter<OUT>() {
                override fun fromJson(reader: JsonReader) = mapping.invoke(reader.read())

                override fun toJson(writer: JsonWriter, value: OUT?) {
                    value?.let { writer.write(mapping(it)) } ?: writer.nullValue()
                }
            }.nullSafe())
        }

    // add the Kotlin adapter last, as it will hijack our custom mappings otherwise
    override fun done() =
        this@asConfigurable
            .add(EventAdapter)
            .add(ThrowableAdapter)
            .add(CollectionEdgeCasesAdapter)
            .add(MoshiNodeAdapter)
            .add(Unit::class.java, UnitAdapter)
            .addLast(KotlinJsonAdapterFactory())
}

private object UnitAdapter : JsonAdapter<Unit>() {
    override fun fromJson(reader: JsonReader) {
        reader.readJsonValue(); Unit
    }

    override fun toJson(writer: JsonWriter, value: Unit?) {
        value?.let { writer.beginObject().endObject() } ?: writer.nullValue()
    }
}
