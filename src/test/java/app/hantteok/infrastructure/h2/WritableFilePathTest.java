package app.hantteok.infrastructure.h2;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.h2.store.fs.FilePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WritableFilePathTest {
    @TempDir Path directory;
    @Test void probeDoesNotModifyExistingDataOrCreateMissingFiles() throws Exception {
        Path existing=directory.resolve("sample.db");
        Files.writeString(existing,"existing data");
        FilePath.register(new WritableFilePath());
        var path=FilePath.get("writeprobe:"+existing);
        assertThat(path.canWrite()).isTrue();
        assertThat(Files.readString(existing)).isEqualTo("existing data");
        var missing=FilePath.get("writeprobe:"+directory.resolve("missing.db"));
        assertThat(missing.canWrite()).isFalse();
        assertThat(Files.exists(directory.resolve("missing.db"))).isFalse();
    }
}
