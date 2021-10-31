package org.http4k.format

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonReader.Token.BEGIN_ARRAY
import com.squareup.moshi.JsonReader.Token.BEGIN_OBJECT
import com.squareup.moshi.JsonReader.Token.BOOLEAN
import com.squareup.moshi.JsonReader.Token.END_ARRAY
import com.squareup.moshi.JsonReader.Token.END_OBJECT
import com.squareup.moshi.JsonReader.Token.NAME
import com.squareup.moshi.JsonReader.Token.NULL
import com.squareup.moshi.JsonReader.Token.NUMBER
import com.squareup.moshi.JsonReader.Token.STRING
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

sealed class MoshiNode {
    data class MoshiObject(val fields: Map<String, MoshiNode>) : MoshiNode(), Map<String, MoshiNode> by fields
    data class MoshiArray(val elements: List<MoshiNode>) : MoshiNode()
    data class MoshiString(val value: String) : MoshiNode()
    data class MoshiBoolean(val value: Boolean) : MoshiNode()
    data class MoshiNumber(val value: Number) : MoshiNode()
    object MoshiNull : MoshiNode()
}

object MoshiNodeAdapter : JsonAdapter.Factory, JsonAdapter<MoshiNode>() {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi) =
        takeIf { MoshiNode::class.java.isAssignableFrom(Types.getRawType(type)) }

    override fun toJson(writer: JsonWriter, `value`: MoshiNode?) {
        with(writer) {
            when (`value`) {
                null -> nullValue()
                is MoshiNode.MoshiObject -> {
                    beginObject()
                    `value`.fields.forEach {
                        name(it.key)
                        toJson(this, it.value)
                    }
                    endObject()
                }
                is MoshiNode.MoshiArray -> {
                    beginArray()
                    `value`.elements.forEach { toJson(this, it) }
                    endArray()
                }
                is MoshiNode.MoshiBoolean -> value(`value`.value)
                is MoshiNode.MoshiNull -> nullValue()
                is MoshiNode.MoshiNumber -> value(`value`.value)
                is MoshiNode.MoshiString -> value(`value`.value)
            }
        }
    }

    @FromJson
    override fun fromJson(reader: JsonReader): MoshiNode = with(reader) {
        when (peek()) {
            NULL -> MoshiNode.MoshiNull
            BEGIN_ARRAY -> {
                beginArray()
                val list = mutableListOf<MoshiNode>()
                while (reader.peek() != END_ARRAY) list += fromJson(this)
                endArray()
                MoshiNode.MoshiArray(list)
            }
            BEGIN_OBJECT -> {
                beginObject()
                val map = mutableMapOf<String, MoshiNode>()
                while (reader.peek() != END_OBJECT) {
                    if (reader.peek() != NAME) throw JsonDataException("expected name at $path")
                    map[reader.nextName()] = fromJson(this)
                }
                endObject()
                MoshiNode.MoshiObject(map)
            }
            BOOLEAN -> MoshiNode.MoshiBoolean(nextBoolean())
            NUMBER -> try {
                peekJson().nextLong()
                MoshiNode.MoshiNumber(nextLong())
            } catch (e: JsonDataException) {
                MoshiNode.MoshiNumber(nextDouble())
            }
            STRING -> MoshiNode.MoshiString(nextString())
            else -> throw JsonDataException("illegal value at $path")
        }
    }
}
