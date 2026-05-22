package net.avianlabs.apollo.sse

import com.apollographql.apollo.api.Adapter
import com.apollographql.apollo.api.CompiledField
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.ObjectType
import com.apollographql.apollo.api.Subscription
import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.api.json.JsonWriter

internal class TestSubscription : Subscription<TestSubscription.Data> {

  data class Data(val test: String?) : Subscription.Data

  override fun id(): String = "TestSubscription"
  override fun document(): String = "subscription { test }"
  override fun name(): String = "TestSubscription"

  override fun serializeVariables(
    writer: JsonWriter,
    customScalarAdapters: CustomScalarAdapters,
    withDefaultValues: Boolean,
  ) { /* No variables */ }

  override fun adapter(): Adapter<Data> = object : Adapter<Data> {
    override fun fromJson(reader: JsonReader, customScalarAdapters: CustomScalarAdapters): Data {
      var test: String? = null
      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "test" -> test = reader.nextString()
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      return Data(test = test)
    }

    override fun toJson(writer: JsonWriter, customScalarAdapters: CustomScalarAdapters, value: Data) {
      writer.beginObject()
      if (value.test != null) writer.name("test").value(value.test) else writer.name("test").nullValue()
      writer.endObject()
    }
  }

  override fun rootField(): CompiledField {
    return CompiledField.Builder("data", ObjectType.Builder("Data").build())
      .build()
  }
}
