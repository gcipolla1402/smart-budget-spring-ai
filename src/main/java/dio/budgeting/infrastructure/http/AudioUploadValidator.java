package dio.budgeting.infrastructure.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@Component
public class AudioUploadValidator {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "flac", "mp3", "mp4", "mpeg", "mpga", "m4a", "ogg", "wav", "webm");
    private static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of(
            "audio/flac", "audio/x-flac", "audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a",
            "audio/ogg", "application/ogg", "audio/wav", "audio/x-wav", "audio/webm", "video/webm",
            "video/mp4", "application/octet-stream");

    private final long maxSizeInBytes;

    public AudioUploadValidator(@Value("${app.ai.audio.max-size:25MB}") DataSize maxSize) {
        this.maxSizeInBytes = maxSize.toBytes();
    }

    public void validate(MultipartFile file) {
        if (file == null) {
            throw new InvalidAudioUploadException("Required multipart file 'file' is missing");
        }
        if (file.isEmpty()) {
            throw new InvalidAudioUploadException("Audio file must not be empty");
        }
        if (file.getSize() > maxSizeInBytes) {
            throw new AudioUploadTooLargeException(
                    "Audio file exceeds the maximum allowed size of %d bytes".formatted(maxSizeInBytes));
        }

        var extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null || !SUPPORTED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new UnsupportedAudioFormatException("Unsupported audio file extension");
        }

        var contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)
                || !SUPPORTED_MEDIA_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new UnsupportedAudioFormatException("Unsupported audio media type");
        }
    }
}
