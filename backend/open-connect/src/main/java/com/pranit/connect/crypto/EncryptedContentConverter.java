package com.pranit.connect.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedContentConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(final String attribute) {
        return MessageCryptoService.getInstance().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(final String dbData) {
        return MessageCryptoService.getInstance().decrypt(dbData);
    }
}
