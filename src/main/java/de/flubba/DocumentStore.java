package de.flubba;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists HTML documents: explicit load/save plus an auto-save slot at a fixed
 * path. Failures propagate as IOException; callers decide how to report them.
 */
public final class DocumentStore {

    private final HTMLEditorKit kit = new HTMLEditorKit();
    private final Path autoSavePath;

    public DocumentStore(Path autoSavePath) {
        this.autoSavePath = autoSavePath;
    }

    public void save(Document document, File file) throws IOException {
        try (var fos = new FileOutputStream(file)) {
            kit.write(fos, document, 0, document.getLength());
        } catch (BadLocationException e) {
            throw new IOException("Failed to write document", e);
        }
    }

    /** Replaces the content of {@code into} with the file's content. */
    public void load(File file, Document into) throws IOException {
        try (var fis = new FileInputStream(file)) {
            into.remove(0, into.getLength());
            kit.read(fis, into, 0);
        } catch (BadLocationException e) {
            throw new IOException("Failed to read document", e);
        }
    }

    public void autoSave(Document document) throws IOException {
        Files.createDirectories(autoSavePath.getParent());
        save(document, autoSavePath.toFile());
    }

    /** @return true if an auto-saved document existed and was restored */
    public boolean restoreAutoSaved(Document into) throws IOException {
        if (!Files.exists(autoSavePath)) {
            return false;
        }
        load(autoSavePath.toFile(), into);
        return true;
    }
}
