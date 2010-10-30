/*
 * Quill and Parchment, a WYSIWYN document editor and rendering engine. 
 *
 * Copyright © 2010 Operational Dynamics Consulting, Pty Ltd
 *
 * The code in this file, and the program it is a part of, is made available
 * to you by its authors as open source software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License version
 * 2 ("GPL") as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GPL for more details.
 *
 * You should have received a copy of the GPL along with this program. If not,
 * see http://www.gnu.org/licenses/. The authors of this program may be
 * contacted through http://research.operationaldynamics.com/projects/quill/.
 */
package quill.ui;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParsingException;
import nu.xom.ValidityException;

import org.freedesktop.enchant.Enchant;
import org.gnome.gtk.CellRendererText;
import org.gnome.gtk.DataColumn;
import org.gnome.gtk.DataColumnString;
import org.gnome.gtk.Entry;
import org.gnome.gtk.ListStore;
import org.gnome.gtk.SelectionMode;
import org.gnome.gtk.TreeIter;
import org.gnome.gtk.TreeModelFilter;
import org.gnome.gtk.TreeModelSort;
import org.gnome.gtk.TreePath;
import org.gnome.gtk.TreeSelection;
import org.gnome.gtk.TreeView;
import org.gnome.gtk.TreeViewColumn;
import org.gnome.gtk.VBox;
import org.gnome.gtk.Window;

import static org.freedesktop.bindings.Internationalization.translateCountryName;
import static org.freedesktop.bindings.Internationalization.translateLanguageName;

/**
 * Popup a window to allow the user to select which (of the installed)
 * dictionaries they wish the document language to be.
 * 
 * @author Andrew Cowie
 */
class DictionarySelectionWindow extends Window
{
    private final VBox top;

    private Entry search;

    private TreeView view;

    private ListStore store;

    private TreeModelFilter filtered;

    private TreeModelSort sorted;

    private DataColumnString tagColumn;

    private DataColumnString displayColumn;

    DictionarySelectionWindow() {
        super();

        top = new VBox(false, 0);
        super.add(top);

        buildModel();
        setupSearch();
        setupView();

        populateModel();

        hookupSelectionSignals();
    }

    private void buildModel() {
        tagColumn = new DataColumnString();
        displayColumn = new DataColumnString();

        store = new ListStore(new DataColumn[] {
                tagColumn, displayColumn
        });
        filtered = new TreeModelFilter(store, null);
        sorted = new TreeModelSort(filtered);
    }

    private void setupSearch() {
        search = new Entry();

        top.packStart(search, false, false, 0);
    }

    private void setupView() {
        final TreeViewColumn vertical;
        final CellRendererText renderer;

        view = new TreeView(sorted);
        view.setRulesHint(false);
        view.setEnableSearch(false);
        view.setReorderable(false);
        view.setHeadersVisible(false);

        vertical = view.appendColumn();
        vertical.setReorderable(false);
        vertical.setResizable(false);
        vertical.setSortColumn(displayColumn);
        vertical.emitClicked();

        renderer = new CellRendererText(vertical);
        renderer.setText(displayColumn);

        top.packStart(view, false, false, 0);
    }

    private void populateModel() {
        final String[] list;
        int i;
        TreeIter row;
        TagToTranslationTable table;
        String code, languageCode, languageName, countryCode, countryName, displayName;

        table = new TagToTranslationTable();

        list = Enchant.listDictionaries();

        for (i = 0; i < list.length; i++) {
            code = list[i];

            /*
             * Parse [sic] the language tags to pull out ISO 639 language and
             * ISO 3166 country codes.
             */

            if (code.length() == 2) {
                languageCode = code;
                countryCode = null;
            } else if (code.length() == 5) {
                languageCode = code.substring(0, 2);
                countryCode = code.substring(3, 5);
            } else {
                throw new AssertionError(
                        "There's nothing wrong with an Enchant lang_tag being longer than fr_CA, but how do we handle it?");
            }

            languageName = table.getName(languageCode);

            if (languageName == null) {
                continue; // huh, but ok
            }

            countryName = table.getCountryName(countryCode);
            if (countryName == null) {
                displayName = translateLanguageName(languageName);
            } else {
                displayName = translateLanguageName(languageName) + " ("
                        + translateCountryName(countryName) + ")";
            }

            row = store.appendRow();
            store.setValue(row, tagColumn, languageCode);
            store.setValue(row, displayColumn, displayName);
        }
    }

