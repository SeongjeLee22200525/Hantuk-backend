package app.hantteok.service;

import static app.hantteok.common.validation.RequestValues.text;
import static app.hantteok.repository.DatabaseRepository.map;

import app.hantteok.common.error.ApiException;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/** Validates the single optional photo attached to a private thank-you message. */
@Service
public class ThanksPhotoService {
    private static final int MAX_PHOTO_BYTES = 5 * 1024 * 1024;
    private static final int MAX_BASE64_LENGTH = 7 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    Map<String, Object> validatePhoto(Map<String, Object> photo) {
        String name = text(photo, "name", 255, true);
        String encoded = text(photo, "base64", MAX_BASE64_LENGTH, true);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw ApiException.bad("올바른 사진을 첨부해 주세요.");
        }
        if (bytes.length > MAX_PHOTO_BYTES || !supportedImage(bytes)) {
            throw ApiException.bad("JPG·PNG·WebP 사진 1장(최대 5MB)을 선택해 주세요.");
        }
        return map("name", name, "base64", Base64.getEncoder().encodeToString(bytes));
    }

    private boolean supportedImage(byte[] bytes) {
        boolean png =
                bytes.length >= PNG_SIGNATURE.length
                        && Arrays.equals(Arrays.copyOf(bytes, PNG_SIGNATURE.length), PNG_SIGNATURE);
        boolean jpeg =
                bytes.length >= 3
                        && bytes[0] == (byte) 255
                        && bytes[1] == (byte) 216
                        && bytes[2] == (byte) 255;
        boolean webp =
                bytes.length >= 12
                        && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                        && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
        return png || jpeg || webp;
    }
}
