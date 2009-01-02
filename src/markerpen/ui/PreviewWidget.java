/*
 * PreviewWidget.java
 *
 * Copyright (c) 2008 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the program it is a part of, are made available
 * to you by its authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */
package markerpen.ui;

import org.freedesktop.cairo.Context;
import org.freedesktop.cairo.Matrix;
import org.gnome.gdk.EventExpose;
import org.gnome.gtk.Allocation;
import org.gnome.gtk.EventBox;
import org.gnome.gtk.PaperSize;
import org.gnome.gtk.Unit;
import org.gnome.gtk.Widget;
import org.gnome.pango.FontDescription;
import org.gnome.pango.Layout;
import org.gnome.pango.LayoutLine;

/*
 * Work in "points", which makes sense since the target back end is PDF.
 */
class PreviewWidget extends EventBox
{
    private int pixelWidth;

    private int pixelHeight;

    private double pageWidth;

    private double pageHeight;

    private double topMargin;

    private double leftMargin;

    private double rightMargin;

    private double scaleFactor;

    // Temporary
    private final String text;

    PreviewWidget(final String text) {
        super();

        this.text = text;

        this.connect(new Widget.ExposeEvent() {
            public boolean onExposeEvent(Widget source, EventExpose event) {
                final Context cr;

                cr = new Context(source.getWindow());

                processSize(cr);
                scaleOutput(cr);
                drawPageOutline(cr);
                drawText(cr);

                return true;
            }
        });
    }

    private void processSize(Context cr) {
        final Allocation rect;
        final PaperSize paper;

        rect = this.getAllocation();

        pixelWidth = rect.getWidth();
        pixelHeight = rect.getHeight();

        paper = PaperSize.A4;
        pageWidth = paper.getWidth(Unit.POINTS);
        pageHeight = paper.getHeight(Unit.POINTS);

        topMargin = 25;
        leftMargin = 45;
        rightMargin = 20;
    }

    private void drawPageOutline(Context cr) {
        final double shadow = 3.0;

        cr.rectangle(shadow, shadow, pageWidth, pageHeight);
        cr.setSourceRGB(0.1, 0.1, 0.1);
        cr.fill();

        cr.rectangle(0, 0, pageWidth, pageHeight);
        cr.setSourceRGB(1.0, 1.0, 1.0);
        cr.fillPreserve();
        cr.setSourceRGB(0.0, 0.0, 0.0);
        cr.setLineWidth(0.5);
        cr.stroke();
    }

    public void drawText(Context cr) {
        final Layout layout;
        final FontDescription desc;
        final String[] paras;
        double y, b, v = 0;

        cr.moveTo(leftMargin, topMargin);

        layout = new Layout(cr);
        desc = new FontDescription("Liberation Serif, 10");
        layout.setFontDescription(desc);

        paras = text.split("\n");

        layout.setWidth(pageWidth - (leftMargin + rightMargin));

        cr.setSourceRGB(0.0, 0.0, 0.0);

        cr.updateLayout(layout);
        b = layout.getBaseline();
        y = topMargin + b;
        for (String para : paras) {
            layout.setText(para);

            for (LayoutLine line : layout.getLinesReadonly()) {
                v = line.getExtentsLogical().getHeight();
                System.err.println(v); // DEBUG

                y += v;

                if (y > pageHeight) {
                    return;
                }
                cr.moveTo(leftMargin, y);
                cr.showLayout(line);
            }

            y += v; // blank line between paras
        }
    }

    private void scaleOutput(Context cr) {
        final Matrix matrix;
        final double scaleWidth, scaleHeight;

        scaleWidth = pixelWidth / (pageWidth + 10.0);
        scaleHeight = pixelHeight / (pageHeight + 10.0);

        if (scaleWidth > scaleHeight) {
            scaleFactor = scaleHeight;
        } else {
            scaleFactor = scaleWidth;
        }

        matrix = new Matrix();
        matrix.scale(scaleFactor, scaleFactor);

        /*
         * Bump the image off of the top left corner.
         */
        matrix.translate(0.5, 1.5);
        cr.transform(matrix);
    }
}
