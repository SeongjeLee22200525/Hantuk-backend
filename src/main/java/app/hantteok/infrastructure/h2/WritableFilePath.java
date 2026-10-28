package app.hantteok.infrastructure.h2;

import org.h2.store.fs.FilePathWrapper;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Opt-in local filesystem adapter for Windows restricted tokens where Files.isWritable reports
 * false although the OS permits opening for writing. The fallback probes an existing file without
 * creating or changing its bytes; it does not grant permissions or bypass a failed native file
 * open.
 */
public final class WritableFilePath extends FilePathWrapper {
    @Override
    public String getScheme() {
        return "writeprobe";
    }

    @Override
    public boolean canWrite() {
        if (super.canWrite()) {
            return true;
        }
        Path path = Path.of(getBase().toString());
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try (FileChannel ignored = FileChannel.open(path, StandardOpenOption.WRITE)) {
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }
}
