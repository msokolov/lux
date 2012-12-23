package lux.functions.file;

import java.io.File;
import java.util.Arrays;

import lux.functions.Function;
import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

/**
 * <p>Provides a (very incomplete, noncompliant) implementation of http://www.expath.org/spec/file</p>
 * <code>file:is-dir($path as xs:string) as xs:boolean</code>
 * <p>returns true iff the file at the given path exists and is a directory.</p>
 * <code>file:list($path as xs:string) as xs:string*</code>
 * <p>If $path is a directory, returns the names of files (and directories) in the directory in a system-dependent order.
 * The directory itself and its parent are not included in the list</p>
 */
public class FileExtensions {
    
    public static final String FILE_NAMESPACE = "http://expath.org/ns/file";
    
    public static void registerFunctions (Processor processor) {
        processor.registerExtensionFunction(createIsDirFunction());
        processor.registerExtensionFunction(createListFunction());
    }

    private static ExtensionFunction createIsDirFunction () {
        return new Function (
                new QName ("file", FILE_NAMESPACE, "is-dir"),
                SequenceType.makeSequenceType(ItemType.BOOLEAN, OccurrenceIndicator.ONE),
                new SequenceType[] { SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ONE)})
        {
            public XdmValue call(XdmValue[] arguments) throws SaxonApiException {
                String path = arguments[0].itemAt(0).getStringValue();
                boolean result = new File(path).isDirectory();
                return new XdmAtomicValue(result);
            }
        };
    }
    
    private static ExtensionFunction createListFunction () {
        
        return new Function (
                new QName ("file", FILE_NAMESPACE, "list"),
                SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ZERO_OR_MORE),
                new SequenceType[] { SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ONE)})
        {
            @Override
            public XdmValue call(XdmValue[] arguments) throws SaxonApiException {
                String dir = arguments[0].itemAt(0).getStringValue();
                String [] files = new File(dir).list();
                if (files == null) {
                    return XdmEmptySequence.getInstance();
                }
                XdmItem[] items = new XdmItem[files.length];
                for (int i = 0; i < files.length; i++) {
                    items[i] = new XdmAtomicValue(files[i]);
                }
                return new XdmValue (Arrays.asList(items));
            }
        };
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