    private void hookupSelectionSignals() {
        final TreeSelection selection;

        selection = view.getSelection();
        selection.setMode(SelectionMode.SINGLE);

        view.connect(new TreeView.RowActivated() {
            public void onRowActivated(TreeView source, TreePath path, TreeViewColumn vertical) {
                final TreeIter row;

                row = sorted.getIter(path);
                row.getClass();
                // TODO
            }
        });
    }
}

/**
 * Build a table of language code to standardized ("official") language names
 * and country code to country names.
 * 
 * <p>
 * The existence of the class is a shame; ideally we could do this work inside
 * java-gnome seeing as how the only reason to look up the ISO names is to use
 * them as <code>msgid</code>s in translations.
 * 
 * @author Andrew Cowie
 */
class TagToTranslationTable
{
    Map<String, String> languages;

    Map<String, String> countries;

    TagToTranslationTable() {
        // 185 languages
        languages = new HashMap<String, String>(190, 1.0f);

        // 246 countries
        countries = new HashMap<String, String>(255, 1.0f);

        try {
            loadLanguageNames();
            loadCountryNames();
        } catch (Exception e) {
            /*
             * Handle the file not being present...
             */
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void loadLanguageNames() throws ValidityException, ParsingException, IOException {
        final File source;
        final Builder parser;
        final Document doc;
        final Element root;
        final Elements children;
        String local;
        final int I;
        int i;
        Element child;
        Attribute attr;
        String code, name;

        source = new File("/usr/share/xml/iso-codes/iso_639.xml");
        parser = new Builder();
        doc = parser.build(source);

        root = doc.getRootElement();

        /*
         * Sanity check
         */

        local = root.getLocalName();
        if (!local.equals("iso_639_entries")) {
            throw new IllegalStateException();
        }

        /*
         * Iterate through list. For any entry with a _1 code, we store its
         * name (there are a fair number of languages that don't have a two
         * character code; some day we may need to support that by changing
         * the parser [sic] above).
         */

        children = root.getChildElements("iso_639_entry");
        I = children.size();
        for (i = 0; i < I; i++) {
            child = children.get(i);

            attr = child.getAttribute("iso_639_1_code");
            if (attr == null) {
                continue;
            }
            code = attr.getValue();

            attr = child.getAttribute("name");
            name = attr.getValue();

            languages.put(code, name);
        }
    }

    String getName(String code) {
        return languages.get(code);
    }

    private void loadCountryNames() throws ValidityException, ParsingException, IOException {
        final File source;
        final Builder parser;
        final Document doc;
        final Element root;
        final Elements children;
        String local;
        final int I;
        int i;
        Element child;
        Attribute attr;
        String code, name;

        source = new File("/usr/share/xml/iso-codes/iso_3166.xml");
        parser = new Builder();
        doc = parser.build(source);

        root = doc.getRootElement();

        /*
         * Sanity check
         */

        local = root.getLocalName();
        if (!local.equals("iso_3166_entries")) {
            throw new IllegalStateException();
        }

        /*
         * Iterate through list. For any entry with an alpha_2 code, we store
         * its name.
         */

        children = root.getChildElements("iso_3166_entry");
        I = children.size();
        for (i = 0; i < I; i++) {
            child = children.get(i);

            attr = child.getAttribute("alpha_2_code");
            if (attr == null) {
                continue;
            }
            code = attr.getValue();

            attr = child.getAttribute("name");
            name = attr.getValue();

            countries.put(code, name);
        }
    }

    String getCountryName(String code) {
        return countries.get(code);
    }
}
