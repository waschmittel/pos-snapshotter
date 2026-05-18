package de.flubba;

import javax.swing.text.DefaultCaret;
import java.awt.event.FocusEvent;

public class PersistentCaret extends DefaultCaret {
    @Override
    public void focusLost(FocusEvent e) {
        setVisible(false);
        setSelectionVisible(true);
    }

    @Override
    public void setSelectionVisible(boolean vis) {
        // Always keep selection visible
        super.setSelectionVisible(true);
    }
}
