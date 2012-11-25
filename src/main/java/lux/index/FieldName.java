package lux.index;

import lux.index.field.AttributeQNameField;
import lux.index.field.AttributeTextField;
import lux.index.field.DocumentField;
import lux.index.field.ElementQNameField;
import lux.index.field.ElementTextField;
import lux.index.field.PathField;
import lux.index.field.PathValueField;
import lux.index.field.QNameValueField;
import lux.index.field.URIField;
import lux.index.field.FieldDefinition;
import lux.index.field.XmlTextField;

public enum FieldName {
    
    URI(URIField.getInstance()), 
    XML_STORE(DocumentField.getInstance()), 
    ELT_QNAME(ElementQNameField.getInstance()),
    ATT_QNAME(AttributeQNameField.getInstance()),
    PATH(PathField.getInstance()), 
    PATH_VALUE(PathValueField.getInstance()),
    QNAME_VALUE(QNameValueField.getInstance()),
    ELEMENT_TEXT(ElementTextField.getInstance()),
    ATTRIBUTE_TEXT(AttributeTextField.getInstance()),
    XML_TEXT(XmlTextField.getInstance());
    
    FieldName (FieldDefinition field) {
        this.field = field;
    }
    
    private final FieldDefinition field;
        
    public FieldDefinition getField () {
        return field;
    }
}
