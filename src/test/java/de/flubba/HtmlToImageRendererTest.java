package de.flubba;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlToImageRendererTest {

    @Test
    void render_fromWorkerThread_producesImage() {
        BufferedImage img = HtmlToImageRenderer.render("<h1>Hello</h1>", 512);
        assertThat(img).isNotNull();
        assertThat(img.getWidth()).isEqualTo(512);
        assertThat(img.getHeight()).isGreaterThan(0);
    }

    @Test
    void render_fromEdt_producesImage() throws Exception {
        var result = new AtomicReference<BufferedImage>();
        SwingUtilities.invokeAndWait(() -> result.set(HtmlToImageRenderer.render("<p>on edt</p>", 512)));
        assertThat(result.get()).isNotNull();
        assertThat(result.get().getWidth()).isEqualTo(512);
    }

    @Test
    void render_emptyHtml_returnsNull() {
        assertThat(HtmlToImageRenderer.render("", 512)).isNull();
        assertThat(HtmlToImageRenderer.render((String) null, 512)).isNull();
    }
}
