package lux.functions.file;

import lux.functions.Function;

import java.io.File;
import java.util.Arrays;

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
 * Provides a (very incomplete, noncompliant) implementation of http://www.expath.org/spec/file
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
