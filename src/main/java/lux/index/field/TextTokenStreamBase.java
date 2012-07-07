package lux.index.field;

import java.io.IOException;

import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

public abstract class TextTokenStreamBase extends XmlTokenStreamBase {

    protected int[] textOffsets;
    protected int iText;

    public TextTokenStreamBase(XdmNode doc, int[] textOffsets) {
        super(doc);
        charStream = new OffsetCharStream(null);
        this.textOffsets = textOffsets;
        iText = 0;
    }

    protected boolean resetTokenizer(CharSequence text) {
        charStream.reset(text);
        try {
          tokenizer.reset(charStream);
          if (curNode.getNodeKind() == XdmNodeKind.TEXT) {
            charStream.setCharStreamOffset(textOffsets[iText++]);
          }       
          return incrementTokenStream(); 
        } catch (IOException e) {
          return false;
        }
      }

}