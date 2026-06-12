package de.flubba;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentStoreTest {

    @TempDir
    Path tempDir;

    private DocumentStore store;
    private final HTMLEditorKit kit = new HTMLEditorKit();

    @BeforeEach
    void setUp() {
        store = new DocumentStore(tempDir.resolve("autosave").resolve("lastDocument.html"));
    }

    private Document docWithText(String text) throws Exception {
        Document doc = kit.createDefaultDocument();
        doc.insertString(0, text, null);
        return doc;
    }

    private static String textOf(Document doc) throws Exception {
        return doc.getText(0, doc.getLength()).strip();
    }

    @Test
    void saveThenLoad_roundTripsContent() throws Exception {
        File file = tempDir.resolve("doc.html").toFile();
        store.save(docWithText("hello receipt"), file);

        Document loaded = kit.createDefaultDocument();
        store.load(file, loaded);

        assertThat(textOf(loaded)).isEqualTo("hello receipt");
    }

    @Test
    void load_replacesExistingContent() throws Exception {
        File file = tempDir.resolve("doc.html").toFile();
        store.save(docWithText("new content"), file);

        Document target = docWithText("old content");
        store.load(file, target);

        assertThat(textOf(target)).isEqualTo("new content").doesNotContain("old");
    }

    @Test
    void load_missingFile_throws() {
        Document doc = kit.createDefaultDocument();
        assertThatThrownBy(() -> store.load(tempDir.resolve("nope.html").toFile(), doc))
                .isInstanceOf(IOException.class);
    }

    @Test
    void autoSave_createsDirectoryAndFile() throws Exception {
        store.autoSave(docWithText("draft"));
        assertThat(tempDir.resolve("autosave").resolve("lastDocument.html")).exists();
    }

    @Test
    void autoSave_directoryCreationFails_throwsAndWritesNothing() throws Exception {
        // occupy the parent path with a file so createDirectories must fail
        Files.createFile(tempDir.resolve("autosave"));
        assertThatThrownBy(() -> store.autoSave(docWithText("draft")))
                .isInstanceOf(IOException.class);
    }

    @Test
    void restoreAutoSaved_roundTrip() throws Exception {
        store.autoSave(docWithText("remember me"));

        Document restored = kit.createDefaultDocument();
        assertThat(store.restoreAutoSaved(restored)).isTrue();
        assertThat(textOf(restored)).isEqualTo("remember me");
    }

    @Test
    void restoreAutoSaved_nothingSaved_returnsFalse() throws Exception {
        Document doc = docWithText("untouched");
        assertThat(store.restoreAutoSaved(doc)).isFalse();
        assertThat(textOf(doc)).isEqualTo("untouched");
    }
}
