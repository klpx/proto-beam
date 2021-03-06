package io.anemos.protobeam.convert;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.protobuf.Descriptors;
import io.anemos.Meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SchemaProtoToBigQueryModel {

    public TableSchema getSchema(Descriptors.Descriptor descriptor) {
        List<TableFieldSchema> fieldSchemas = convertSchema(descriptor);
        return new TableSchema().setFields(fieldSchemas);
    }

    private List<TableFieldSchema> convertSchema(Descriptors.Descriptor descriptor) {
        List<TableFieldSchema> fieldSchemas = new ArrayList<>();
        for (Descriptors.FieldDescriptor fieldDescriptor : descriptor.getFields()) {
            fieldSchemas.add(convertField(fieldDescriptor));
        }
        return fieldSchemas;
    }

    private TableFieldSchema convertField(Descriptors.FieldDescriptor fieldDescriptor) {
//        if (isNested(fieldDescriptor))
//            return getNestedSchema(fieldDescriptor);
        String bigQueryType = extractFieldType(fieldDescriptor);
        TableFieldSchema fieldSchema =
                new TableFieldSchema()
                        .setName(fieldDescriptor.getName())
                        .setType(bigQueryType);
        if (fieldDescriptor.isRepeated())
            fieldSchema.setMode("REPEATED");
        if ("STRUCT".equals(bigQueryType)) {
            fieldSchema.setFields(convertSchema(fieldDescriptor.getMessageType()));
        }
        // OK, this could be better...
        Map<Descriptors.FieldDescriptor, Object> allFields = fieldDescriptor.getOptions().getAllFields();
        if (allFields.size() > 0) {
            allFields.forEach((f, opt) -> {
                switch (f.getFullName()) {
                    case "google.protobuf.FieldOptions.deprecated":
                        Boolean deprecated = (Boolean) opt;
                        if (deprecated) {
                            String description = fieldSchema.getDescription();
                            if ((description == null) || description.length() == 0) {
                                description = "";
                            }
                            fieldSchema.setDescription("@deprecated\n" + description);
                        }
                        break;
                    case "io.anemos.field_meta":
                        Meta.FieldMeta meta = (Meta.FieldMeta) opt;
                        String description = fieldSchema.getDescription();
                        if ((description == null) || description.length() == 0) {
                            description = "";
                        }
                        fieldSchema.setDescription(description + meta.getDescription());
                        break;
                }
            });
        }

        //Meta.FieldMeta

        return fieldSchema;
    }

    private String extractFieldType(Descriptors.FieldDescriptor fieldDescriptor) {
        if (isTimestamp(fieldDescriptor))
            return "TIMESTAMP";
        if (isDecimal(fieldDescriptor))
            return "FLOAT64";
        Descriptors.FieldDescriptor.Type fieldType = fieldDescriptor.getType();
        switch (fieldType) {
            case DOUBLE:
            case FLOAT:
                return "FLOAT64";
            case INT64:
            case UINT64:
            case INT32:
            case FIXED64:
            case FIXED32:
            case UINT32:
            case SFIXED32:
            case SFIXED64:
            case SINT32:
            case SINT64:
                return "INT64";
            case BOOL:
                return "BOOL";
            case STRING:
                return "STRING";
            case BYTES:
                return "BYTES";
            case ENUM:
                return "STRING";
            case MESSAGE:
                return "STRUCT";
        }
        throw new RuntimeException("Field type not matched.");
    }

    private boolean isTimestamp(Descriptors.FieldDescriptor fieldDescriptor) {
        return (".google.protobuf.Timestamp".equals(fieldDescriptor.toProto().getTypeName()));
    }

    private boolean isDecimal(Descriptors.FieldDescriptor fieldDescriptor) {
        return ".bcl.Decimal".equals(fieldDescriptor.toProto().getTypeName());
    }

    private TableFieldSchema getNestedSchema(Descriptors.FieldDescriptor fieldDescriptor) {
//        if (isCampaignCategory(fieldDescriptor))
//            return getCampaignCategorySchema();
//        else if (isArticleReservation(fieldDescriptor))
//            return getArticleReservationSchema();
        throw new RuntimeException("No nested schema found for field " + fieldDescriptor.getName());
    }
}
