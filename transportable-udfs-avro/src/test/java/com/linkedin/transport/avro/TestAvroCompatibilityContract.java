/**
 * Copyright 2026 LinkedIn Corporation. All rights reserved.
 * Licensed under the BSD-2 Clause license.
 * See LICENSE in the project root for license information.
 */
package com.linkedin.transport.avro;

import com.linkedin.transport.api.StdFactory;
import com.linkedin.transport.api.data.PlatformData;
import com.linkedin.transport.api.data.StdArray;
import com.linkedin.transport.api.data.StdData;
import com.linkedin.transport.api.data.StdMap;
import com.linkedin.transport.api.data.StdStruct;
import com.linkedin.transport.api.types.StdType;
import com.linkedin.transport.avro.typesystem.AvroBoundVariables;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.generic.GenericData;
import org.apache.avro.util.Utf8;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


public class TestAvroCompatibilityContract {
  private static final String GOLDEN_RESOURCE =
      "/com/linkedin/transport/avro/avro-1.10.2-compatibility-contract.txt";

  @Test
  public void testAvroCompatibilityContract() throws IOException {
    assertEquals(buildContract(), readGoldenContract());
  }

  private static String buildContract() {
    StdFactory factory = new AvroFactory(new AvroBoundVariables());
    StdType stringType = AvroWrapper.createStdType(Schema.create(Schema.Type.STRING));
    StdType bytesType = AvroWrapper.createStdType(Schema.create(Schema.Type.BYTES));
    StdType booleanType = AvroWrapper.createStdType(Schema.create(Schema.Type.BOOLEAN));
    StdType longArrayType =
        AvroWrapper.createStdType(Schema.createArray(Schema.create(Schema.Type.LONG)));
    StdType stringMapType =
        AvroWrapper.createStdType(Schema.createMap(Schema.create(Schema.Type.STRING)));

    StdStruct struct = factory.createStruct(
        Arrays.asList("name", "payload", "enabled", "scores", "attributes"),
        Arrays.asList(stringType, bytesType, booleanType, longArrayType, stringMapType));
    struct.setField("name", factory.createString("member"));
    struct.setField("payload", factory.createBinary(ByteBuffer.wrap(new byte[] {0, 1, 127, -1})));
    struct.setField("enabled", factory.createBoolean(true));

    StdArray scores = factory.createArray(longArrayType);
    scores.add(factory.createLong(7L));
    scores.add(factory.createLong(9L));
    struct.setField("scores", scores);

    StdMap attributes = factory.createMap(stringMapType);
    attributes.put(factory.createString("beta"), factory.createString("two"));
    attributes.put(factory.createString("alpha"), factory.createString("one"));
    struct.setField("attributes", attributes);

    Schema structSchema = ((org.apache.avro.generic.GenericRecord)
        ((PlatformData) struct).getUnderlyingData()).getSchema();
    Schema enumSchema = Schema.createEnum(
        "Status", null, "com.linkedin.transport.avro.contract", Arrays.asList("ACTIVE", "INACTIVE"));
    Schema nullableLong = Schema.createUnion(
        Arrays.asList(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.LONG)));
    Schema nullableLongReversed = Schema.createUnion(
        Arrays.asList(Schema.create(Schema.Type.LONG), Schema.create(Schema.Type.NULL)));

    StringBuilder contract = new StringBuilder();
    append(contract, "schema.canonical", SchemaNormalization.toParsingForm(structSchema));
    append(contract, "schema.fingerprint64",
        Long.toUnsignedString(SchemaNormalization.parsingFingerprint64(structSchema)));
    append(contract, "enum.schema.canonical", SchemaNormalization.toParsingForm(enumSchema));
    append(contract, "enum.schema.fingerprint64",
        Long.toUnsignedString(SchemaNormalization.parsingFingerprint64(enumSchema)));
    append(contract, "struct.name", stringValue(struct.getField("name")));
    append(contract, "struct.payload", bytesValue(struct.getField("payload")));
    append(contract, "struct.enabled", booleanValue(struct.getField("enabled")));
    append(contract, "struct.scores", arrayValue((StdArray) struct.getField("scores")));
    append(contract, "struct.attributes", mapValue((StdMap) struct.getField("attributes")));
    append(contract, "string.java",
        stringValue(AvroWrapper.createStdData("member", Schema.create(Schema.Type.STRING))));
    append(contract, "string.utf8",
        stringValue(AvroWrapper.createStdData(new Utf8("member"), Schema.create(Schema.Type.STRING))));
    append(contract, "enum.java",
        stringValue(AvroWrapper.createStdData("ACTIVE", enumSchema)));
    append(contract, "enum.symbol",
        stringValue(AvroWrapper.createStdData(new GenericData.EnumSymbol(enumSchema, "ACTIVE"), enumSchema)));
    append(contract, "union.value", longValue(AvroWrapper.createStdData(42L, nullableLong)));
    append(contract, "union.null",
        String.valueOf(AvroWrapper.createStdData(null, nullableLong)));
    append(contract, "union.reversed.value",
        longValue(AvroWrapper.createStdData(42L, nullableLongReversed)));
    append(contract, "union.reversed.null",
        String.valueOf(AvroWrapper.createStdData(null, nullableLongReversed)));
    return contract.toString();
  }

  private static String readGoldenContract() throws IOException {
    InputStream input = TestAvroCompatibilityContract.class.getResourceAsStream(GOLDEN_RESOURCE);
    assertNotNull(input, "Missing compatibility contract: " + GOLDEN_RESOURCE);
    try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int count;
      while ((count = stream.read(buffer)) != -1) {
        output.write(buffer, 0, count);
      }
      String content = new String(output.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
      int contractStart = content.indexOf("schema.canonical=");
      assertTrue(contractStart >= 0, "Missing compatibility contract content");
      return content.substring(contractStart);
    }
  }

  private static void append(StringBuilder contract, String key, String value) {
    contract.append(key).append('=').append(value).append('\n');
  }

  private static String stringValue(StdData data) {
    return ((PlatformData) data).getUnderlyingData().toString();
  }

  private static String longValue(StdData data) {
    return String.valueOf(((PlatformData) data).getUnderlyingData());
  }

  private static String booleanValue(StdData data) {
    return String.valueOf(((PlatformData) data).getUnderlyingData());
  }

  private static String bytesValue(StdData data) {
    ByteBuffer bytes = ((ByteBuffer) ((PlatformData) data).getUnderlyingData()).duplicate();
    StringBuilder value = new StringBuilder();
    while (bytes.hasRemaining()) {
      value.append(String.format("%02x", bytes.get() & 0xff));
    }
    return value.toString();
  }

  private static String arrayValue(StdArray array) {
    List<String> values = new ArrayList<>();
    for (StdData value : array) {
      values.add(String.valueOf(((PlatformData) value).getUnderlyingData()));
    }
    return values.toString();
  }

  private static String mapValue(StdMap map) {
    List<String> entries = new ArrayList<>();
    for (StdData key : map.keySet()) {
      entries.add(stringValue(key) + "=" + stringValue(map.get(key)));
    }
    Collections.sort(entries);
    return entries.toString();
  }
}
